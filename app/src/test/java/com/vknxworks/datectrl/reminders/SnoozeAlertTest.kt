package com.vknxworks.datectrl.reminders

import android.provider.CalendarContract.CalendarAlerts
import org.junit.Assert.assertEquals
import org.junit.Test

class SnoozeAlertTest {
    @Test
    fun snoozeIsANewScheduledAlertAtTheSnoozeTime() {
        val fields = Reminders.snoozeAlert(eventId = 7, begin = 1_000, end = 2_000, at = 900_000, now = 300_000).toMap()
        assertEquals(7L, fields[CalendarAlerts.EVENT_ID])
        assertEquals(1_000L, fields[CalendarAlerts.BEGIN])
        assertEquals(2_000L, fields[CalendarAlerts.END])
        assertEquals(900_000L, fields[CalendarAlerts.ALARM_TIME])
        assertEquals(300_000L, fields[CalendarAlerts.CREATION_TIME])
        assertEquals(CalendarAlerts.STATE_SCHEDULED.toLong(), fields[CalendarAlerts.STATE])
        assertEquals(0L, fields[CalendarAlerts.NOTIFY_TIME])
        assertEquals(0L, fields[CalendarAlerts.RECEIVED_TIME])
    }
}
