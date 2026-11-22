package com.vknxworks.datectrl.reminders

import android.provider.CalendarContract.CalendarAlerts
import com.vknxworks.datectrl.reminders.SnoozePlan.Entry
import com.vknxworks.datectrl.reminders.SnoozePlan.Rearm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoozePlanTest {
    private val now = 1_800_000_000_000L
    private val entry = Entry(eventId = 42, begin = now - 60_000, end = now + 3_600_000, allDay = false, at = now + 600_000, stored = true)

    @Test
    fun exactAlarmOnlyWhenAndroidAllowsIt() {
        assertEquals(SnoozePlan.Alarm.EXACT, SnoozePlan.alarm(canScheduleExact = true))
        assertEquals(SnoozePlan.Alarm.INEXACT, SnoozePlan.alarm(canScheduleExact = false))
    }

    @Test
    fun entryRoundTripsAndHoldsNoText() {
        val value = SnoozePlan.encode(entry)
        assertEquals("${entry.end},0,${entry.at},1", value)
        assertEquals(entry, SnoozePlan.decode(entry.key, value))
        val allDay = entry.copy(allDay = true, stored = false)
        assertEquals(allDay, SnoozePlan.decode(allDay.key, SnoozePlan.encode(allDay)))
    }

    @Test
    fun malformedEntriesAreRejected() {
        assertNull(SnoozePlan.decode("42:1", null))
        assertNull(SnoozePlan.decode("42", "1,0,2,1"))
        assertNull(SnoozePlan.decode("x:1", "1,0,2,1"))
        assertNull(SnoozePlan.decode("0:1", "1,0,2,1"))
        assertNull(SnoozePlan.decode("42:1", "1,0,2"))
        assertNull(SnoozePlan.decode("42:1", "1,2,2,1"))
        assertNull(SnoozePlan.decode("42:1", "1,0,0,1"))
        assertNull(SnoozePlan.decode("42:1", "1,0,2,yes"))
    }

    @Test
    fun futureSnoozeIsArmedAgain() {
        assertEquals(Rearm.ARM, SnoozePlan.rearm(entry, now, CalendarAlerts.STATE_SCHEDULED, canWrite = true))
    }

    @Test
    fun rowRemovedAtStartIsWrittenBackWhenAllowed() {
        assertEquals(Rearm.ARM_AND_RESTORE_ROW, SnoozePlan.rearm(entry, now, rowState = null, canWrite = true))
        assertEquals(Rearm.ARM, SnoozePlan.rearm(entry, now, rowState = null, canWrite = false))
        // Never had a row (no write access when snoozed): don't add one later.
        assertEquals(Rearm.ARM, SnoozePlan.rearm(entry.copy(stored = false), now, rowState = null, canWrite = true))
    }

    @Test
    fun snoozeHandledElsewhereIsForgotten() {
        assertEquals(Rearm.FORGET, SnoozePlan.rearm(entry, now, CalendarAlerts.STATE_FIRED, canWrite = true))
        assertEquals(Rearm.FORGET, SnoozePlan.rearm(entry, now, CalendarAlerts.STATE_DISMISSED, canWrite = true))
    }

    @Test
    fun dueOrMissedSnoozeIsDeliveredButAWeekOldOneIsForgotten() {
        assertEquals(Rearm.DELIVER, SnoozePlan.rearm(entry.copy(at = now), now, null, canWrite = true))
        assertEquals(Rearm.DELIVER, SnoozePlan.rearm(entry.copy(at = now - 3_600_000), now, null, canWrite = true))
        assertEquals(Rearm.FORGET, SnoozePlan.rearm(entry.copy(at = now - SnoozePlan.MAX_AGE_MS - 1), now, null, canWrite = true))
    }

    @Test
    fun dueScheduledRowIsShownAndMarked() {
        val d = SnoozePlan.due(CalendarAlerts.STATE_SCHEDULED, eventExists = true, end = now + 1, now = now, enabled = true)
        assertTrue(d.show)
        assertTrue(d.markFired)
    }

    @Test
    fun dueAlreadyShownByTheCalendarBroadcastIsNotShownTwice() {
        val d = SnoozePlan.due(CalendarAlerts.STATE_FIRED, eventExists = true, end = now + 1, now = now, enabled = true)
        assertFalse(d.show)
        assertFalse(d.markFired)
    }

    @Test
    fun dueWithoutRowStillShowsWhileTheEventExists() {
        assertTrue(SnoozePlan.due(null, eventExists = true, end = now + 1, now = now, enabled = true).show)
        assertFalse(SnoozePlan.due(null, eventExists = false, end = now + 1, now = now, enabled = true).show)
    }

    @Test
    fun dueRespectsSettingAndOldEvents() {
        val off = SnoozePlan.due(CalendarAlerts.STATE_SCHEDULED, eventExists = true, end = now + 1, now = now, enabled = false)
        assertFalse(off.show)
        assertTrue(off.markFired)
        assertFalse(SnoozePlan.due(null, true, end = now - SnoozePlan.LATE_MS - 1, now = now, enabled = true).show)
        assertTrue(SnoozePlan.due(null, true, end = now - SnoozePlan.LATE_MS + 1, now = now, enabled = true).show)
    }

    @Test
    fun overflowDropsTheEarliestSnoozes() {
        val list = (1..5).map { entry.copy(eventId = it.toLong(), at = now + it) }
        assertTrue(SnoozePlan.overflow(list, max = 5).isEmpty())
        assertEquals(listOf(1L, 2L), SnoozePlan.overflow(list, max = 3).map { it.eventId })
    }
}
