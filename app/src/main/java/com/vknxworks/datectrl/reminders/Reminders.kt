package com.vknxworks.datectrl.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.CalendarAlerts
import androidx.core.content.ContextCompat
import com.vknxworks.datectrl.MainActivity
import com.vknxworks.datectrl.R
import com.vknxworks.datectrl.data.settings.appDataStore
import com.vknxworks.datectrl.ui.common.timeRange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * Android's calendar storage keeps track of reminders and broadcasts EVENT_REMINDER when one is due; the
 * calendar app shows it. DateCtrl reads the due alerts, shows a notification for each and marks them shown.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CalendarContract.ACTION_EVENT_REMINDER) return
        val alarmTime = intent.data?.lastPathSegment?.toLongOrNull() ?: System.currentTimeMillis()
        val pending = goAsync()
        Thread {
            try {
                Reminders.fire(context.applicationContext, alarmTime)
            } catch (_: Throwable) {
                // A broken calendar provider must never crash the app.
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** The buttons on a reminder: Snooze, Dismiss, and swiping it away. Not exported. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val r = Reminders.Reminder.from(intent) ?: return
        val pending = goAsync()
        Thread {
            try {
                when (intent.action) {
                    ACTION_DISMISS -> Reminders.dismiss(context.applicationContext, r)
                    ACTION_SNOOZE -> Reminders.snooze(context.applicationContext, r)
                    ACTION_REPOST -> Reminders.show(context.applicationContext, r)
                }
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_DISMISS = "com.vknxworks.datectrl.REMINDER_DISMISS"
        const val ACTION_SNOOZE = "com.vknxworks.datectrl.REMINDER_SNOOZE"
        const val ACTION_REPOST = "com.vknxworks.datectrl.REMINDER_REPOST"
    }
}

object Reminders {
    const val CHANNEL = "reminders"
    private const val SNOOZE_MS = 10 * 60_000L

    data class Reminder(val eventId: Long, val begin: Long, val end: Long, val title: String, val location: String, val allDay: Boolean) {
        val notificationId: Int get() = (eventId * 31 + begin / 60_000).hashCode()

        fun into(intent: Intent): Intent = intent
            .putExtra("event_id", eventId).putExtra("begin", begin).putExtra("end", end)
            .putExtra("title", title).putExtra("location", location).putExtra("all_day", allDay)

        companion object {
            fun from(intent: Intent): Reminder? = runCatching {
                val id = intent.getLongExtra("event_id", -1L)
                if (id <= 0) return null
                Reminder(
                    id, intent.getLongExtra("begin", 0L), intent.getLongExtra("end", 0L),
                    intent.getStringExtra("title").orEmpty(), intent.getStringExtra("location").orEmpty(), intent.getBooleanExtra("all_day", false),
                )
            }.getOrNull()
        }
    }

    private fun granted(context: Context, p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    /** "Event reminders" in Settings; read straight from the settings file (the app may not be running). */
    private fun enabled(context: Context): Boolean = runCatching {
        runBlocking { context.appDataStore.data.first()[booleanPreferencesKey("reminders")] ?: true }
    }.getOrDefault(true)

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(CHANNEL, context.getString(R.string.ch_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.ch_reminders_desc)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        nm.createNotificationChannel(ch)
    }

    /** Shows every alert that's due at [alarmTime] (or earlier and not shown yet) and marks it shown. */
    fun fire(context: Context, alarmTime: Long) {
        if (!granted(context, android.Manifest.permission.READ_CALENDAR)) return
        val show = enabled(context)
        val resolver = context.contentResolver
        val proj = arrayOf(
            CalendarAlerts._ID, CalendarAlerts.EVENT_ID, CalendarAlerts.BEGIN, CalendarAlerts.END,
            CalendarAlerts.TITLE, CalendarAlerts.EVENT_LOCATION, CalendarAlerts.ALL_DAY,
        )
        val now = System.currentTimeMillis()
        val due = ArrayList<Pair<Long, Reminder>>()
        resolver.query(
            CalendarAlerts.CONTENT_URI, proj,
            "${CalendarAlerts.STATE}=? AND ${CalendarAlerts.ALARM_TIME}<=?",
            arrayOf(CalendarAlerts.STATE_SCHEDULED.toString(), maxOf(alarmTime, now).toString()),
            "${CalendarAlerts.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                due += c.getLong(0) to Reminder(
                    c.getLong(1), c.getLong(2), c.getLong(3), c.getString(4).orEmpty(), c.getString(5).orEmpty(), c.getInt(6) != 0,
                )
            }
        }
        val canWrite = granted(context, android.Manifest.permission.WRITE_CALENDAR)
        for ((alertId, r) in due) {
            // An event that has already ended only gets a reminder if it ended in the last hour (the phone was off).
            if (show && r.end > now - 3_600_000L) show(context, r)
            if (canWrite) {
                runCatching {
                    resolver.update(
                        ContentUris.withAppendedId(CalendarAlerts.CONTENT_URI, alertId),
                        ContentValues().apply {
                            put(CalendarAlerts.STATE, CalendarAlerts.STATE_FIRED)
                            put(CalendarAlerts.NOTIFY_TIME, now)
                        },
                        null, null,
                    )
                }
            }
        }
    }

    private fun broadcast(context: Context, action: String, r: Reminder, extra: Int): PendingIntent = PendingIntent.getBroadcast(
        context, r.notificationId * 4 + extra,
        r.into(Intent(context, ReminderActionReceiver::class.java).setAction(action)),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun show(context: Context, r: Reminder) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!granted(context, android.Manifest.permission.POST_NOTIFICATIONS)) return
        createChannel(context)
        val open = PendingIntent.getActivity(
            context, r.notificationId,
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, r.eventId))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, r.begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, r.end)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = r.title.ifBlank { context.getString(R.string.no_title) }
        val text = listOf(timeRange(context, r.begin, r.end, r.allDay), r.location).filter { it.isNotBlank() }.joinToString(" · ")
        val public = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_event)
            .setContentTitle(context.getString(R.string.reminder_public))
            .build()
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_event)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_EVENT)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(open)
            .setDeleteIntent(broadcast(context, ReminderActionReceiver.ACTION_DISMISS, r, 1))
            .setAutoCancel(true)
            .setWhen(r.begin)
            .setShowWhen(!r.allDay)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.snooze), broadcast(context, ReminderActionReceiver.ACTION_SNOOZE, r, 2)).build())
            .addAction(Notification.Action.Builder(null, context.getString(R.string.dismiss), broadcast(context, ReminderActionReceiver.ACTION_DISMISS, r, 1)).build())
            .build()
        runCatching { nm.notify(r.notificationId, n) }
    }

    fun dismiss(context: Context, r: Reminder) {
        context.getSystemService(NotificationManager::class.java)?.cancel(r.notificationId)
        if (!granted(context, android.Manifest.permission.WRITE_CALENDAR)) return
        runCatching {
            context.contentResolver.update(
                CalendarAlerts.CONTENT_URI,
                ContentValues().apply { put(CalendarAlerts.STATE, CalendarAlerts.STATE_DISMISSED) },
                "${CalendarAlerts.EVENT_ID}=? AND ${CalendarAlerts.BEGIN}=? AND ${CalendarAlerts.STATE}=?",
                arrayOf(r.eventId.toString(), r.begin.toString(), CalendarAlerts.STATE_FIRED.toString()),
            )
        }
    }

    /** Shows the reminder again in 10 minutes. */
    fun snooze(context: Context, r: Reminder) {
        context.getSystemService(NotificationManager::class.java)?.cancel(r.notificationId)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + SNOOZE_MS
        val pi = broadcast(context, ReminderActionReceiver.ACTION_REPOST, r, 3)
        runCatching {
            if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}
