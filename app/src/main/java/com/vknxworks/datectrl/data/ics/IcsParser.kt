package com.vknxworks.datectrl.data.ics

import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Reads iCalendar (.ics) files: VEVENTs with their times (time zones from TZID and VTIMEZONE, all-day dates, UTC
 * and floating times), title, place, description, repeat rule and skipped dates, changed occurrences and
 * reminders (VALARM). Everything else in the file is skipped.
 *
 * Files come from other apps, so reading is bounded ([IcsLimits]): the size, the number of events, the length of
 * one property and how deeply components nest. iCalendar has no entities, includes or references that get
 * resolved: nothing in the file is ever fetched or expanded (no TZURL, ATTACH or URL), and XML-based calendars
 * (xCal) or HTML pages are refused because the first line must be BEGIN:VCALENDAR. Unfolding is linear in the
 * size of the file.
 */
object IcsParser {

    /** One content line: NAME;PARAM=value:VALUE. Names and parameter names are upper case. */
    data class Prop(val name: String, val params: Map<String, String>, val value: String) {
        fun param(key: String): String? = params[key]
    }

    internal class Component(val name: String) {
        val props = ArrayList<Prop>()
        val children = ArrayList<Component>()
        fun first(name: String): Prop? = props.firstOrNull { it.name == name }
        fun all(name: String): List<Prop> = props.filter { it.name == name }
    }

    private val DATE = Regex("^(\\d{4})(\\d{2})(\\d{2})$")
    private val DATE_TIME = Regex("^(\\d{4})(\\d{2})(\\d{2})T(\\d{2})(\\d{2})(\\d{2})?(Z)?$")
    private val RRULE_SAFE = Regex("^[A-Z0-9=;,:+\\-]+$")

    /** Reads at most [IcsLimits.MAX_BYTES] from [input] (UTF-8). */
    fun parse(input: InputStream, defaultZone: ZoneId = ZoneId.systemDefault()): IcsParseResult =
        parse(InputStreamReader(LimitedInputStream(input, IcsLimits.MAX_BYTES), Charsets.UTF_8), defaultZone)

    fun parse(text: String, defaultZone: ZoneId = ZoneId.systemDefault()): IcsParseResult {
        if (text.length > IcsLimits.MAX_BYTES) throw IcsException(IcsException.Reason.TOO_BIG)
        return parse(StringReader(text), defaultZone)
    }

    fun parse(reader: Reader, defaultZone: ZoneId): IcsParseResult {
        val root = readComponents(reader)
        return convert(root, defaultZone)
    }

    // ---------- Lines and components ----------

    /** Calls [onLine] with every logical (unfolded) line. Continuation lines start with a space or a tab. */
    internal fun unfold(reader: Reader, onLine: (String) -> Unit) {
        val br = if (reader is BufferedReader) reader else BufferedReader(reader, 16 * 1024)
        val cur = StringBuilder()
        var overflow = false
        var have = false
        fun flush() {
            if (have && !overflow) onLine(cur.toString())
            cur.setLength(0)
            overflow = false
            have = false
        }
        while (true) {
            val raw = br.readLine() ?: break
            if (raw.isNotEmpty() && (raw[0] == ' ' || raw[0] == '\t')) {
                if (!have) continue
                if (!overflow) {
                    if (cur.length + raw.length - 1 > IcsLimits.MAX_LINE) overflow = true else cur.append(raw, 1, raw.length)
                }
                continue
            }
            flush()
            if (raw.isEmpty()) continue
            have = true
            if (raw.length > IcsLimits.MAX_LINE) overflow = true else cur.append(raw)
        }
        flush()
    }

    /** Splits a content line into name, parameters and value; null if it isn't one. */
    internal fun parseLine(line: String): Prop? {
        val n = line.length
        var i = 0
        while (i < n && line[i] != ';' && line[i] != ':') i++
        if (i >= n || i == 0) return null
        // "item1.X-ABLABEL": the group prefix isn't part of the name.
        val name = line.substring(0, i).trim().substringAfterLast('.').uppercase()
        val params = HashMap<String, String>()
        while (i < n && line[i] == ';') {
            i++
            val ks = i
            while (i < n && line[i] != '=' && line[i] != ';' && line[i] != ':') i++
            val key = line.substring(ks, i).trim().uppercase()
            val value = StringBuilder()
            if (i < n && line[i] == '=') {
                i++
                while (i < n && line[i] != ';' && line[i] != ':') {
                    if (line[i] == '"') {
                        i++
                        val qs = i
                        while (i < n && line[i] != '"') i++
                        value.append(line, qs, i)
                        if (i < n) i++
                    } else {
                        value.append(line[i])
                        i++
                    }
                }
            }
            if (key.isNotEmpty() && params.size < IcsLimits.MAX_PARAMS) params.putIfAbsent(key, value.toString())
        }
        if (i >= n || line[i] != ':') return null
        return Prop(name, params, line.substring(i + 1))
    }

    /** The components we use (VCALENDAR, VEVENT, VALARM, VTIMEZONE and its observances); the rest is skipped. */
    private val KEEP = mapOf(
        "" to setOf("VCALENDAR"),
        "VCALENDAR" to setOf("VEVENT", "VTIMEZONE"),
        "VEVENT" to setOf("VALARM"),
        "VTIMEZONE" to setOf("STANDARD", "DAYLIGHT"),
    )

    internal class Tree(val calendars: List<Component>, val truncated: Boolean)

    internal fun readComponents(reader: Reader): Tree {
        val calendars = ArrayList<Component>()
        val stack = ArrayList<Component>()
        var skipDepth = 0
        var seenFirst = false
        var events = 0
        var timezones = 0
        var truncated = false
        try {
            unfold(reader) { raw ->
                val line = if (!seenFirst) raw.removePrefix("\uFEFF").trimStart() else raw
                if (!seenFirst) {
                    if (line.isBlank()) return@unfold
                    seenFirst = true
                    if (!line.equals("BEGIN:VCALENDAR", ignoreCase = true)) throw IcsException(IcsException.Reason.NOT_ICALENDAR)
                }
                val upper = line.trimEnd()
                when {
                    upper.startsWith("BEGIN:", ignoreCase = true) -> {
                        val name = upper.substring(6).trim().uppercase()
                        if (skipDepth > 0) {
                            skipDepth++
                            if (skipDepth > 1000) throw IcsException(IcsException.Reason.NOT_ICALENDAR)
                            return@unfold
                        }
                        val parent = stack.lastOrNull()?.name ?: ""
                        val keep = name in KEEP[parent].orEmpty() && stack.size < IcsLimits.MAX_DEPTH
                        val overLimit = when (name) {
                            "VEVENT" -> events >= IcsLimits.MAX_EVENTS
                            "VTIMEZONE" -> timezones >= IcsLimits.MAX_TIMEZONES
                            "STANDARD", "DAYLIGHT" -> (stack.lastOrNull()?.children?.size ?: 0) >= IcsLimits.MAX_OBSERVANCES
                            "VALARM" -> (stack.lastOrNull()?.children?.size ?: 0) >= IcsLimits.MAX_REMINDERS * 4
                            else -> false
                        }
                        if (!keep || overLimit) {
                            if (name == "VEVENT" && overLimit) truncated = true
                            skipDepth = 1
                            return@unfold
                        }
                        if (name == "VEVENT") events++
                        if (name == "VTIMEZONE") timezones++
                        val comp = Component(name)
                        stack.lastOrNull()?.children?.add(comp) ?: calendars.add(comp)
                        stack += comp
                    }
                    upper.startsWith("END:", ignoreCase = true) -> {
                        if (skipDepth > 0) {
                            skipDepth--
                            return@unfold
                        }
                        val name = upper.substring(4).trim().uppercase()
                        // Close up to the matching BEGIN (a file with a missing END still reads).
                        val at = stack.indexOfLast { it.name == name }
                        if (at >= 0) while (stack.size > at) stack.removeAt(stack.size - 1)
                    }
                    skipDepth > 0 -> {}
                    else -> {
                        val comp = stack.lastOrNull() ?: return@unfold
                        if (comp.props.size >= IcsLimits.MAX_PROPS) return@unfold
                        parseLine(line)?.let { comp.props += it }
                    }
                }
            }
        } catch (e: IcsException) {
            throw e
        } catch (e: TooBigException) {
            throw IcsException(IcsException.Reason.TOO_BIG)
        }
        if (!seenFirst) throw IcsException(IcsException.Reason.NOT_ICALENDAR)
        return Tree(calendars, truncated)
    }

    // ---------- Values ----------

    /** "20261113" or "20261113T090000" (also with Z, or without seconds) as a local date-time; null if neither. */
    internal fun localDateTime(v: String?): LocalDateTime? {
        val s = v?.trim() ?: return null
        DATE.matchEntire(s)?.let { m ->
            return runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()).atStartOfDay() }.getOrNull()
        }
        val m = DATE_TIME.matchEntire(s) ?: return null
        return runCatching {
            LocalDateTime.of(
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toIntOrNull() ?: 0,
            )
        }.getOrNull()
    }

    /** A point in time from a DTSTART-like value. */
    internal data class When(val allDay: Boolean, val millis: Long, val zone: String, val utc: Boolean)

    internal class Context(val zones: ZoneResolver, val floating: ZoneId)

    /** One value of a date or date-time property, with the property's VALUE and TZID. */
    internal fun parseWhen(value: String, params: Map<String, String>, ctx: Context): When? {
        val s = value.trim()
        val isDate = params["VALUE"].equals("DATE", ignoreCase = true) || DATE.matches(s)
        if (isDate) {
            val m = DATE.matchEntire(s.take(8)) ?: return null
            val d = runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull() ?: return null
            return When(true, d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), "UTC", true)
        }
        val m = DATE_TIME.matchEntire(s) ?: return null
        val t = localDateTime(s.removeSuffix("Z")) ?: return null
        if (m.groupValues[7] == "Z") return When(false, t.toInstant(ZoneOffset.UTC).toEpochMilli(), "UTC", true)
        val tzid = params["TZID"]
        if (tzid.isNullOrBlank()) {
            // A floating time: the same clock time wherever you are; here, the calendar's or the phone's zone.
            return When(false, java.time.ZonedDateTime.ofLocal(t, ctx.floating, null).toInstant().toEpochMilli(), ctx.floating.id, false)
        }
        val z = ctx.zones.resolve(tzid, t.year)
        return When(false, z.toMillis(t), z.id, false)
    }

    private fun parseWhen(p: Prop?, ctx: Context): When? = p?.let { parseWhen(it.value, it.params, ctx) }

    // ---------- Events ----------

    private fun convert(tree: Tree, defaultZone: ZoneId): IcsParseResult {
        val timezones = HashMap<String, VTimezone>()
        var floating = defaultZone
        for (cal in tree.calendars) {
            cal.first("X-WR-TIMEZONE")?.value?.trim()?.let { tz -> runCatching { ZoneId.of(tz) }.getOrNull()?.let { floating = it } }
            for (c in cal.children) if (c.name == "VTIMEZONE") {
                val id = c.first("TZID")?.value?.trim() ?: continue
                val obs = c.children.mapNotNull { VTimezone.observance(it.name == "DAYLIGHT", it.props) }
                timezones.putIfAbsent(id, VTimezone(id, c.first("X-LIC-LOCATION")?.value?.trim(), obs))
            }
        }
        val ctx = Context(ZoneResolver(timezones, defaultZone), floating)
        val masters = LinkedHashMap<String, IcsEvent>()
        val changed = ArrayList<Pair<IcsEvent, Boolean>>()
        val reused = ArrayList<IcsEvent>()
        var skipped = 0
        for (cal in tree.calendars) for (c in cal.children) {
            if (c.name != "VEVENT") continue
            val e = runCatching { event(c, ctx) }.getOrNull()
            if (e == null) {
                skipped++
                continue
            }
            val cancelled = c.first("STATUS")?.value?.trim().equals("CANCELLED", ignoreCase = true)
            if (e.recurrenceId != null) {
                changed += e to cancelled
            } else if (cancelled) {
                skipped++
            } else if (masters.containsKey(e.uid)) {
                // The same UID twice without RECURRENCE-ID: the same event twice, or (from careless apps) another
                // event that reused the UID. Kept when it starts at another time.
                if (masters.getValue(e.uid).start == e.start || reused.any { it.uid == e.uid && it.start == e.start }) skipped++ else reused += e
            } else {
                masters[e.uid] = e
            }
        }
        // Changed and cancelled occurrences go with their repeating event; without one they're events of their own.
        val standalone = ArrayList<IcsEvent>()
        for ((o, cancelled) in changed) {
            val m = masters[o.uid]
            if (m != null && m.repeating) {
                masters[o.uid] = if (cancelled) {
                    m.copy(exdates = (m.exdates + o.recurrenceId!!).distinct().take(IcsLimits.MAX_EXDATES))
                } else if (m.overrides.size < IcsLimits.MAX_OVERRIDES) {
                    m.copy(overrides = m.overrides + o.copy(rrule = null, exdates = emptyList()))
                } else {
                    skipped++
                    m
                }
            } else if (!cancelled) {
                standalone += o.copy(recurrenceId = null, rrule = null, exdates = emptyList(), uid = derivedUid(o.uid, o.recurrenceId!!))
            } else {
                skipped++
            }
        }
        val all = (masters.values + reused + standalone).sortedBy { it.start }
        return IcsParseResult(all, skipped, tree.truncated)
    }

    private fun event(c: Component, ctx: Context): IcsEvent? {
        val startProp = c.first("DTSTART") ?: return null
        val start = parseWhen(startProp, ctx) ?: return null
        val rrule = c.first("RRULE")?.value?.trim()?.uppercase()?.removePrefix("RRULE:")
            ?.takeIf { it.length <= IcsLimits.MAX_RRULE && RRULE_SAFE.matches(it) && "FREQ=" in it }
        val endWhen = parseWhen(c.first("DTEND"), ctx)
        var end = when {
            endWhen != null && start.allDay -> if (endWhen.allDay) endWhen.millis else startOfUtcDay(endWhen.millis)
            endWhen != null -> endWhen.millis
            else -> IcsDuration.parse(c.first("DURATION")?.value)?.takeIf { it >= 0 }?.let { d ->
                if (start.allDay) start.millis + (d / DAY_MS).coerceAtLeast(1) * DAY_MS else start.millis + d
            } ?: if (start.allDay) start.millis + DAY_MS else start.millis
        }
        if (start.allDay && end <= start.millis) end = start.millis + DAY_MS
        if (!start.allDay && end < start.millis) end = start.millis

        val title = text(c, "SUMMARY", IcsLimits.MAX_TITLE)
        val uid = c.first("UID")?.value?.let { IcsText.unescape(it).trim() }?.takeIf { it.isNotEmpty() }?.take(IcsLimits.MAX_UID)
            ?: generatedUid(start.millis, end, title)

        val exdates = if (rrule == null) emptyList() else c.all("EXDATE").asSequence()
            .flatMap { p -> p.value.split(',').asSequence().mapNotNull { v -> parseWhen(v, p.params, ctx)?.let { w -> if (start.allDay && !w.allDay) startOfUtcDay(w.millis) else w.millis } } }
            .distinct().take(IcsLimits.MAX_EXDATES).toList()

        val recurrenceId = parseWhen(c.first("RECURRENCE-ID"), ctx)?.millis

        // A time in UTC that doesn't repeat is shown in the phone's zone; a repeating one keeps UTC (its rule is in UTC).
        val zone = when {
            start.allDay -> "UTC"
            start.utc && rrule == null -> ctx.floating.id
            else -> start.zone
        }
        return IcsEvent(
            uid = uid,
            title = title,
            location = text(c, "LOCATION", IcsLimits.MAX_LOCATION),
            description = text(c, "DESCRIPTION", IcsLimits.MAX_DESCRIPTION),
            allDay = start.allDay,
            start = start.millis,
            end = end,
            timeZone = zone,
            rrule = rrule,
            exdates = exdates,
            reminders = reminders(c, start.millis, end),
            recurrenceId = recurrenceId,
        )
    }

    private fun text(c: Component, name: String, max: Int): String =
        c.first(name)?.value?.let { IcsText.unescape(it).trim() }?.take(max).orEmpty()

    /** Display and sound alarms as minutes before the start; e-mail and other alarms are left out. */
    private fun reminders(c: Component, start: Long, end: Long): List<Int> {
        val out = ArrayList<Int>()
        for (a in c.children) {
            if (a.name != "VALARM") continue
            val action = a.first("ACTION")?.value?.trim()?.uppercase() ?: "DISPLAY"
            if (action != "DISPLAY" && action != "AUDIO") continue
            val trigger = a.first("TRIGGER") ?: continue
            val before: Long = if (trigger.param("VALUE").equals("DATE-TIME", ignoreCase = true)) {
                val t = localDateTime(trigger.value.trim().removeSuffix("Z")) ?: continue
                start - t.toInstant(ZoneOffset.UTC).toEpochMilli()
            } else {
                val d = IcsDuration.parse(trigger.value) ?: continue
                val related = if (trigger.param("RELATED").equals("END", ignoreCase = true)) end else start
                start - (related + d)
            }
            if (before < 0) continue
            val minutes = before / 60_000L
            if (minutes > IcsLimits.MAX_REMINDER_MINUTES) continue
            if (minutes.toInt() !in out) out += minutes.toInt()
        }
        return out.sorted().take(IcsLimits.MAX_REMINDERS)
    }

    private fun startOfUtcDay(ms: Long): Long = Math.floorDiv(ms, DAY_MS) * DAY_MS

    /** A stable UID for an event that has none, so importing the same file twice still finds the duplicate. */
    internal fun generatedUid(start: Long, end: Long, title: String): String = "datectrl-" + sha(listOf(start, end, title).joinToString("\u0000")).take(32)

    /** A changed occurrence imported on its own gets its own UID, so it can't clash with the series. */
    private fun derivedUid(uid: String, recurrenceId: Long): String = (uid.take(200) + "-" + recurrenceId)

    private fun sha(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }

    /**
     * Android's EXDATE column: "20261105T100000Z,20261112T100000Z", sometimes "Europe/Brussels;20261105T110000,..."
     * or several such lists on separate lines. Milliseconds, in order.
     */
    fun parseAndroidExdates(s: String?, defaultZone: ZoneId): List<Long> {
        if (s.isNullOrBlank()) return emptyList()
        val out = ArrayList<Long>()
        for (line in s.split('\n')) {
            val l = line.trim()
            if (l.isEmpty()) continue
            val semi = l.indexOf(';')
            val zone = if (semi > 0) runCatching { ZoneId.of(l.substring(0, semi)) }.getOrNull() ?: defaultZone else ZoneOffset.UTC
            val values = if (semi > 0) l.substring(semi + 1) else l
            for (v in values.split(',')) {
                val t = v.trim()
                val local = localDateTime(t.removeSuffix("Z")) ?: continue
                val ms = if (t.endsWith("Z") || DATE.matches(t)) local.toInstant(ZoneOffset.UTC).toEpochMilli()
                else java.time.ZonedDateTime.ofLocal(local, zone, null).toInstant().toEpochMilli()
                out += ms
                if (out.size >= IcsLimits.MAX_EXDATES) return out.distinct().sorted()
            }
        }
        return out.distinct().sorted()
    }

    private const val DAY_MS = 86_400_000L

    private class TooBigException : RuntimeException()

    /** Fails once more than [max] bytes have been read. */
    private class LimitedInputStream(input: InputStream, private val max: Long) : FilterInputStream(input) {
        private var count = 0L
        private fun add(n: Long) {
            if (n > 0) {
                count += n
                if (count > max) throw TooBigException()
            }
        }
        override fun read(): Int = super.read().also { if (it >= 0) add(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { add(it.toLong()) }
        override fun skip(n: Long): Long = super.skip(n).also { add(it) }
    }
}
