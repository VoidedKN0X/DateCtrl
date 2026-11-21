package com.vknxworks.datectrl.data.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * A repeat the editor can build and show: every [interval] days, weeks, months or years, tied to the start day
 * (its weekday for weekly, its day of the month for monthly), ending never, on [until] (the last day an
 * occurrence may fall on, inclusive) or after [count] times. At most one of [until] and [count] is set.
 */
data class RepeatRule(val freq: Freq, val interval: Int = 1, val until: LocalDate? = null, val count: Int? = null) {
    init {
        require(interval >= 1) { "interval" }
        require(until == null || count == null) { "until and count" }
        require(count == null || count >= 1) { "count" }
    }
}

/** Reading and writing the RRULEs of [RepeatRule] (RFC 5545 section 3.3.10). Plain Kotlin, no Android. */
object RepeatRules {
    const val MAX_INTERVAL = 99
    const val MAX_COUNT = 999

    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val UTC_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun parts(rule: String): Map<String, String> =
        rule.uppercase().split(';').mapNotNull { p -> p.split('=').takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()

    /**
     * [rrule] as a [RepeatRule] when it is one the editor can show and build again (the shape [build] writes, or
     * Android's simple FREQ rules); null for anything else, which the editor then keeps as it is. [zone] is the
     * event's time zone, for a date-time UNTIL.
     */
    fun parse(rrule: String?, zone: ZoneId): RepeatRule? {
        if (rrule.isNullOrBlank()) return null
        val p = parts(rrule)
        val freq = when (p["FREQ"]) {
            "DAILY" -> Freq.DAILY
            "WEEKLY" -> Freq.WEEKLY
            "MONTHLY" -> Freq.MONTHLY
            "YEARLY" -> Freq.YEARLY
            else -> return null
        }
        val allowed = setOf("FREQ", "INTERVAL", "UNTIL", "COUNT", "WKST", "BYDAY", "BYMONTHDAY")
        if (!allowed.containsAll(p.keys)) return null
        if ("BYDAY" in p && !(freq == Freq.WEEKLY && p["BYDAY"]!!.matches(Regex("^(MO|TU|WE|TH|FR|SA|SU)$")))) return null
        if ("BYMONTHDAY" in p && !(freq == Freq.MONTHLY && p["BYMONTHDAY"]!!.matches(Regex("^\\d{1,2}$")))) return null
        val interval = p["INTERVAL"]?.let { it.toIntOrNull() ?: return null } ?: 1
        if (interval !in 1..MAX_INTERVAL) return null
        val count = p["COUNT"]?.let { it.toIntOrNull() ?: return null }
        if (count != null && count !in 1..MAX_COUNT) return null
        val until = p["UNTIL"]?.let { untilDate(it, zone) ?: return null }
        if (until != null && count != null) return null
        return RepeatRule(freq, interval, until, count)
    }

    private fun untilDate(v: String, zone: ZoneId): LocalDate? = runCatching {
        when {
            v.length == 8 -> LocalDate.parse(v, DATE)
            v.endsWith("Z") -> LocalDateTime.parse(v, UTC_TIME).atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDate()
            // A floating date-time: read in the event's own zone.
            else -> LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")).toLocalDate()
        }
    }.getOrNull()

    /**
     * The RRULE for [r] on an event starting on [start]. All-day events get a date UNTIL; timed events the last
     * moment of the until day in [zone], in UTC, as RFC 5545 asks for when DTSTART has a time zone.
     */
    fun build(r: RepeatRule, start: LocalDate, allDay: Boolean, zone: ZoneId): String {
        val sb = StringBuilder("FREQ=").append(r.freq.name)
        if (r.interval > 1) sb.append(";INTERVAL=").append(r.interval)
        when (r.freq) {
            Freq.WEEKLY -> sb.append(";BYDAY=").append(byDay(start.dayOfWeek))
            Freq.MONTHLY -> sb.append(";BYMONTHDAY=").append(start.dayOfMonth)
            else -> Unit
        }
        r.count?.let { sb.append(";COUNT=").append(it) }
        r.until?.let { u ->
            if (allDay) {
                sb.append(";UNTIL=").append(u.format(DATE))
            } else {
                val last = u.atTime(LocalTime.of(23, 59, 59)).atZone(zone).withZoneSameInstant(ZoneOffset.UTC)
                sb.append(";UNTIL=").append(last.format(UTC_TIME))
            }
        }
        return sb.toString()
    }

    fun byDay(d: DayOfWeek) = when (d) {
        DayOfWeek.MONDAY -> "MO"
        DayOfWeek.TUESDAY -> "TU"
        DayOfWeek.WEDNESDAY -> "WE"
        DayOfWeek.THURSDAY -> "TH"
        DayOfWeek.FRIDAY -> "FR"
        DayOfWeek.SATURDAY -> "SA"
        DayOfWeek.SUNDAY -> "SU"
    }
}
