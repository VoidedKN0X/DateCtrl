package com.vknxworks.datectrl.data.ics

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/**
 * A VTIMEZONE from a file: its STANDARD and DAYLIGHT observances, enough to know the UTC offset at any local time.
 * Only what files really use is supported: yearly rules by month with a weekday ("last Sunday", "2nd Sunday")
 * or a day of the month, and fixed RDATE onsets. Nothing is ever fetched (TZURL is ignored).
 */
class VTimezone(val tzid: String, val location: String?, private val observances: List<Observance>) {

    data class Observance(
        val daylight: Boolean,
        /** Local time of the first onset, in the offset before it (TZOFFSETFROM). */
        val start: LocalDateTime,
        val offsetFrom: ZoneOffset,
        val offsetTo: ZoneOffset,
        val byMonth: Int? = null,
        /**
         * Weekday and which one in the month (1 = first, -1 = last); null for a day of the month. Without an
         * ordinal (0) the weekday is looked for among [byMonthDays] ("Sunday on or after the 8th").
         */
        val byDay: Pair<Int, DayOfWeek>? = null,
        val byMonthDay: Int? = null,
        val byMonthDays: List<Int> = emptyList(),
        val until: LocalDateTime? = null,
        val yearly: Boolean = false,
        val rdates: List<LocalDateTime> = emptyList(),
    ) {
        /** When this observance starts in [year], or null if it doesn't. */
        fun onsetIn(year: Int): LocalDateTime? {
            if (!yearly) return null
            if (year < start.year) return null
            val month = byMonth ?: start.monthValue
            val first = LocalDate.of(year, month, 1)
            val len = first.lengthOfMonth()
            val day = when {
                byDay != null && byDay.first == 0 -> {
                    val dow = byDay.second
                    byMonthDays.map { if (it < 0) len + it + 1 else it }.filter { it in 1..len }.sorted()
                        .map { first.withDayOfMonth(it) }.firstOrNull { it.dayOfWeek == dow } ?: return null
                }
                byDay != null -> {
                    val (n, dow) = byDay
                    if (n < 0) first.with(TemporalAdjusters.lastInMonth(dow))
                    else first.with(TemporalAdjusters.dayOfWeekInMonth(n.coerceIn(1, 5), dow)).takeIf { it.monthValue == month } ?: return null
                }
                byMonthDay != null -> {
                    val d = if (byMonthDay < 0) len + byMonthDay + 1 else byMonthDay
                    if (d !in 1..len) return null
                    first.withDayOfMonth(d)
                }
                else -> {
                    val d = start.dayOfMonth
                    if (d > first.lengthOfMonth()) return null
                    first.withDayOfMonth(d)
                }
            }
            val at = day.atTime(start.toLocalTime())
            if (at.isBefore(start)) return null
            if (until != null && at.isAfter(until)) return null
            return at
        }

        /** The latest onset at or before [t] (local time), or null. */
        fun lastOnsetBefore(t: LocalDateTime): LocalDateTime? {
            var best: LocalDateTime? = null
            if (!start.isAfter(t)) best = start
            for (r in rdates) if (!r.isAfter(t) && (best == null || r.isAfter(best))) best = r
            for (y in listOf(t.year, t.year - 1)) {
                val o = onsetIn(y) ?: continue
                if (!o.isAfter(t) && (best == null || o.isAfter(best))) best = o
            }
            return best
        }
    }

    /** The UTC offset at local time [t]. */
    fun offsetAt(t: LocalDateTime): ZoneOffset {
        var best: Observance? = null
        var bestAt: LocalDateTime? = null
        for (o in observances) {
            val at = o.lastOnsetBefore(t) ?: continue
            if (bestAt == null || at.isAfter(bestAt)) {
                best = o
                bestAt = at
            }
        }
        return best?.offsetTo ?: observances.minByOrNull { it.start }?.offsetFrom ?: ZoneOffset.UTC
    }

    val isEmpty: Boolean get() = observances.isEmpty()

    companion object {
        private val DAYS = mapOf(
            "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY, "TH" to DayOfWeek.THURSDAY,
            "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY,
        )

        /** "+0100", "-0530", "+013000". */
        fun parseOffset(s: String?): ZoneOffset? {
            val m = Regex("^([+-])(\\d{2})(\\d{2})(\\d{2})?$").matchEntire(s?.trim() ?: return null) ?: return null
            val sign = if (m.groupValues[1] == "-") -1 else 1
            val h = m.groupValues[2].toInt()
            val min = m.groupValues[3].toInt()
            val sec = m.groupValues[4].toIntOrNull() ?: 0
            if (h > 18 || min > 59 || sec > 59) return null
            return runCatching { ZoneOffset.ofTotalSeconds(sign * (h * 3600 + min * 60 + sec)) }.getOrNull()
        }

        /** One STANDARD or DAYLIGHT block, from its properties. Null if it lacks what's needed. */
        internal fun observance(daylight: Boolean, props: List<IcsParser.Prop>): Observance? {
            fun p(name: String) = props.firstOrNull { it.name == name }
            val start = IcsParser.localDateTime(p("DTSTART")?.value) ?: return null
            val to = parseOffset(p("TZOFFSETTO")?.value) ?: return null
            val from = parseOffset(p("TZOFFSETFROM")?.value) ?: to
            val rdates = props.filter { it.name == "RDATE" }.flatMap { it.value.split(',') }.take(100).mapNotNull { IcsParser.localDateTime(it) }
            val rule = p("RRULE")?.value?.uppercase()?.split(';')
                ?.mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }?.toMap()
            if (rule == null || rule["FREQ"] != "YEARLY") return Observance(daylight, start, from, to, rdates = rdates)
            val monthDays = rule["BYMONTHDAY"]?.split(',')?.take(31)?.mapNotNull { it.trim().toIntOrNull()?.takeIf { d -> d in -31..31 && d != 0 } }.orEmpty()
            val byDay = rule["BYDAY"]?.split(',')?.firstOrNull()?.let { d ->
                val m = Regex("^([+-]?\\d)?(MO|TU|WE|TH|FR|SA|SU)$").matchEntire(d) ?: return@let null
                // A weekday without a number but with days of the month: that weekday among those days.
                val n = m.groupValues[1].toIntOrNull() ?: if (monthDays.isNotEmpty()) 0 else 1
                n to DAYS.getValue(m.groupValues[2])
            }
            val until = rule["UNTIL"]?.let { u ->
                // UNTIL is in UTC; as a local time it's close enough to decide which year an onset falls in.
                IcsParser.localDateTime(u.removeSuffix("Z"))
            }
            return Observance(
                daylight, start, from, to,
                byMonth = rule["BYMONTH"]?.split(',')?.firstOrNull()?.toIntOrNull()?.takeIf { it in 1..12 },
                byDay = byDay,
                byMonthDay = if (byDay == null) monthDays.firstOrNull() else null,
                byMonthDays = monthDays,
                until = until,
                yearly = true,
                rdates = rdates,
            )
        }
    }
}

/**
 * Turns a TZID from a file into a time zone Android knows. In order: an IANA name ("Europe/Brussels"), an IANA
 * name at the end of a longer id ("/mozilla.org/20050126_1/Europe/Brussels"), the VTIMEZONE's X-LIC-LOCATION,
 * a common Windows name ("Romance Standard Time"), and finally a system zone whose offsets match the
 * file's VTIMEZONE over the year. When nothing matches, the file's own rules are used to work out the times.
 */
class ZoneResolver(private val timezones: Map<String, VTimezone>, private val fallback: ZoneId) {

    /** A resolved zone: a real one, or the file's own rules when no real zone matches. */
    data class Resolved(val zone: ZoneId?, val custom: VTimezone?) {
        fun toMillis(t: LocalDateTime): Long = when {
            zone != null -> java.time.ZonedDateTime.ofLocal(t, zone, null).toInstant().toEpochMilli()
            custom != null -> t.toInstant(custom.offsetAt(t)).toEpochMilli()
            else -> t.toInstant(ZoneOffset.UTC).toEpochMilli()
        }

        /** The name Android stores with the event. */
        val id: String get() = zone?.id ?: "UTC"
    }

    private val cache = HashMap<String, Resolved>()

    fun resolve(tzid: String?, year: Int): Resolved {
        if (tzid.isNullOrBlank()) return Resolved(fallback, null)
        val key = tzid.trim()
        return cache.getOrPut("$key\u0000$year") { find(key, year) }
    }

    private fun find(tzid: String, year: Int): Resolved {
        known(tzid)?.let { return Resolved(it, null) }
        val vt = timezones[tzid]
        vt?.location?.let { known(it) }?.let { return Resolved(it, null) }
        if (vt != null && !vt.isEmpty) {
            matching(vt, year)?.let { return Resolved(it, null) }
            return Resolved(null, vt)
        }
        return Resolved(fallback, null)
    }

    /** A zone Android knows by this name, or by the IANA name at its end, or by its Windows name. */
    private fun known(name: String): ZoneId? {
        val n = name.trim().trim('"')
        if (n.isEmpty()) return null
        zoneOf(n)?.let { return it }
        // "/mozilla.org/20050126_1/Europe/Brussels", "/softwarestudio.org/Olson_20011030_5/America/New_York"
        val parts = n.split('/').filter { it.isNotEmpty() }
        for (i in 1 until parts.size) {
            val tail = parts.subList(i, parts.size).joinToString("/")
            if ('/' in tail || parts.size - i == 1 && tail.equals("UTC", true)) zoneOf(tail)?.let { return it }
        }
        WINDOWS[n.lowercase()]?.let { return zoneOf(it) }
        return null
    }

    private fun zoneOf(n: String): ZoneId? {
        if (n.equals("Z", true) || n.equals("UTC", true) || n.equals("GMT", true) || n.equals("Etc/UTC", true)) return ZoneId.of("UTC")
        // Only region names: short ids like "EST" or offsets like "+01:00" are not zones Android stores.
        if ('/' !in n) return null
        return runCatching { ZoneId.of(n) }.getOrNull()
    }

    /** A system zone with the same offsets as [vt] all through [year] (the phone's own zone first). */
    private fun matching(vt: VTimezone, year: Int): ZoneId? {
        val samples = ArrayList<LocalDateTime>()
        for (m in 1..12) samples += LocalDateTime.of(year, m, 15, 12, 0)
        // Around each change of offset, so zones that switch on different Sundays are told apart.
        var prev = vt.offsetAt(LocalDateTime.of(year, 1, 1, 12, 0))
        var day = LocalDate.of(year, 1, 2)
        while (day.year == year) {
            val t = day.atTime(12, 0)
            val o = vt.offsetAt(t)
            if (o != prev) {
                samples += day.minusDays(1).atTime(12, 0)
                samples += t
                prev = o
            }
            day = day.plusDays(1)
        }
        val expected = samples.map { vt.offsetAt(it) }
        fun fits(z: ZoneId): Boolean = samples.indices.all { z.rules.getOffset(samples[it]) == expected[it] }
        if (fits(fallback)) return fallback
        return candidates.firstOrNull { fits(it) }
    }

    private val candidates: List<ZoneId> by lazy {
        ZoneId.getAvailableZoneIds()
            .filter { '/' in it && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
            .sorted()
            .mapNotNull { runCatching { ZoneId.of(it) }.getOrNull() }
    }

    companion object {
        /** The Windows names Outlook and Exchange put in files most often. */
        private val WINDOWS = mapOf(
            "utc" to "UTC",
            "gmt standard time" to "Europe/London",
            "greenwich standard time" to "Atlantic/Reykjavik",
            "w. europe standard time" to "Europe/Berlin",
            "romance standard time" to "Europe/Paris",
            "central europe standard time" to "Europe/Budapest",
            "central european standard time" to "Europe/Warsaw",
            "e. europe standard time" to "Europe/Chisinau",
            "fle standard time" to "Europe/Kiev",
            "gtb standard time" to "Europe/Bucharest",
            "russian standard time" to "Europe/Moscow",
            "turkey standard time" to "Europe/Istanbul",
            "israel standard time" to "Asia/Jerusalem",
            "south africa standard time" to "Africa/Johannesburg",
            "arabian standard time" to "Asia/Dubai",
            "india standard time" to "Asia/Kolkata",
            "china standard time" to "Asia/Shanghai",
            "singapore standard time" to "Asia/Singapore",
            "tokyo standard time" to "Asia/Tokyo",
            "korea standard time" to "Asia/Seoul",
            "aus eastern standard time" to "Australia/Sydney",
            "new zealand standard time" to "Pacific/Auckland",
            "eastern standard time" to "America/New_York",
            "central standard time" to "America/Chicago",
            "mountain standard time" to "America/Denver",
            "us mountain standard time" to "America/Phoenix",
            "pacific standard time" to "America/Los_Angeles",
            "alaskan standard time" to "America/Anchorage",
            "hawaiian standard time" to "Pacific/Honolulu",
            "atlantic standard time" to "America/Halifax",
            "e. south america standard time" to "America/Sao_Paulo",
            "argentina standard time" to "America/Argentina/Buenos_Aires",
        )
    }
}
