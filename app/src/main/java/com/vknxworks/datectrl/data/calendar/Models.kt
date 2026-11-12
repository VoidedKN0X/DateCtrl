package com.vknxworks.datectrl.data.calendar

import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** One calendar Android knows (from an account, or a local one on this phone). */
data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val visible: Boolean,
    val accessLevel: Int,
) {
    /** Events can be added and changed. */
    val writable: Boolean get() = accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR

    /** A calendar only on this phone (no account syncs it). */
    val local: Boolean get() = accountType == CalendarContract.ACCOUNT_TYPE_LOCAL
}

/** One occurrence of an event (a repeating event has one per time it happens). */
data class Instance(
    val eventId: Long,
    val begin: Long,
    val end: Long,
    val title: String,
    val location: String,
    val allDay: Boolean,
    val calendarId: Long,
    val color: Int,
    val declined: Boolean,
) {
    /** The first day it's on, in the phone's time zone (all-day events are stored in UTC). */
    fun firstDay(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        if (allDay) Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
        else Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()

    /** The last day it's on (an event ending at midnight doesn't reach into the next day). */
    fun lastDay(zone: ZoneId = ZoneId.systemDefault()): LocalDate {
        val first = firstDay(zone)
        val last = if (allDay) Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
        else Instant.ofEpochMilli((end - 1).coerceAtLeast(begin)).atZone(zone).toLocalDate()
        return if (last.isBefore(first)) first else last
    }

    fun isOn(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        !day.isBefore(firstDay(zone)) && !day.isAfter(lastDay(zone))

    /** A stable key for lists. */
    val key: String get() = "$eventId@$begin"
}

data class Attendee(val name: String, val email: String, val status: Int, val organizer: Boolean)

/** Everything about one event, for the detail and edit screens. */
data class EventDetail(
    val id: Long,
    val calendarId: Long,
    val calendarName: String,
    val color: Int,
    val accessLevel: Int,
    val accountName: String,
    val accountType: String,
    val title: String,
    val location: String,
    val description: String,
    val dtStart: Long,
    val dtEnd: Long?,
    val duration: String?,
    val allDay: Boolean,
    val rrule: String?,
    val timeZone: String?,
    /** Set when this event is a changed or cancelled occurrence of a repeating event. */
    val originalId: Long?,
    val organizer: String?,
    val reminders: List<Int>,
    val attendees: List<Attendee>,
) {
    val writable: Boolean get() = accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
    val repeating: Boolean get() = !rrule.isNullOrBlank()

    /** Length in milliseconds, from DTEND or (for repeating events) DURATION. */
    val lengthMs: Long
        get() = dtEnd?.let { (it - dtStart).coerceAtLeast(0) } ?: parseDuration(duration) ?: if (allDay) DAY_MS else HOUR_MS
}

/** What the edit screen saves. Times are wall-clock milliseconds; all-day events use [startDate]..[endDate]. */
data class EventDraft(
    val calendarId: Long,
    val title: String = "",
    val location: String = "",
    val description: String = "",
    val allDay: Boolean = false,
    val start: Long,
    val end: Long,
    /** First and last day of an all-day event (inclusive). */
    val startDate: LocalDate,
    val endDate: LocalDate,
    val rrule: String? = null,
    val timeZone: String = ZoneId.systemDefault().id,
    val reminders: List<Int> = emptyList(),
)

const val HOUR_MS = 3_600_000L
const val DAY_MS = 86_400_000L

/** RFC 5545 durations as Android stores them: "P3600S", "PT1H", "P1D", "P2W", "PT1H30M". Null if unreadable. */
fun parseDuration(d: String?): Long? {
    if (d.isNullOrBlank()) return null
    val m = Regex("^([+-])?P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$").find(d.trim().uppercase())
        ?: Regex("^P(\\d+)S$").find(d.trim().uppercase())?.let { return it.groupValues[1].toLong() * 1000 }
        ?: return null
    fun g(i: Int) = m.groupValues[i].toLongOrNull() ?: 0L
    val s = g(2) * 7 * 86400 + g(3) * 86400 + g(4) * 3600 + g(5) * 60 + g(6)
    return s * 1000 * (if (m.groupValues[1] == "-") -1 else 1)
}

/** The simple repeats the editor offers; anything else is kept as it is ("Custom"). */
enum class Repeat(val freq: String?) {
    NEVER(null), DAILY("DAILY"), WEEKLY("WEEKLY"), MONTHLY("MONTHLY"), YEARLY("YEARLY"), CUSTOM(null);

    companion object {
        fun of(rrule: String?): Repeat {
            if (rrule.isNullOrBlank()) return NEVER
            val parts = rrule.uppercase().split(';').mapNotNull { p -> p.split('=').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            val freq = parts["FREQ"]
            // What the editor writes itself: weekly on one day, monthly on one day of the month.
            val simple = parts.keys.all { k ->
                when (k) {
                    "FREQ", "WKST" -> true
                    "INTERVAL" -> parts[k] == "1"
                    "BYDAY" -> freq == "WEEKLY" && parts[k]!!.matches(Regex("^(MO|TU|WE|TH|FR|SA|SU)$"))
                    "BYMONTHDAY" -> freq == "MONTHLY" && parts[k]!!.matches(Regex("^\\d{1,2}$"))
                    else -> false
                }
            }
            if (!simple) return CUSTOM
            return entries.firstOrNull { it.freq != null && it.freq == freq } ?: CUSTOM
        }
    }
}
