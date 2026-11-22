package com.vknxworks.datectrl.reminders

import android.provider.CalendarContract.CalendarAlerts

/**
 * The decisions behind snooze, kept free of Android calls so they can be unit tested. [Snoozes] does the work.
 */
internal object SnoozePlan {
    /** A snooze older than this is never shown any more (the phone was off for a week, or the clock jumped). */
    const val MAX_AGE_MS = 7 * 24 * 3_600_000L

    /** Same rule as the calendar storage's own reminders: an event that ended over an hour ago gets no reminder. */
    const val LATE_MS = 3_600_000L

    /** At most this many snoozes are remembered; the oldest are dropped first. */
    const val MAX_ENTRIES = 50

    /**
     * A snooze DateCtrl remembers so it can set its alarm again after a restart. Holds no title, place or notes:
     * those are read from the calendar storage when the snooze is due.
     *
     * [stored] says whether a calendar alert row was written for it (needs calendar write access).
     */
    data class Entry(val eventId: Long, val begin: Long, val end: Long, val allDay: Boolean, val at: Long, val stored: Boolean) {
        val key: String get() = key(eventId, begin)
    }

    fun key(eventId: Long, begin: Long): String = "$eventId:$begin"

    fun encode(e: Entry): String = "${e.end},${if (e.allDay) 1 else 0},${e.at},${if (e.stored) 1 else 0}"

    /** Reads an entry back; anything malformed gives null (and is then forgotten). */
    fun decode(key: String, value: String?): Entry? {
        value ?: return null
        val k = key.split(':')
        val v = value.split(',')
        if (k.size != 2 || v.size != 4) return null
        val eventId = k[0].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val begin = k[1].toLongOrNull() ?: return null
        val end = v[0].toLongOrNull() ?: return null
        val allDay = when (v[1]) { "1" -> true; "0" -> false; else -> return null }
        val at = v[2].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val stored = when (v[3]) { "1" -> true; "0" -> false; else -> return null }
        return Entry(eventId, begin, end, allDay, at, stored)
    }

    /** Which entries to drop so at most [max] remain: the ones due earliest go first. */
    fun overflow(entries: List<Entry>, max: Int = MAX_ENTRIES): List<Entry> =
        if (entries.size <= max) emptyList() else entries.sortedBy { it.at }.take(entries.size - max)

    enum class Alarm { EXACT, INEXACT }

    /** An exact alarm when Android allows one (USE_EXACT_ALARM); otherwise the inexact one DateCtrl used before. */
    fun alarm(canScheduleExact: Boolean): Alarm = if (canScheduleExact) Alarm.EXACT else Alarm.INEXACT

    enum class Rearm {
        /** Due already (the phone was off or DateCtrl couldn't run): show it now. */
        DELIVER,

        /** Set the alarm again. */
        ARM,

        /** Set the alarm again and write the calendar alert row again (the calendar storage removed it). */
        ARM_AND_RESTORE_ROW,

        /** Done elsewhere or too old: forget it. */
        FORGET,
    }

    /**
     * What to do with a remembered snooze when DateCtrl re-arms (phone started, app started, any calendar reminder).
     * [rowState] is the state of its calendar alert row, or null if there is none (the calendar storage removes
     * scheduled alert rows when the phone starts).
     */
    fun rearm(e: Entry, now: Long, rowState: Int?, canWrite: Boolean): Rearm = when {
        e.at < now - MAX_AGE_MS -> Rearm.FORGET
        e.at <= now -> Rearm.DELIVER
        rowState == null -> if (e.stored && canWrite) Rearm.ARM_AND_RESTORE_ROW else Rearm.ARM
        rowState == CalendarAlerts.STATE_SCHEDULED -> Rearm.ARM
        // Already shown or dismissed (by DateCtrl's other path or another calendar app).
        else -> Rearm.FORGET
    }

    /** [show]: post the notification. [markFired]: mark the calendar alert row as shown. */
    data class Due(val show: Boolean, val markFired: Boolean)

    /**
     * When a snooze is due. [rowState] is its calendar alert row's state (null if there's none), [eventExists] whether
     * the event is still in the calendar storage, [enabled] the "Event reminders" setting.
     */
    fun due(rowState: Int?, eventExists: Boolean, end: Long, now: Long, enabled: Boolean): Due {
        // Shown or dismissed already, e.g. when the calendar storage's own reminder broadcast came first.
        if (rowState != null && rowState != CalendarAlerts.STATE_SCHEDULED) return Due(show = false, markFired = false)
        val show = eventExists && enabled && end > now - LATE_MS
        return Due(show = show, markFired = rowState == CalendarAlerts.STATE_SCHEDULED)
    }
}
