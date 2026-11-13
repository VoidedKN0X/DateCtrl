package com.vknxworks.datectrl.data.ics

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.zone.ZoneOffsetTransitionRule
import java.util.Locale

/**
 * Writes events as an RFC 5545 iCalendar file: CRLF line ends, lines folded at 75 octets, TEXT values escaped,
 * a UID and DTSTAMP on every event, and a VTIMEZONE for every TZID used (built from the phone's time zone rules).
 * All-day events are written as dates; changed occurrences as VEVENTs with the series' UID and a RECURRENCE-ID.
 */
object IcsWriter {
    private val LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val RRULE_SAFE = Regex("^[A-Z0-9=;,:+\\-]+$")
    private const val PRODID = "-//VKNXWorks//DateCtrl//EN"

    fun write(events: List<IcsEvent>, now: Instant = Instant.now()): String {
        val out = StringBuilder(events.size * 400 + 256)
        fun line(s: String) = IcsText.fold(s, out)
        line("BEGIN:VCALENDAR")
        line("VERSION:2.0")
        line("PRODID:$PRODID")
        line("CALSCALE:GREGORIAN")
        line("METHOD:PUBLISH")
        // Every zone a TZID points to, with the earliest year it's needed for.
        val zones = sortedMapOf<String, Int>()
        for (e in events) for (x in listOf(e) + e.overrides) {
            val z = zoneOf(x) ?: continue
            val year = Instant.ofEpochMilli(x.start).atZone(z).year
            zones[z.id] = minOf(zones[z.id] ?: year, year)
        }
        for ((id, year) in zones) vtimezone(ZoneId.of(id), year).forEach(::line)
        val stamp = LOCAL.format(LocalDateTime.ofInstant(now, ZoneOffset.UTC)) + "Z"
        for (e in events) {
            vevent(e, e, stamp, recurrenceId = null).forEach(::line)
            for (o in e.overrides) vevent(o.copy(uid = e.uid), e, stamp, recurrenceId = o.recurrenceId).forEach(::line)
        }
        line("END:VCALENDAR")
        return out.toString()
    }

    /** The zone a timed event is written in with TZID; null when it's written as a date or in UTC. */
    private fun zoneOf(e: IcsEvent): ZoneId? {
        if (e.allDay) return null
        val id = e.timeZone
        if (id.isBlank() || id.equals("UTC", true) || id.equals("GMT", true) || id.equals("Etc/UTC", true)) return null
        val z = runCatching { ZoneId.of(id) }.getOrNull() ?: return null
        // Only real region zones get a TZID; fixed offsets are written in UTC.
        return if (z is ZoneOffset) null else z
    }

    /** "DTSTART;TZID=Europe/Brussels:20261113T090000", "DTSTART;VALUE=DATE:20261113" or "DTSTART:20261113T080000Z". */
    private fun time(name: String, ms: Long, allDay: Boolean, zone: ZoneId?): String = when {
        allDay -> "$name;VALUE=DATE:" + DATE.format(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC))
        zone != null -> "$name;TZID=${zone.id}:" + LOCAL.format(Instant.ofEpochMilli(ms).atZone(zone))
        else -> "$name:" + LOCAL.format(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC)) + "Z"
    }

    private fun vevent(e: IcsEvent, series: IcsEvent, stamp: String, recurrenceId: Long?): List<String> {
        val zone = zoneOf(e)
        val lines = ArrayList<String>()
        lines += "BEGIN:VEVENT"
        lines += "UID:" + IcsText.escape(e.uid)
        lines += "DTSTAMP:$stamp"
        if (recurrenceId != null) lines += time("RECURRENCE-ID", recurrenceId, series.allDay, zoneOf(series))
        lines += time("DTSTART", e.start, e.allDay, zone)
        val end = if (e.allDay) maxOf(e.end, e.start + DAY_MS) else maxOf(e.end, e.start)
        lines += time("DTEND", end, e.allDay, zone)
        if (e.title.isNotEmpty()) lines += "SUMMARY:" + IcsText.escape(e.title)
        if (e.location.isNotEmpty()) lines += "LOCATION:" + IcsText.escape(e.location)
        if (e.description.isNotEmpty()) lines += "DESCRIPTION:" + IcsText.escape(e.description)
        val rrule = e.rrule?.trim()?.uppercase()?.takeIf { recurrenceId == null && RRULE_SAFE.matches(it) && "FREQ=" in it }
        if (rrule != null) {
            lines += "RRULE:$rrule"
            if (e.exdates.isNotEmpty()) {
                val values = e.exdates.sorted().joinToString(",") { time("X", it, e.allDay, zone).substringAfter(':') }
                lines += when {
                    e.allDay -> "EXDATE;VALUE=DATE:$values"
                    zone != null -> "EXDATE;TZID=${zone.id}:$values"
                    else -> "EXDATE:$values"
                }
            }
        }
        for (m in e.reminders.distinct().sorted()) {
            lines += "BEGIN:VALARM"
            lines += "ACTION:DISPLAY"
            lines += "DESCRIPTION:" + IcsText.escape(e.title.ifBlank { "Reminder" })
            lines += "TRIGGER:" + if (m <= 0) "PT0S" else "-" + IcsDuration.format(m * 60_000L)
            lines += "END:VALARM"
        }
        lines += "END:VEVENT"
        return lines
    }

    // ---------- VTIMEZONE ----------

    private val BYDAY = mapOf(
        DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE", DayOfWeek.THURSDAY to "TH",
        DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU",
    )

    /**
     * A VTIMEZONE for [zone] from the phone's time zone rules: one STANDARD and one DAYLIGHT block with yearly
     * rules for zones that change the clocks, or a single STANDARD block for zones that don't.
     */
    internal fun vtimezone(zone: ZoneId, fromYear: Int): List<String> {
        val rules = zone.rules
        val lines = ArrayList<String>()
        lines += "BEGIN:VTIMEZONE"
        lines += "TZID:${zone.id}"
        val transitionRules = rules.transitionRules
        val lastFixed = rules.transitions.lastOrNull()
        if (transitionRules.isEmpty()) {
            val offset = rules.getOffset(Instant.now())
            val since = lastFixed?.dateTimeAfter ?: LocalDateTime.of(1970, 1, 1, 0, 0)
            val before = lastFixed?.offsetBefore ?: offset
            lines += observance("STANDARD", since, before, offset, null)
        } else {
            // Start the yearly rules where they really apply: after the zone's last one-off change, and no later
            // than the first event (files may be read by apps that ignore rules before DTSTART).
            val ruleStart = (lastFixed?.dateTimeAfter?.year?.plus(1) ?: 1970).coerceAtMost(fromYear).coerceAtLeast(1970)
            for (r in transitionRules) {
                val t = r.createTransition(ruleStart)
                val type = if (t.offsetAfter.totalSeconds > r.standardOffset.totalSeconds) "DAYLIGHT" else "STANDARD"
                lines += observance(type, t.dateTimeBefore, t.offsetBefore, t.offsetAfter, rrule(r))
            }
        }
        lines += "END:VTIMEZONE"
        return lines
    }

    private fun observance(type: String, start: LocalDateTime, from: ZoneOffset, to: ZoneOffset, rrule: String?): List<String> = buildList {
        add("BEGIN:$type")
        add("DTSTART:" + LOCAL.format(start))
        add("TZOFFSETFROM:" + offset(from))
        add("TZOFFSETTO:" + offset(to))
        if (rrule != null) add("RRULE:$rrule")
        add("END:$type")
    }

    /**
     * The yearly rule of a Java transition rule. Java says "Sunday on or after the 25th" (its form of "last Sunday"
     * in a 31-day month) or "Sunday on or after the 8th": written as BYDAY=-1SU and BYDAY=2SU where that's exact,
     * otherwise as the weekday within the seven possible days (BYDAY=SU;BYMONTHDAY=2,3,4,5,6,7,8).
     */
    internal fun rrule(r: ZoneOffsetTransitionRule): String {
        val head = "FREQ=YEARLY;BYMONTH=${r.month.value}"
        val dow = r.dayOfWeek ?: return "$head;BYMONTHDAY=${r.dayOfMonthIndicator}"
        val day = BYDAY.getValue(dow)
        val ind = r.dayOfMonthIndicator
        val fixedLength = r.month != java.time.Month.FEBRUARY
        return when {
            ind == -1 || (ind > 0 && fixedLength && ind + 6 == r.month.length(false)) -> "$head;BYDAY=-1$day"
            ind > 0 && (ind - 1) % 7 == 0 -> "$head;BYDAY=${(ind + 6) / 7}$day"
            ind > 0 -> "$head;BYDAY=$day;BYMONTHDAY=" + (ind..minOf(ind + 6, 31)).joinToString(",")
            else -> "$head;BYDAY=$day;BYMONTHDAY=" + ((ind - 6)..ind).joinToString(",")
        }
    }

    /** "+0100", "-0530" (seconds only when the offset has them). */
    internal fun offset(o: ZoneOffset): String {
        val total = o.totalSeconds
        val sign = if (total < 0) "-" else "+"
        val a = Math.abs(total)
        val s = a % 60
        return sign + "%02d%02d".format(Locale.ROOT, a / 3600, (a % 3600) / 60) + if (s != 0) "%02d".format(Locale.ROOT, s) else ""
    }

    private const val DAY_MS = 86_400_000L
}
