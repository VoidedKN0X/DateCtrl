package com.vknxworks.datectrl.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RepeatRuleTest {
    private val brussels = ZoneId.of("Europe/Brussels")
    private val start = LocalDate.of(2026, 11, 23) // a Monday

    @Test fun buildsWhatItReads() {
        val cases = listOf(
            RepeatRule(Freq.WEEKLY, 2) to "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO",
            RepeatRule(Freq.DAILY, count = 10) to "FREQ=DAILY;COUNT=10",
            RepeatRule(Freq.MONTHLY, 3, until = LocalDate.of(2027, 6, 30)) to "FREQ=MONTHLY;INTERVAL=3;BYMONTHDAY=23;UNTIL=20270630T215959Z",
            RepeatRule(Freq.YEARLY) to "FREQ=YEARLY",
        )
        for ((rule, text) in cases) {
            assertEquals(text, RepeatRules.build(rule, start, allDay = false, zone = brussels))
            assertEquals(rule, RepeatRules.parse(text, brussels))
        }
    }

    @Test fun allDayUntilIsADate() {
        val r = RepeatRule(Freq.WEEKLY, until = LocalDate.of(2026, 12, 31))
        val text = RepeatRules.build(r, start, allDay = true, zone = brussels)
        assertEquals("FREQ=WEEKLY;BYDAY=MO;UNTIL=20261231", text)
        assertEquals(r, RepeatRules.parse(text, brussels))
    }

    @Test fun winterAndSummerTimeUntil() {
        // 23:59:59 in Brussels in July is 21:59:59 UTC.
        val text = RepeatRules.build(RepeatRule(Freq.DAILY, until = LocalDate.of(2027, 7, 1)), start, false, brussels)
        assertEquals("FREQ=DAILY;UNTIL=20270701T215959Z", text)
        assertEquals(LocalDate.of(2027, 7, 1), RepeatRules.parse(text, brussels)!!.until)
    }

    @Test fun keepsWhatItCantShow() {
        assertNull(RepeatRules.parse("FREQ=WEEKLY;BYDAY=MO,WE,FR", brussels))
        assertNull(RepeatRules.parse("FREQ=MONTHLY;BYDAY=2TU", brussels))
        assertNull(RepeatRules.parse("FREQ=HOURLY", brussels))
        assertNull(RepeatRules.parse("FREQ=DAILY;COUNT=3;UNTIL=20270101", brussels))
        assertNull(RepeatRules.parse(null, brussels))
        assertEquals(RepeatRule(Freq.WEEKLY), RepeatRules.parse("FREQ=WEEKLY;WKST=SU;BYDAY=MO", brussels))
    }
}
