package com.vknxworks.datectrl.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.CalendarContract.CalendarAlerts
import android.provider.CalendarContract.Events
import androidx.core.content.ContextCompat
import com.vknxworks.datectrl.reminders.SnoozePlan.Entry

/**
 * Phone started (or DateCtrl was updated): Android has forgotten DateCtrl's alarms, and the calendar storage
 * removes its scheduled alert rows when the phone starts (AOSP CalendarProvider2 on BOOT_COMPLETED), snoozes
 * included. Sets the alarm of every remembered snooze again and shows the ones that came due while the phone was off.
 * Only the system can send these two broadcasts; the receiver isn't exported.
 */
class SnoozeRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        Thread {
            try {
                Snoozes.rearm(context.applicationContext)
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/**
 * Snoozed reminders. Each snooze is:
 * - a scheduled row in the calendar storage's alerts (so the state is shared with it, as the AOSP calendar app does),
 * - an exact alarm to DateCtrl's own [ReminderActionReceiver] at the snooze time (USE_EXACT_ALARM; an inexact one
 *   if Android says exact alarms aren't allowed),
 * - a small record in DateCtrl's private files (event id, occurrence start and end, all-day, snooze time; no title),
 *   so the alarm can be set again after a restart, when Android has forgotten it.
 *
 * Call from a background thread: everything here reads or writes the calendar storage or the record file.
 */
object Snoozes {
    private const val PREFS = "snoozes"
    const val EXTRA_AT = "snooze_at"

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun granted(context: Context, p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    private fun all(context: Context): List<Entry> {
        val p = prefs(context)
        val out = ArrayList<Entry>()
        val bad = ArrayList<String>()
        for ((k, v) in p.all) {
            val e = SnoozePlan.decode(k, v as? String)
            if (e == null) bad += k else out += e
        }
        if (bad.isNotEmpty()) p.edit().apply { bad.forEach { remove(it) } }.commit()
        return out
    }

    private fun get(context: Context, eventId: Long, begin: Long): Entry? =
        SnoozePlan.decode(SnoozePlan.key(eventId, begin), prefs(context).getString(SnoozePlan.key(eventId, begin), null))

    private fun requestCode(eventId: Long, begin: Long): Int = (eventId * 31 + begin / 60_000).hashCode() * 4 + 3

    /** The alarm's PendingIntent: explicit to DateCtrl's non-exported receiver, immutable. */
    private fun alarmIntent(context: Context, e: Entry, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context, requestCode(e.eventId, e.begin),
        Intent(context, ReminderActionReceiver::class.java)
            .setAction(ReminderActionReceiver.ACTION_SNOOZE_DUE)
            .putExtra("event_id", e.eventId).putExtra("begin", e.begin).putExtra("end", e.end)
            .putExtra("all_day", e.allDay).putExtra(EXTRA_AT, e.at),
        PendingIntent.FLAG_IMMUTABLE or flags,
    )

    private fun arm(context: Context, e: Entry) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(context, e, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val exact = SnoozePlan.alarm(runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)) == SnoozePlan.Alarm.EXACT
        val done = exact && runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.at, pi) }.isSuccess
        if (!done) runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.at, pi) }
    }

    private fun disarm(context: Context, e: Entry) {
        val pi = alarmIntent(context, e, PendingIntent.FLAG_NO_CREATE) ?: return
        runCatching { context.getSystemService(AlarmManager::class.java)?.cancel(pi) }
        pi.cancel()
    }

    /** The state of the snooze's calendar alert row, or null if there is none (or it can't be read). */
    private fun rowState(context: Context, e: Entry): Int? {
        if (!granted(context, android.Manifest.permission.READ_CALENDAR)) return null
        return runCatching {
            context.contentResolver.query(
                CalendarAlerts.CONTENT_URI, arrayOf(CalendarAlerts.STATE),
                "${CalendarAlerts.EVENT_ID}=? AND ${CalendarAlerts.BEGIN}=? AND ${CalendarAlerts.ALARM_TIME}=?",
                arrayOf(e.eventId.toString(), e.begin.toString(), e.at.toString()), null,
            )?.use { c ->
                var state: Int? = null
                while (c.moveToNext()) {
                    val s = c.getInt(0)
                    if (state == null || s == CalendarAlerts.STATE_SCHEDULED) state = s
                }
                state
            }
        }.getOrNull()
    }

    private fun writeRow(context: Context, e: Entry, now: Long): Boolean = runCatching {
        val values = ContentValues().apply {
            for ((column, value) in Reminders.snoozeAlert(e.eventId, e.begin, e.end, e.at, now)) put(column, value)
        }
        context.contentResolver.insert(CalendarAlerts.CONTENT_URI, values) != null
    }.getOrDefault(false)

    private fun setRowState(context: Context, e: Entry, from: Int, to: Int) {
        if (!granted(context, android.Manifest.permission.WRITE_CALENDAR)) return
        runCatching {
            context.contentResolver.update(
                CalendarAlerts.CONTENT_URI,
                ContentValues().apply {
                    put(CalendarAlerts.STATE, to)
                    if (to == CalendarAlerts.STATE_FIRED) put(CalendarAlerts.NOTIFY_TIME, System.currentTimeMillis())
                },
                "${CalendarAlerts.EVENT_ID}=? AND ${CalendarAlerts.BEGIN}=? AND ${CalendarAlerts.ALARM_TIME}=? AND ${CalendarAlerts.STATE}=?",
                arrayOf(e.eventId.toString(), e.begin.toString(), e.at.toString(), from.toString()),
            )
        }
    }

    /** Title and place of the event, or null if it's gone (deleted here or in another app) or can't be read. */
    private fun event(context: Context, eventId: Long): Pair<String, String>? {
        if (!granted(context, android.Manifest.permission.READ_CALENDAR)) return null
        return runCatching {
            context.contentResolver.query(
                ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
                arrayOf(Events.TITLE, Events.EVENT_LOCATION, Events.DELETED), null, null, null,
            )?.use { c -> if (c.moveToFirst() && c.getInt(2) == 0) c.getString(0).orEmpty() to c.getString(1).orEmpty() else null }
        }.getOrNull()
    }

    /**
     * Snoozes [r] until [at]: writes the calendar alert row (if allowed), remembers it and sets the alarm.
     * A snooze already waiting for the same occurrence is replaced.
     */
    @Synchronized
    fun snooze(context: Context, r: Reminders.Reminder, at: Long, now: Long) {
        get(context, r.eventId, r.begin)?.let { forgetAndCancelRow(context, it) }
        val canWrite = granted(context, android.Manifest.permission.WRITE_CALENDAR)
        val draft = Entry(r.eventId, r.begin, r.end, r.allDay, at, stored = false)
        val e = draft.copy(stored = canWrite && writeRow(context, draft, now))
        val p = prefs(context)
        p.edit().putString(e.key, SnoozePlan.encode(e)).commit()
        val list = all(context)
        val drop = SnoozePlan.overflow(list)
        if (drop.isNotEmpty()) {
            drop.forEach { disarm(context, it) }
            p.edit().apply { drop.forEach { remove(it.key) } }.commit()
        }
        arm(context, e)
    }

    /** The alarm of a snooze went off ([at] is the snooze time it was set for). */
    @Synchronized
    fun alarm(context: Context, eventId: Long, begin: Long, at: Long) {
        val e = get(context, eventId, begin) ?: return // Dismissed, opened or deleted meanwhile.
        if (e.at != at) return // Replaced by a newer snooze; its own alarm is set.
        deliver(context, e, System.currentTimeMillis())
    }

    private fun deliver(context: Context, e: Entry, now: Long) {
        prefs(context).edit().remove(e.key).commit()
        val state = rowState(context, e)
        val ev = event(context, e.eventId)
        val d = SnoozePlan.due(state, ev != null, e.end, now, Reminders.enabled(context))
        if (d.markFired) setRowState(context, e, CalendarAlerts.STATE_SCHEDULED, CalendarAlerts.STATE_FIRED)
        if (d.show && ev != null) Reminders.show(context, Reminders.Reminder(e.eventId, e.begin, e.end, ev.first, ev.second, e.allDay))
    }

    /**
     * Sets the alarm of every remembered snooze again, shows the ones that are due, and writes back calendar alert
     * rows the calendar storage removed. Runs when the phone starts, when DateCtrl starts and with every calendar
     * reminder broadcast; setting an alarm that is already set just replaces it.
     */
    @Synchronized
    fun rearm(context: Context) {
        val list = all(context)
        if (list.isEmpty()) return
        val now = System.currentTimeMillis()
        // Only write a row back when the existing ones can be read too, so none is written twice.
        val canWrite = granted(context, android.Manifest.permission.WRITE_CALENDAR) &&
            granted(context, android.Manifest.permission.READ_CALENDAR)
        for (e in list) {
            when (SnoozePlan.rearm(e, now, rowState(context, e), canWrite)) {
                SnoozePlan.Rearm.DELIVER -> { disarm(context, e); deliver(context, e, now) }
                SnoozePlan.Rearm.ARM -> arm(context, e)
                SnoozePlan.Rearm.ARM_AND_RESTORE_ROW -> {
                    writeRow(context, e, now)
                    arm(context, e)
                }
                SnoozePlan.Rearm.FORGET -> { disarm(context, e); prefs(context).edit().remove(e.key).commit() }
            }
        }
    }

    /** A calendar alert was shown by the reminder broadcast; if it was this snooze, its own alarm isn't needed. */
    @Synchronized
    fun shown(context: Context, eventId: Long, begin: Long, alarmTime: Long) {
        val e = get(context, eventId, begin) ?: return
        if (e.at != alarmTime) return
        disarm(context, e)
        prefs(context).edit().remove(e.key).commit()
    }

    /** Dismissed or opened: a snooze waiting for this occurrence is cancelled (alarm, record and calendar row). */
    @Synchronized
    fun cancel(context: Context, eventId: Long, begin: Long) {
        get(context, eventId, begin)?.let { forgetAndCancelRow(context, it) }
    }

    /** The event was deleted: cancel every snooze of it. */
    @Synchronized
    fun cancelEvent(context: Context, eventId: Long) {
        all(context).filter { it.eventId == eventId }.forEach { forgetAndCancelRow(context, it) }
    }

    private fun forgetAndCancelRow(context: Context, e: Entry) {
        disarm(context, e)
        prefs(context).edit().remove(e.key).commit()
        setRowState(context, e, CalendarAlerts.STATE_SCHEDULED, CalendarAlerts.STATE_DISMISSED)
    }
}
