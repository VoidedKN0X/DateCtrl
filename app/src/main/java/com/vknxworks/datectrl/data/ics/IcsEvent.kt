package com.vknxworks.datectrl.data.ics

/**
 * One event read from or written to an .ics file (RFC 5545). Times are epoch milliseconds. All-day events are
 * stored the way Android stores them: at UTC midnight, [end] exclusive (the midnight after the last day).
 * Nothing here touches Android, so it can be tested on its own.
 */
data class IcsEvent(
    val uid: String,
    val title: String = "",
    val location: String = "",
    val description: String = "",
    val allDay: Boolean = false,
    val start: Long,
    val end: Long,
    /** The IANA time zone the times belong to ("UTC" for all-day events and times given in UTC). */
    val timeZone: String = "UTC",
    val rrule: String? = null,
    /** Starts of the occurrences a repeating event skips. */
    val exdates: List<Long> = emptyList(),
    /** Reminders, in minutes before the start. */
    val reminders: List<Int> = emptyList(),
    /** For a changed occurrence of a repeating event: the start of the occurrence it replaces. */
    val recurrenceId: Long? = null,
    /** Changed occurrences of this (repeating) event. */
    val overrides: List<IcsEvent> = emptyList(),
) {
    val repeating: Boolean get() = !rrule.isNullOrBlank()
    val lengthMs: Long get() = (end - start).coerceAtLeast(0)
}

/** What reading a file gave: the events, how many couldn't be used, and whether the file had more than the limit. */
data class IcsParseResult(val events: List<IcsEvent>, val skipped: Int, val truncated: Boolean)

/** Why a file couldn't be read at all. */
class IcsException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { TOO_BIG, NOT_ICALENDAR }
}

/** Bounds for reading files from other apps: big or hostile files can't exhaust memory or time. */
object IcsLimits {
    const val MAX_BYTES = 8L * 1024 * 1024
    const val MAX_EVENTS = 5_000
    /** One unfolded property (a long description, for example). Longer ones are dropped. */
    const val MAX_LINE = 256 * 1024
    const val MAX_DEPTH = 8
    const val MAX_PROPS = 500
    const val MAX_PARAMS = 20
    const val MAX_TIMEZONES = 200
    const val MAX_OBSERVANCES = 50
    const val MAX_REMINDERS = 5
    const val MAX_EXDATES = 1_000
    const val MAX_OVERRIDES = 500
    const val MAX_TITLE = 500
    const val MAX_LOCATION = 500
    const val MAX_DESCRIPTION = 10_000
    const val MAX_UID = 255
    const val MAX_RRULE = 500
    /** Reminders further ahead than this (60 days) are left out. */
    const val MAX_REMINDER_MINUTES = 60 * 24 * 60
}

/** iCalendar TEXT values: escaping, unescaping and line folding (RFC 5545 3.1 and 3.3.11). */
object IcsText {
    /** Escapes backslash, semicolon, comma and line breaks; drops other control characters. */
    fun escape(s: String): String {
        val out = StringBuilder(s.length + 8)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' -> out.append("\\\\")
                c == ';' -> out.append("\\;")
                c == ',' -> out.append("\\,")
                c == '\r' -> {
                    out.append("\\n")
                    if (i + 1 < s.length && s[i + 1] == '\n') i++
                }
                c == '\n' -> out.append("\\n")
                c == '\t' -> out.append(c)
                c < ' ' || c == '\u007f' -> {}
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }

    /** Undoes [escape]: "\n" and "\N" become line breaks, "\\", "\;" and "\," the character itself. */
    fun unescape(s: String): String {
        if (s.indexOf('\\') < 0) return clean(s)
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n', 'N' -> out.append('\n')
                    else -> out.append(n)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return clean(out.toString())
    }

    /** Control characters other than line breaks and tabs are dropped. */
    private fun clean(s: String): String =
        if (s.none { (it < ' ' && it != '\n' && it != '\t') || it == '\u007f' }) s
        else s.filter { !((it < ' ' && it != '\n' && it != '\t') || it == '\u007f') }

    /**
     * Folds one content line at 75 octets of UTF-8 (RFC 5545 3.1), never inside a character, and ends every
     * physical line with CRLF. Continuation lines start with a space, which counts toward their 75 octets.
     */
    fun fold(line: String, out: StringBuilder) {
        var octets = 0
        var limit = 75
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val n = Character.charCount(cp)
            val size = when {
                cp < 0x80 -> 1
                cp < 0x800 -> 2
                cp < 0x10000 -> 3
                else -> 4
            }
            if (octets + size > limit) {
                out.append("\r\n ")
                octets = 1
                limit = 75
            }
            out.appendCodePoint(cp)
            octets += size
            i += n
        }
        out.append("\r\n")
    }
}

/** RFC 5545 durations ("PT1H30M", "-P1D", "P2W"; Android's "P3600S" too). Milliseconds, or null if unreadable. */
object IcsDuration {
    private val RE = Regex("^([+-])?P(?:(\\d{1,9})W)?(?:(\\d{1,9})D)?(?:T(?:(\\d{1,9})H)?(?:(\\d{1,9})M)?(?:(\\d{1,12})S)?)?$")
    private val SECONDS_ONLY = Regex("^([+-])?P(\\d{1,12})S$")

    fun parse(s: String?): Long? {
        val v = s?.trim()?.uppercase() ?: return null
        if (v.isEmpty()) return null
        SECONDS_ONLY.matchEntire(v)?.let { m ->
            val sec = m.groupValues[2].toLong()
            return sec * 1000 * (if (m.groupValues[1] == "-") -1 else 1)
        }
        val m = RE.matchEntire(v) ?: return null
        if (m.groupValues.drop(2).all { it.isEmpty() }) return null
        fun g(i: Int) = m.groupValues[i].toLongOrNull() ?: 0L
        val sec = g(2) * 7 * 86_400 + g(3) * 86_400 + g(4) * 3_600 + g(5) * 60 + g(6)
        return sec * 1000 * (if (m.groupValues[1] == "-") -1 else 1)
    }

    /** "P2D" for whole days, otherwise "PT1H30M" (seconds only when needed). Never negative. */
    fun format(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        if (total == 0L) return "PT0S"
        if (total % 86_400 == 0L) return "P${total / 86_400}D"
        val days = total / 86_400
        val h = (total % 86_400) / 3_600
        val m = (total % 3_600) / 60
        val s = total % 60
        return buildString {
            append('P')
            if (days > 0) append(days).append('D')
            append('T')
            if (h > 0) append(h).append('H')
            if (m > 0) append(m).append('M')
            if (s > 0) append(s).append('S')
        }
    }
}
