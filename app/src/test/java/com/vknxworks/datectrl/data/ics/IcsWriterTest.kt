package com.vknxworks.datectrl.data.ics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class IcsWriterTest {
    private val brussels = ZoneId.of("Europe/Brussels")
    private val now = Instant.parse("2026-11-13T12:00:00Z")

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun utcDay(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private val timed = IcsEvent(
        uid = "series-1@datectrl", title = "Team; sync, weekly \\ notes", location = "Room 2\nFloor 3",
        description = "Agenda:\n- one\n- two", start = ms("2026-11-02T09:00:00Z"), end = ms("2026-11-02T10:00:00Z"),
        timeZone = "Europe/Brussels", rrule = "FREQ=WEEKLY;BYDAY=MO", exdates = listOf(ms("2026-11-09T09:00:00Z")),
        reminders = listOf(10, 1440),
        overrides = listOf(
            IcsEvent(uid = "series-1@datectrl", title = "Team sync (moved)", start = ms("2026-11-16T13:00:00Z"), end = ms("2026-11-16T14:00:00Z"),
                timeZone = "Europe/Brussels", recurrenceId = ms("2026-11-16T09:00:00Z")),
        ),
    )
    private val allDay = IcsEvent(uid = "holiday", title = "Holiday", allDay = true, start = utcDay(2026, 12, 24), end = utcDay(2026, 12, 27))
    private val utc = IcsEvent(uid = "utc", title = "Call", start = ms("2026-11-20T15:00:00Z"), end = ms("2026-11-20T15:30:00Z"), timeZone = "UTC")

    private fun write(vararg e: IcsEvent) = IcsWriter.write(e.toList(), now)

    @Test
    fun linesEndInCrlfAndAreFoldedAt75Octets() {
        val long = IcsEvent(uid = "x", title = "Ünïcödé 🎉 ".repeat(40), description = "é".repeat(300), start = 0, end = 0)
        val text = write(long, timed)
        assertTrue(text.endsWith("\r\n"))
        assertFalse("bare LF", Regex("[^\r]\n").containsMatchIn(text))
        assertFalse("bare CR", Regex("\r[^\n]").containsMatchIn(text))
        for (line in text.split("\r\n")) {
            assertTrue("${line.toByteArray(Charsets.UTF_8).size} octets: $line", line.toByteArray(Charsets.UTF_8).size <= 75)
            // Never split inside a character: every physical line is valid UTF-8 on its own.
            assertEquals(line, String(line.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
        }
        // Unfolding gives the original back.
        val back = IcsParser.parse(text, brussels).events.first { it.uid == "x" }
        assertEquals(long.title.trim(), back.title)
        assertEquals(long.description, back.description)
    }

    @Test
    fun foldingKeepsSurrogatePairsTogether() {
        val out = StringBuilder()
        IcsText.fold("SUMMARY:" + "🎉".repeat(40), out)
        for (line in out.split("\r\n").filter { it.isNotEmpty() }) {
            val bytes = line.toByteArray(Charsets.UTF_8)
            assertTrue(bytes.size <= 75)
            assertFalse(line.contains('�'))
            assertEquals(0, line.trimStart().length % 2)
        }
    }

    @Test
    fun textIsEscaped() {
        val text = write(timed)
        assertTrue(text.contains("SUMMARY:Team\\; sync\\, weekly \\\\ notes\r\n"))
        assertTrue(text.contains("LOCATION:Room 2\\nFloor 3\r\n"))
        assertEquals("a\\nb\\nc", IcsText.escape("a\r\nb\rc"))
        assertEquals("ab", IcsText.escape("a\u0007b"))
    }

    @Test
    fun requiredPropertiesArePresent() {
        val text = write(timed, allDay, utc)
        assertTrue(text.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:"))
        assertTrue(text.trimEnd().endsWith("END:VCALENDAR"))
        val events = text.split("BEGIN:VEVENT").drop(1)
        assertEquals(4, events.size)
        for (e in events) {
            assertTrue(e.contains("\r\nUID:"))
            assertTrue(e.contains("\r\nDTSTAMP:20261113T120000Z\r\n"))
            assertTrue(e.contains("\r\nDTSTART"))
        }
        assertTrue(text.contains("DTSTART;TZID=Europe/Brussels:20261102T100000\r\n"))
        assertTrue(text.contains("DTSTART;VALUE=DATE:20261224\r\nDTEND;VALUE=DATE:20261227\r\n"))
        assertTrue(text.contains("DTSTART:20261120T150000Z\r\n"))
        assertTrue(text.contains("EXDATE;TZID=Europe/Brussels:20261109T100000\r\n"))
        assertTrue(text.contains("RECURRENCE-ID;TZID=Europe/Brussels:20261116T100000\r\n"))
        assertTrue(text.contains("TRIGGER:-PT10M\r\n"))
        assertTrue(text.contains("TRIGGER:-P1D\r\n"))
        // Exactly one VTIMEZONE, for the zone that's used.
        assertEquals(1, Regex("BEGIN:VTIMEZONE").findAll(text).count())
        assertTrue(text.contains("TZID:Europe/Brussels\r\n"))
    }

    @Test
    fun roundTrip() {
        val back = IcsParser.parse(write(timed, allDay, utc), brussels).events.associateBy { it.uid }
        val t = back.getValue("series-1@datectrl")
        assertEquals(timed.title, t.title)
        assertEquals(timed.location, t.location)
        assertEquals(timed.description, t.description)
        assertEquals(timed.start, t.start)
        assertEquals(timed.end, t.end)
        assertEquals("Europe/Brussels", t.timeZone)
        assertEquals(timed.rrule, t.rrule)
        assertEquals(timed.exdates, t.exdates)
        assertEquals(timed.reminders, t.reminders)
        val o = t.overrides.single()
        assertEquals(timed.overrides[0].recurrenceId, o.recurrenceId)
        assertEquals(timed.overrides[0].start, o.start)
        assertEquals(timed.overrides[0].title, o.title)
        val a = back.getValue("holiday")
        assertTrue(a.allDay)
        assertEquals(allDay.start, a.start)
        assertEquals(allDay.end, a.end)
        val u = back.getValue("utc")
        assertEquals(utc.start, u.start)
        assertEquals(utc.end, u.end)
    }

    /** The generated VTIMEZONE alone (under a name no system knows) gives the same times as the real zone. */
    @Test
    fun generatedVTimezonesAreRight() {
        for (zoneId in listOf("Europe/Brussels", "America/New_York", "Australia/Sydney", "Asia/Kolkata", "America/Sao_Paulo")) {
            val zone = ZoneId.of(zoneId)
            val starts = listOf("2026-01-15T09:00:00Z", "2026-03-20T09:00:00Z", "2026-03-25T09:00:00Z", "2026-03-11T09:00:00Z", "2026-10-28T09:00:00Z", "2026-04-02T09:00:00Z", "2026-07-01T09:00:00Z", "2026-10-30T09:00:00Z", "2026-11-05T09:00:00Z").map { ms(it) }
            val events = starts.mapIndexed { i, s -> IcsEvent(uid = "e$i", start = s, end = s + 3_600_000, timeZone = zoneId) }
            val text = IcsWriter.write(events, now).replace("TZID:$zoneId", "TZID:Nowhere/Custom").replace("TZID=$zoneId", "TZID=Nowhere/Custom")
            // Read in a zone that differs from every sample, so a match can't come from the phone's zone.
            val back = IcsParser.parse(text, ZoneId.of("Pacific/Chatham")).events.associateBy { it.uid }
            starts.forEachIndexed { i, s -> assertEquals("$zoneId e$i", s, back.getValue("e$i").start) }
        }
    }

    @Test
    fun vtimezoneRules() {
        val lines = IcsWriter.vtimezone(ZoneId.of("America/New_York"), 2026)
        assertTrue(lines.contains("RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=2SU"))
        assertTrue(lines.contains("RRULE:FREQ=YEARLY;BYMONTH=11;BYDAY=1SU"))
        assertTrue(lines.contains("TZOFFSETTO:-0400"))
        val eu = IcsWriter.vtimezone(ZoneId.of("Europe/Brussels"), 2026)
        assertTrue(eu.contains("RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU"))
        assertTrue(eu.contains("BEGIN:DAYLIGHT"))
        val fixed = IcsWriter.vtimezone(ZoneId.of("Asia/Kolkata"), 2026)
        assertTrue(fixed.contains("TZOFFSETTO:+0530"))
        assertFalse(fixed.any { it.startsWith("RRULE") })
        assertEquals("-0330", IcsWriter.offset(ZoneOffset.ofHoursMinutes(-3, -30)))
    }

    @Test
    fun unalignedWeekdayRulesAreExact() {
        val one = ZoneOffset.ofHours(1)
        val two = ZoneOffset.ofHours(2)
        fun rule(day: Int) = java.time.zone.ZoneOffsetTransitionRule.of(
            java.time.Month.MARCH, day, java.time.DayOfWeek.SUNDAY, java.time.LocalTime.of(2, 0), false,
            java.time.zone.ZoneOffsetTransitionRule.TimeDefinition.WALL, one, one, two,
        )
        assertEquals("FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU", IcsWriter.rrule(rule(25)))
        assertEquals("FREQ=YEARLY;BYMONTH=3;BYDAY=2SU", IcsWriter.rrule(rule(8)))
        assertEquals("FREQ=YEARLY;BYMONTH=3;BYDAY=SU;BYMONTHDAY=2,3,4,5,6,7,8", IcsWriter.rrule(rule(2)))
        assertEquals("FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU", IcsWriter.rrule(rule(-1)))
        assertEquals("FREQ=YEARLY;BYMONTH=3;BYDAY=SU;BYMONTHDAY=-8,-7,-6,-5,-4,-3,-2", IcsWriter.rrule(rule(-2)))
        // And the reader understands that form: Sunday on or after 2 March 2026 is the 8th.
        val e = IcsParser.parse(
            listOf(
                "BEGIN:VCALENDAR", "BEGIN:VTIMEZONE", "TZID:Odd/Rule",
                "BEGIN:DAYLIGHT", "DTSTART:20000305T020000", "TZOFFSETFROM:+0117", "TZOFFSETTO:+0217", "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=SU;BYMONTHDAY=2,3,4,5,6,7,8", "END:DAYLIGHT",
                "BEGIN:STANDARD", "DTSTART:20001001T030000", "TZOFFSETFROM:+0217", "TZOFFSETTO:+0117", "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=1SU", "END:STANDARD",
                "END:VTIMEZONE",
                "BEGIN:VEVENT", "UID:a", "DTSTART;TZID=Odd/Rule:20260307T120000", "END:VEVENT",
                "BEGIN:VEVENT", "UID:b", "DTSTART;TZID=Odd/Rule:20260309T120000", "END:VEVENT",
                "END:VCALENDAR",
            ).joinToString("\r\n"),
            brussels,
        ).events.associateBy { it.uid }
        assertEquals(ms("2026-03-07T10:43:00Z"), e.getValue("a").start)
        assertEquals(ms("2026-03-09T09:43:00Z"), e.getValue("b").start)
    }

    @Test
    fun repeatRulesThatAreUnsafeAreLeftOut() {
        val text = write(utc.copy(rrule = "FREQ=DAILY\r\nX-INJECT:1"))
        assertFalse(text.contains("X-INJECT"))
        assertFalse(text.contains("RRULE"))
    }
}
