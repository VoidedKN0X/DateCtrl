package com.vknxworks.datectrl.data.ics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class IcsParserTest {
    private val brussels = ZoneId.of("Europe/Brussels")

    private fun cal(vararg body: String) = (listOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Test//EN") + body + "END:VCALENDAR").joinToString("\r\n") + "\r\n"

    private fun parse(vararg body: String, zone: ZoneId = brussels) = IcsParser.parse(cal(*body), zone)

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun utcDay(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun timedEventWithTzid() {
        val r = parse(
            "BEGIN:VEVENT", "UID:a@test", "DTSTART;TZID=Europe/Brussels:20261113T090000", "DTEND;TZID=Europe/Brussels:20261113T103000",
            "SUMMARY:Dentist", "LOCATION:Main street 1", "DESCRIPTION:Bring the card", "END:VEVENT",
        )
        val e = r.events.single()
        assertEquals("a@test", e.uid)
        assertEquals("Dentist", e.title)
        assertEquals("Main street 1", e.location)
        assertEquals("Bring the card", e.description)
        assertFalse(e.allDay)
        assertEquals(ms("2026-11-13T08:00:00Z"), e.start)
        assertEquals(ms("2026-11-13T09:30:00Z"), e.end)
        assertEquals("Europe/Brussels", e.timeZone)
        assertEquals(0, r.skipped)
        assertFalse(r.truncated)
    }

    @Test
    fun summerTimeUsesTheZoneRules() {
        val e = parse("BEGIN:VEVENT", "UID:s", "DTSTART;TZID=Europe/Brussels:20260701T090000", "END:VEVENT").events.single()
        assertEquals(ms("2026-07-01T07:00:00Z"), e.start)
        // No DTEND or DURATION on a date-time: it takes no time.
        assertEquals(e.start, e.end)
    }

    @Test
    fun allDayDatesAreUtcMidnightWithExclusiveEnd() {
        val r = parse(
            "BEGIN:VEVENT", "UID:d1", "DTSTART;VALUE=DATE:20261224", "DTEND;VALUE=DATE:20261227", "SUMMARY:Holiday", "END:VEVENT",
            "BEGIN:VEVENT", "UID:d2", "DTSTART;VALUE=DATE:20261231", "SUMMARY:One day", "END:VEVENT",
            "BEGIN:VEVENT", "UID:d3", "DTSTART:20260101", "DURATION:P2D", "END:VEVENT",
        )
        val byUid = r.events.associateBy { it.uid }
        val a = byUid.getValue("d1")
        val b = byUid.getValue("d2")
        val c = byUid.getValue("d3")
        assertTrue(a.allDay)
        assertEquals("UTC", a.timeZone)
        assertEquals(utcDay(2026, 12, 24), a.start)
        assertEquals(utcDay(2026, 12, 27), a.end)
        assertEquals(utcDay(2026, 12, 31), b.start)
        assertEquals(utcDay(2027, 1, 1), b.end)
        assertTrue(c.allDay)
        assertEquals(utcDay(2026, 1, 1), c.start)
        assertEquals(utcDay(2026, 1, 3), c.end)
    }

    @Test
    fun utcTimesAndDuration() {
        val e = parse("BEGIN:VEVENT", "UID:u", "DTSTART:20261113T140000Z", "DURATION:PT1H30M", "END:VEVENT").events.single()
        assertEquals(ms("2026-11-13T14:00:00Z"), e.start)
        assertEquals(ms("2026-11-13T15:30:00Z"), e.end)
        // A single event in UTC is shown in the phone's zone.
        assertEquals("Europe/Brussels", e.timeZone)
        val r = parse("BEGIN:VEVENT", "UID:r", "DTSTART:20261113T140000Z", "RRULE:FREQ=WEEKLY", "END:VEVENT").events.single()
        assertEquals("UTC", r.timeZone)
    }

    @Test
    fun floatingTimeUsesTheCalendarZone() {
        val e = IcsParser.parse(cal("X-WR-TIMEZONE:America/New_York", "BEGIN:VEVENT", "UID:f", "DTSTART:20261113T090000", "END:VEVENT"), brussels).events.single()
        assertEquals(ms("2026-11-13T14:00:00Z"), e.start)
        assertEquals("America/New_York", e.timeZone)
        val g = parse("BEGIN:VEVENT", "UID:g", "DTSTART:20261113T090000", "END:VEVENT").events.single()
        assertEquals(ms("2026-11-13T08:00:00Z"), g.start)
    }

    @Test
    fun unfoldingAndEscapes() {
        val text = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:x\r\nDTSTART:20261113T090000Z\r\nSUMMARY:Long ti\r\n tle\r\n\twith tab fold\r\n" +
            "DESCRIPTION:Line one\\nLine two\\, with comma\\; semicolon \\\\ backslash\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        val e = IcsParser.parse(text, brussels).events.single()
        assertEquals("Long titlewith tab fold", e.title)
        assertEquals("Line one\nLine two, with comma; semicolon \\ backslash", e.description)
    }

    @Test
    fun bareLfAndBomAndLowercaseAreRead() {
        val text = "\uFEFFbegin:vcalendar\nbegin:vevent\nuid:x\ndtstart:20261113T090000Z\nsummary:Hi\nend:vevent\nend:vcalendar\n"
        assertEquals("Hi", IcsParser.parse(text, brussels).events.single().title)
    }

    @Test
    fun quotedParametersMayContainColons() {
        val e = parse("BEGIN:VEVENT", "UID:q", "DTSTART;TZID=\"Europe/Brussels\":20261113T090000", "LOCATION;ALTREP=\"http://x.test/a:b\":Hall", "END:VEVENT").events.single()
        assertEquals(ms("2026-11-13T08:00:00Z"), e.start)
        assertEquals("Hall", e.location)
    }

    @Test
    fun repeatRuleAndSkippedDates() {
        val e = parse(
            "BEGIN:VEVENT", "UID:w", "DTSTART;TZID=Europe/Brussels:20261102T100000", "DTEND;TZID=Europe/Brussels:20261102T110000",
            "RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=20261231T235959Z",
            "EXDATE;TZID=Europe/Brussels:20261109T100000,20261116T100000", "EXDATE;TZID=Europe/Brussels:20261123T100000", "END:VEVENT",
        ).events.single()
        assertEquals("FREQ=WEEKLY;BYDAY=MO;UNTIL=20261231T235959Z", e.rrule)
        assertEquals(listOf(ms("2026-11-09T09:00:00Z"), ms("2026-11-16T09:00:00Z"), ms("2026-11-23T09:00:00Z")), e.exdates)
        assertEquals(3_600_000L, e.lengthMs)
    }

    @Test
    fun unsafeRepeatRulesAreDropped() {
        val e = parse("BEGIN:VEVENT", "UID:w", "DTSTART:20261102T100000Z", "RRULE:FREQ=DAILY;X=<script>", "END:VEVENT").events.single()
        assertNull(e.rrule)
    }

    @Test
    fun alarmsBecomeReminders() {
        val e = parse(
            "BEGIN:VEVENT", "UID:a", "DTSTART:20261113T090000Z", "DTEND:20261113T100000Z",
            "BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER:-PT15M", "END:VALARM",
            "BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER;RELATED=END:-PT2H", "END:VALARM",
            "BEGIN:VALARM", "ACTION:AUDIO", "TRIGGER;VALUE=DATE-TIME:20261112T090000Z", "END:VALARM",
            "BEGIN:VALARM", "ACTION:EMAIL", "TRIGGER:-PT30M", "END:VALARM",
            "BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER:PT10M", "END:VALARM",
            "BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER:-PT15M", "END:VALARM",
            // Apple's "no alarm" placeholder, decades before the event.
            "BEGIN:VALARM", "ACTION:NONE", "TRIGGER;VALUE=DATE-TIME:19760401T005545Z", "END:VALARM",
            "END:VEVENT",
        ).events.single()
        assertEquals(listOf(15, 60, 1440), e.reminders)
    }

    @Test
    fun customVTimezoneMatchesASystemZone() {
        val e = parse(
            "BEGIN:VTIMEZONE", "TZID:Custom Central", "BEGIN:DAYLIGHT", "DTSTART:19810329T020000", "TZOFFSETFROM:+0100", "TZOFFSETTO:+0200",
            "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU", "END:DAYLIGHT", "BEGIN:STANDARD", "DTSTART:19961027T030000", "TZOFFSETFROM:+0200",
            "TZOFFSETTO:+0100", "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU", "END:STANDARD", "END:VTIMEZONE",
            "BEGIN:VEVENT", "UID:c1", "DTSTART;TZID=Custom Central:20260701T090000", "END:VEVENT",
            "BEGIN:VEVENT", "UID:c2", "DTSTART;TZID=Custom Central:20261201T090000", "END:VEVENT",
        )
        val (summer, winter) = r(e)
        assertEquals(ms("2026-07-01T07:00:00Z"), summer.start)
        assertEquals(ms("2026-12-01T08:00:00Z"), winter.start)
        // The phone's zone has the same rules, so it's the one stored.
        assertEquals("Europe/Brussels", summer.timeZone)
    }

    private fun r(p: IcsParseResult) = p.events

    @Test
    fun vtimezoneThatMatchesNothingIsUsedAsItIs() {
        val e = parse(
            "BEGIN:VTIMEZONE", "TZID:Odd", "BEGIN:STANDARD", "DTSTART:19700101T000000", "TZOFFSETFROM:+0317", "TZOFFSETTO:+0317", "END:STANDARD", "END:VTIMEZONE",
            "BEGIN:VEVENT", "UID:o", "DTSTART;TZID=Odd:20261113T090000", "END:VEVENT",
        ).events.single()
        assertEquals(ms("2026-11-13T05:43:00Z"), e.start)
        assertEquals("UTC", e.timeZone)
    }

    @Test
    fun otherTzidSpellings() {
        val r = parse(
            "BEGIN:VEVENT", "UID:m", "DTSTART;TZID=/mozilla.org/20050126_1/America/New_York:20261113T090000", "END:VEVENT",
            "BEGIN:VEVENT", "UID:w", "DTSTART;TZID=Romance Standard Time:20261113T090000", "END:VEVENT",
            "BEGIN:VTIMEZONE", "TZID:Lic", "X-LIC-LOCATION:Asia/Tokyo", "END:VTIMEZONE",
            "BEGIN:VEVENT", "UID:l", "DTSTART;TZID=Lic:20261113T090000", "END:VEVENT",
        ).events.associateBy { it.uid }
        assertEquals(ms("2026-11-13T14:00:00Z"), r.getValue("m").start)
        assertEquals("America/New_York", r.getValue("m").timeZone)
        assertEquals(ms("2026-11-13T08:00:00Z"), r.getValue("w").start)
        assertEquals("Europe/Paris", r.getValue("w").timeZone)
        assertEquals(ms("2026-11-13T00:00:00Z"), r.getValue("l").start)
    }

    @Test
    fun changedAndCancelledOccurrencesGoWithTheirSeries() {
        val r = parse(
            "BEGIN:VEVENT", "UID:s", "DTSTART:20261102T100000Z", "DURATION:PT1H", "RRULE:FREQ=DAILY;COUNT=10", "SUMMARY:Standup", "END:VEVENT",
            "BEGIN:VEVENT", "UID:s", "RECURRENCE-ID:20261104T100000Z", "DTSTART:20261104T120000Z", "DTEND:20261104T130000Z", "SUMMARY:Standup (moved)", "END:VEVENT",
            "BEGIN:VEVENT", "UID:s", "RECURRENCE-ID:20261105T100000Z", "DTSTART:20261105T100000Z", "STATUS:CANCELLED", "END:VEVENT",
            "BEGIN:VEVENT", "UID:orphan", "RECURRENCE-ID:20261106T100000Z", "DTSTART:20261106T150000Z", "SUMMARY:Alone", "END:VEVENT",
        )
        assertEquals(2, r.events.size)
        val series = r.events.first { it.uid == "s" }
        assertEquals(listOf(ms("2026-11-05T10:00:00Z")), series.exdates)
        val moved = series.overrides.single()
        assertEquals(ms("2026-11-04T10:00:00Z"), moved.recurrenceId)
        assertEquals(ms("2026-11-04T12:00:00Z"), moved.start)
        assertEquals("Standup (moved)", moved.title)
        val alone = r.events.first { it.title == "Alone" }
        assertNull(alone.recurrenceId)
        assertTrue(alone.uid.startsWith("orphan-"))
    }

    @Test
    fun cancelledAndBrokenEventsAreSkipped() {
        val r = parse(
            "BEGIN:VEVENT", "UID:c", "DTSTART:20261113T090000Z", "STATUS:CANCELLED", "END:VEVENT",
            "BEGIN:VEVENT", "UID:n", "SUMMARY:No start", "END:VEVENT",
            "BEGIN:VEVENT", "UID:b", "DTSTART:2026-11-13", "END:VEVENT",
            "BEGIN:VTODO", "UID:t", "DTSTART:20261113T090000Z", "END:VTODO",
            "BEGIN:VEVENT", "UID:ok", "DTSTART:20261113T090000Z", "END:VEVENT",
        )
        assertEquals(listOf("ok"), r.events.map { it.uid })
        assertEquals(3, r.skipped)
    }

    @Test
    fun missingUidGetsAStableOne() {
        val a = parse("BEGIN:VEVENT", "DTSTART:20261113T090000Z", "SUMMARY:x", "END:VEVENT").events.single()
        val b = parse("BEGIN:VEVENT", "DTSTART:20261113T090000Z", "SUMMARY:x", "END:VEVENT").events.single()
        assertTrue(a.uid.startsWith("datectrl-"))
        assertEquals(a.uid, b.uid)
    }

    @Test
    fun sameUidTwice() {
        val r = parse(
            "BEGIN:VEVENT", "UID:dup", "DTSTART:20261113T090000Z", "END:VEVENT",
            "BEGIN:VEVENT", "UID:dup", "DTSTART:20261113T090000Z", "END:VEVENT",
            "BEGIN:VEVENT", "UID:dup", "DTSTART:20261114T090000Z", "END:VEVENT",
        )
        assertEquals(2, r.events.size)
        assertEquals(1, r.skipped)
    }

    @Test
    fun textIsCapped() {
        val e = parse("BEGIN:VEVENT", "UID:l", "DTSTART:20261113T090000Z", "SUMMARY:" + "a".repeat(5_000), "END:VEVENT").events.single()
        assertEquals(IcsLimits.MAX_TITLE, e.title.length)
    }

    @Test
    fun refusesWhatIsntICalendar() {
        for (text in listOf(
            "<?xml version=\"1.0\"?>\n<!DOCTYPE x [<!ENTITY a \"b\">]>\n<icalendar/>",
            "<!DOCTYPE html><html><body>BEGIN:VCALENDAR</body></html>",
            "BEGIN:VCARD\nFN:x\nEND:VCARD",
            "",
        )) {
            try {
                IcsParser.parse(text, brussels)
                fail("Should refuse: $text")
            } catch (e: IcsException) {
                assertEquals(IcsException.Reason.NOT_ICALENDAR, e.reason)
            }
        }
    }

    @Test
    fun refusesFilesThatAreTooBig() {
        val chunk = "X-PAD:" + "x".repeat(1000) + "\r\n"
        val stream = object : InputStream() {
            val head = "BEGIN:VCALENDAR\r\n".toByteArray()
            val body = chunk.toByteArray()
            var pos = 0L
            override fun read(): Int {
                val p = pos++
                return if (p < head.size) head[p.toInt()].toInt() else body[((p - head.size) % body.size).toInt()].toInt()
            }
        }
        try {
            IcsParser.parse(stream, brussels)
            fail("Should refuse an endless file")
        } catch (e: IcsException) {
            assertEquals(IcsException.Reason.TOO_BIG, e.reason)
        }
    }

    @Test
    fun eventCountIsCapped() {
        val body = (0 until IcsLimits.MAX_EVENTS + 10).flatMap { listOf("BEGIN:VEVENT", "UID:e$it", "DTSTART:20261113T090000Z", "END:VEVENT") }
        val r = IcsParser.parse(cal(*body.toTypedArray()), brussels)
        assertEquals(IcsLimits.MAX_EVENTS, r.events.size)
        assertTrue(r.truncated)
    }

    @Test
    fun deepNestingIsSkippedOrRefused() {
        val nested = (0 until 50).map { "BEGIN:X-N$it" } + (49 downTo 0).map { "END:X-N$it" }
        val r = parse(*(nested + listOf("BEGIN:VEVENT", "UID:ok", "DTSTART:20261113T090000Z", "END:VEVENT")).toTypedArray())
        assertEquals(1, r.events.size)
        val bomb = (0 until 5_000).map { "BEGIN:X" }
        try {
            parse(*bomb.toTypedArray())
            fail("Should refuse")
        } catch (e: IcsException) {
            assertEquals(IcsException.Reason.NOT_ICALENDAR, e.reason)
        }
    }

    @Test
    fun unfoldingIsLinear() {
        // One property folded over 300,000 lines: quadratic unfolding would take minutes.
        val sb = StringBuilder("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:big\r\nDTSTART:20261113T090000Z\r\nDESCRIPTION:a")
        repeat(300_000) { sb.append("\r\n b") }
        sb.append("\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n")
        val t0 = System.nanoTime()
        val r = IcsParser.parse(sb.toString(), brussels)
        val seconds = (System.nanoTime() - t0) / 1e9
        assertTrue("took $seconds s", seconds < 5)
        // Longer than one property may be: dropped, the event itself still reads.
        assertEquals("", r.events.single().description)
    }

    @Test
    fun streamReadingHandlesUtf8() {
        val text = cal("BEGIN:VEVENT", "UID:u", "DTSTART:20261113T090000Z", "SUMMARY:Café 🎉", "END:VEVENT")
        val e = IcsParser.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)), brussels).events.single()
        assertEquals("Café 🎉", e.title)
    }

    @Test
    fun androidExdates() {
        assertEquals(
            listOf(ms("2026-11-05T10:00:00Z"), ms("2026-11-12T10:00:00Z")),
            IcsParser.parseAndroidExdates("20261112T100000Z,20261105T100000Z", brussels),
        )
        assertEquals(listOf(ms("2026-11-05T10:00:00Z")), IcsParser.parseAndroidExdates("Europe/Brussels;20261105T110000", brussels))
        assertEquals(listOf(utcDay(2026, 11, 5)), IcsParser.parseAndroidExdates("20261105", brussels))
        assertEquals(emptyList<Long>(), IcsParser.parseAndroidExdates(null, brussels))
    }

    @Test
    fun durations() {
        assertEquals(5_400_000L, IcsDuration.parse("PT1H30M"))
        assertEquals(-900_000L, IcsDuration.parse("-PT15M"))
        assertEquals(1_209_600_000L, IcsDuration.parse("P2W"))
        assertEquals(3_600_000L, IcsDuration.parse("P3600S"))
        assertEquals(90_000_000L, IcsDuration.parse("P1DT1H"))
        assertNull(IcsDuration.parse("P"))
        assertNull(IcsDuration.parse("1H"))
        assertEquals("P2D", IcsDuration.format(172_800_000L))
        assertEquals("PT1H30M", IcsDuration.format(5_400_000L))
        assertEquals("P1DT1H", IcsDuration.format(90_000_000L))
        assertEquals("PT0S", IcsDuration.format(0))
    }
}
