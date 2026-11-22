package com.vknxworks.datectrl.data.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** What a quick-add sentence says: the title, the day, and a start (and end) time, or none for all day. */
data class QuickEvent(
    val title: String,
    val date: LocalDate,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    /** Whether the sentence named a day itself (otherwise [date] is today, or tomorrow for a time already past). */
    val dateGiven: Boolean = false,
)

/**
 * Reads a short sentence like "dentist friday 14:00", "lunch with Anna tomorrow 12:30-13:30",
 * "tandarts vrijdag om 14u", "dentiste vendredi à 14h30" or "Zahnarzt Freitag um 14 Uhr" into a [QuickEvent],
 * entirely on the phone. English, Dutch, French and German.
 *
 * Days: today/vandaag/aujourd'hui/heute, tonight/vanavond/ce soir/heute Abend, tomorrow/morgen/demain,
 * day after tomorrow/overmorgen/après-demain/übermorgen, weekdays (with next/volgende/nächsten before, or
 * prochain after: "vendredi prochain"), next week/volgende week/semaine prochaine/nächste Woche, "in 3 days",
 * "over 2 weken", "dans 3 jours", "in 2 Wochen", "3 dec", "december 3", "3 december 2027", "1er mars", "3. März",
 * "3/12", "3-12-2027", "2027-12-03" (day before month, as in Belgium). Times: 14:00, 14.00, 14u, 14u30, 14h,
 * 14h30, 14 Uhr, 14:30 Uhr, 2pm, 2:30pm, ranges with "-", "to", "tot", "until", "à", "bis", "from … to …",
 * "van … tot …", "de … à …", "von … bis …". Whatever isn't a day or a time is the title.
 */
object QuickAdd {
    private val WEEKDAYS = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY, "maandag" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "dinsdag" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY, "woensdag" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "donderdag" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY, "vrijdag" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY, "zaterdag" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "zondag" to DayOfWeek.SUNDAY,
        // French and German: full names only ("mar", "do", "so" and the like are other words).
        "lundi" to DayOfWeek.MONDAY, "mardi" to DayOfWeek.TUESDAY, "mercredi" to DayOfWeek.WEDNESDAY,
        "jeudi" to DayOfWeek.THURSDAY, "vendredi" to DayOfWeek.FRIDAY, "samedi" to DayOfWeek.SATURDAY,
        "dimanche" to DayOfWeek.SUNDAY,
        "montag" to DayOfWeek.MONDAY, "dienstag" to DayOfWeek.TUESDAY, "mittwoch" to DayOfWeek.WEDNESDAY,
        "donnerstag" to DayOfWeek.THURSDAY, "freitag" to DayOfWeek.FRIDAY, "samstag" to DayOfWeek.SATURDAY,
        "sonnabend" to DayOfWeek.SATURDAY, "sonntag" to DayOfWeek.SUNDAY,
    )

    private val MONTHS = mapOf(
        "january" to 1, "jan" to 1, "januari" to 1,
        "february" to 2, "feb" to 2, "februari" to 2,
        "march" to 3, "mar" to 3, "maart" to 3, "mrt" to 3, "maa" to 3,
        "april" to 4, "apr" to 4,
        "may" to 5, "mei" to 5,
        "june" to 6, "jun" to 6, "juni" to 6,
        "july" to 7, "jul" to 7, "juli" to 7,
        "august" to 8, "aug" to 8, "augustus" to 8,
        "september" to 9, "sep" to 9, "sept" to 9,
        "october" to 10, "oct" to 10, "oktober" to 10, "okt" to 10,
        "november" to 11, "nov" to 11,
        "december" to 12, "dec" to 12,
        // French
        "janvier" to 1, "janv" to 1, "février" to 2, "fevrier" to 2, "févr" to 2, "fevr" to 2, "fév" to 2,
        "mars" to 3, "avril" to 4, "avr" to 4, "mai" to 5, "juin" to 6, "juillet" to 7, "juil" to 7,
        "août" to 8, "aout" to 8, "septembre" to 9, "octobre" to 10, "novembre" to 11,
        "décembre" to 12, "decembre" to 12, "déc" to 12,
        // German
        "januar" to 1, "jänner" to 1, "februar" to 2, "märz" to 3, "maerz" to 3, "mär" to 3,
        "juni" to 6, "juli" to 7, "oktober" to 10, "dezember" to 12, "dez" to 12,
    )

    private val NEXT = setOf(
        "next", "volgende", "komende",
        "nächste", "nächsten", "nächster", "naechste", "naechsten", "naechster", "kommende", "kommenden",
    )
    /** "Next" written after the word, in French: "vendredi prochain", "semaine prochaine". */
    private val NEXT_AFTER = setOf("prochain", "prochaine")
    private val THIS = setOf("this", "deze", "ce", "diesen", "dieser")
    private val IN = setOf("in", "over", "dans")
    private val DAY_UNITS = setOf("day", "days", "dag", "dagen", "jour", "jours", "tag", "tage", "tagen")
    private val WEEK_UNITS = setOf("week", "weeks", "weken", "semaine", "semaines", "woche", "wochen")
    private val RANGE = setOf("-", "–", "to", "tot", "until", "till", "à", "jusqu'à", "bis")
    private val TODAY = setOf("today", "vandaag", "tonight", "vanavond", "aujourd'hui", "heute")
    private val TOMORROW = setOf("tomorrow", "morgen", "demain")
    private val DAY_AFTER_TOMORROW = setOf("overmorgen", "après-demain", "apres-demain", "übermorgen", "uebermorgen")
    /** Words before a bare hour that make it a time: "at 12", "om 12", "à 12", "um 12". */
    private val AT = setOf("at", "om", "à", "um")

    /** Small words that only link a day or time to the sentence; dropped next to one. */
    private val LINKS = setOf(
        "at", "om", "on", "op", "from", "van", "the", "de", "rond", "around",
        "à", "le", "la", "vers", "am", "um", "von", "ab", "gegen",
    )

    private val TIME = Regex("^(\\d{1,2})(?:[:.h](\\d{2})|u(\\d{2})?|h)?(am|pm|a\\.m\\.|p\\.m\\.)?$")
    private val TIME_RANGE = Regex("^(.+?)[-–](.+)$")
    private val NUM_DATE = Regex("^(\\d{1,2})[/.-](\\d{1,2})(?:[/.-](\\d{2}|\\d{4}))?$")
    private val ISO_DATE = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$")
    private val ORDINAL = Regex("^(\\d{1,2})(?:st|nd|rd|th|e|de|ste|er)?$")
    private val WHITESPACE = Regex("\\s+")
    private val YEAR = Regex("^\\d{4}$")

    /** [text] read on [today]; [now] decides whether a time without a day is today or tomorrow. Null for an empty sentence. */
    fun parse(text: String, today: LocalDate, now: LocalTime? = null): QuickEvent? {
        val words = text.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        // Matching ignores case and a trailing comma or full stop ("friday," "14:00.").
        // A typographic apostrophe counts as a plain one ("aujourd’hui").
        val low = words.map { it.lowercase().replace('\u2019', '\'').trimEnd(',', ';', '!', '?').let { w -> if (w.endsWith(".") && !w.endsWith("m.")) w.dropLast(1) else w } }
        val used = BooleanArray(words.size)
        var date: LocalDate? = null
        var start: LocalTime? = null
        var end: LocalTime? = null

        fun take(from: Int, n: Int) {
            for (k in from until from + n) used[k] = true
            // A linking word just before ("at 14:00", "op vrijdag", "van 14u") goes too.
            var j = from - 1
            while (j >= 0 && !used[j] && low[j] in LINKS) {
                used[j] = true
                j--
            }
        }

        var i = 0
        while (i < low.size) {
            if (used[i]) { i++; continue }
            val w = low[i]
            val n1 = low.getOrNull(i + 1)
            val n2 = low.getOrNull(i + 2)
            // ---- days ----
            if (date == null) {
                val d: Pair<LocalDate, Int>? = when {
                    w == "ce" && n1 == "soir" -> today to 2
                    w == "heute" && n1 == "abend" -> today to 2
                    w in TODAY -> today to 1
                    w in TOMORROW -> today.plusDays(1) to 1
                    w in DAY_AFTER_TOMORROW -> today.plusDays(2) to 1
                    w == "day" && n1 == "after" && n2 == "tomorrow" -> today.plusDays(2) to 3
                    w in NEXT && n1 != null && WEEKDAYS[n1] != null ->
                        today.with(TemporalAdjusters.next(WEEKDAYS[n1]!!)) to 2
                    w in NEXT && n1 != null && n1 in WEEK_UNITS -> today.plusWeeks(1).with(DayOfWeek.MONDAY) to 2
                    WEEKDAYS[w] != null && n1 in NEXT_AFTER -> today.with(TemporalAdjusters.next(WEEKDAYS[w]!!)) to 2
                    w in WEEK_UNITS && n1 in NEXT_AFTER -> today.plusWeeks(1).with(DayOfWeek.MONDAY) to 2
                    w in THIS && n1 != null && WEEKDAYS[n1] != null ->
                        today.with(TemporalAdjusters.nextOrSame(WEEKDAYS[n1]!!)) to 2
                    WEEKDAYS[w] != null -> today.with(TemporalAdjusters.nextOrSame(WEEKDAYS[w]!!)) to 1
                    w in IN && n1?.toIntOrNull() != null && n2 != null && (n2 in DAY_UNITS || n2 in WEEK_UNITS) -> {
                        val n = n1.toInt().toLong()
                        (if (n2 in DAY_UNITS) today.plusDays(n) else today.plusWeeks(n)) to 3
                    }
                    else -> dateAt(low, i, today)
                }
                if (d != null) {
                    date = d.first
                    take(i, d.second)
                    i += d.second
                    continue
                }
            }
            // ---- times ----
            if (start == null) {
                val t = timeAt(low, i)
                if (t != null) {
                    start = t.first
                    end = t.second
                    take(i, t.third)
                    i += t.third
                    continue
                }
            }
            i++
        }

        val title = words.filterIndexed { k, _ -> !used[k] }.joinToString(" ").trim().trim(',', '-', '–').trim()
        val given = date != null
        var day = date ?: today
        // A time that has already passed today, without a day, is tomorrow.
        if (!given && start != null && now != null && start.isBefore(now)) day = today.plusDays(1)
        return QuickEvent(title, day, start, end, given)
    }

    /** A date at word [i]: numeric, ISO, "3 dec [2027]" or "dec 3 [2027]". Returns it and how many words it took. */
    private fun dateAt(low: List<String>, i: Int, today: LocalDate): Pair<LocalDate, Int>? {
        val w = low[i]
        ISO_DATE.matchEntire(w)?.let { m ->
            return safe(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { it to 1 }
        }
        NUM_DATE.matchEntire(w)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val y = m.groupValues[3]
            // "14.00" is a time, not 14 January: a dotted pair needs a valid day and month and is read as a date
            // only with a year ("3.12.2027").
            if (w.contains('.') && y.isEmpty()) return null
            return if (y.isEmpty()) upcoming(day, month, today)?.let { it to 1 }
            else safe(year(y), month, day)?.let { it to 1 }
        }
        val dayNum = ORDINAL.matchEntire(w)?.groupValues?.get(1)?.toInt()
        val next = low.getOrNull(i + 1)
        if (dayNum != null && next != null && MONTHS[next] != null) {
            val month = MONTHS[next]!!
            val y = low.getOrNull(i + 2)?.takeIf { it.matches(YEAR) }
            return if (y != null) safe(y.toInt(), month, dayNum)?.let { it to 3 } else upcoming(dayNum, month, today)?.let { it to 2 }
        }
        val month = MONTHS[w]
        val d2 = next?.let { ORDINAL.matchEntire(it)?.groupValues?.get(1)?.toInt() }
        if (month != null && d2 != null) {
            val y = low.getOrNull(i + 2)?.takeIf { it.matches(YEAR) }
            return if (y != null) safe(y.toInt(), month, d2)?.let { it to 3 } else upcoming(d2, month, today)?.let { it to 2 }
        }
        return null
    }

    private fun year(y: String) = if (y.length == 2) 2000 + y.toInt() else y.toInt()

    private fun safe(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate.of(y, m, d) }.getOrNull()

    /** The next [day]/[month] from today on (this year, or next year when it has passed). */
    private fun upcoming(day: Int, month: Int, today: LocalDate): LocalDate? {
        val d = safe(today.year, month, day) ?: safe(today.year + 1, month, day) ?: return null
        return if (d.isBefore(today)) safe(today.year + 1, month, day) else d
    }

    /**
     * A time or time range at word [i]: "14:00", "14:00-15:30", "14 - 16u", "2pm to 4pm", "14u tot 15u".
     * Returns start, end (or null) and how many words it took.
     */
    private fun timeAt(low: List<String>, i: Int): Triple<LocalTime, LocalTime?, Int>? {
        val w = low[i]
        // German "Uhr" after a number makes it a time and is taken with it: "14 Uhr", "14-16 Uhr", "14 bis 16 Uhr".
        fun uhr(at: Int) = low.getOrNull(at) == "uhr"
        fun withUhr(start: LocalTime, end: LocalTime?, n: Int) = Triple(start, end, if (uhr(i + n)) n + 1 else n)
        // One word with a dash: "14:00-15:30", "14-16u".
        TIME_RANGE.matchEntire(w)?.let { m ->
            val a = m.groupValues[1]
            val b = m.groupValues[2]
            val endT = time(b, null) ?: return@let
            val startT = time(a, b) ?: return@let
            if (looksLikeTime(a) || looksLikeTime(b) || uhr(i + 1)) return withUhr(startT, endT, 1)
        }
        val startT = time(w, null) ?: return null
        // A bare number ("3") isn't a time unless a range, a linking word or "Uhr" makes it one.
        val bare = !looksLikeTime(w) && !uhr(i + 1)
        val n1 = low.getOrNull(i + 1)
        // "14:00 -15:00"
        if (n1 != null && n1.length > 1 && (n1[0] == '-' || n1[0] == '–')) {
            val other = n1.drop(1)
            val e = time(other, null)
            if (e != null && (!bare || looksLikeTime(other) || uhr(i + 2))) return withUhr(time(w, other) ?: startT, e, 2)
        }
        // "14:00 - 15:00", "2pm to 4pm", "14u tot 16u", "14h à 16h", "14 bis 16 Uhr"
        if (n1 != null && n1 in RANGE) {
            val other = low.getOrNull(i + 2)
            val e = other?.let { time(it, null) }
            if (other != null && e != null && (!bare || looksLikeTime(other) || uhr(i + 3))) return withUhr(time(w, other) ?: startT, e, 3)
        }
        if (bare) {
            val before = low.getOrNull(i - 1)
            if (before !in AT) return null
        }
        return withUhr(startT, null, 1)
    }

    private fun looksLikeTime(w: String): Boolean = TIME.matchEntire(w)?.let { m ->
        m.groupValues[2].isNotEmpty() || m.groupValues[3].isNotEmpty() || m.groupValues[4].isNotEmpty() || w.endsWith("u") || w.endsWith("h")
    } ?: false

    /**
     * One time: "14:00", "14.00", "14u30", "14u", "14h", "2pm", "2:30 p.m.", "14". [partner] is the other end of a
     * range: "2-4pm" takes the pm of 4pm for the 2.
     */
    private fun time(w: String, partner: String?): LocalTime? {
        val m = TIME.matchEntire(w) ?: return null
        var h = m.groupValues[1].toInt()
        val min = (m.groupValues[2].ifEmpty { m.groupValues[3] }).ifEmpty { "0" }.toInt()
        var ampm = m.groupValues[4]
        if (ampm.isEmpty() && partner != null) ampm = TIME.matchEntire(partner)?.groupValues?.get(4).orEmpty()
        if (ampm.startsWith("p") && h in 1..11) h += 12
        if (ampm.startsWith("a") && h == 12) h = 0
        if (ampm.isNotEmpty() && m.groupValues[1].toInt() !in 1..12) return null
        if (h !in 0..23 || min !in 0..59) return null
        return LocalTime.of(h, min)
    }
}
