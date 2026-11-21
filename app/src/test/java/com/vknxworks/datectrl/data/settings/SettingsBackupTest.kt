package com.vknxworks.datectrl.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SettingsBackupTest {
    @Test fun appliesValidValuesOnly() {
        val cur = AppSettings(defaultCalendar = 7L, hideRecents = true, lockTimeout = LockTimeout.MINUTES_5)
        val s = SettingsBackups.apply(
            SettingsBackup(weekStart = "SUNDAY", weekNumbers = true, defaultReminder = -1, defaultDuration = 2, hideRecents = false, hideScreenshots = true),
            cur,
        )
        assertEquals(WeekStart.SUNDAY, s.weekStart)
        assertTrue(s.weekNumbers)
        assertEquals(-1, s.defaultReminder)
        // 2 minutes is too short: kept.
        assertEquals(cur.defaultDuration, s.defaultDuration)
        assertTrue(s.hideRecents)
        assertTrue(s.hideScreenshots)
        assertEquals(7L, s.defaultCalendar)
        assertEquals(LockTimeout.MINUTES_5, s.lockTimeout)
    }

    @Test fun checksFormatAndVersion() {
        SettingsBackups.check(SettingsBackup())
        try {
            SettingsBackups.check(SettingsBackup(format = "sumctrl-settings"))
            fail()
        } catch (e: BackupFormatException) {
            assertEquals(false, e.newer)
        }
        try {
            SettingsBackups.check(SettingsBackup(version = 2))
            fail()
        } catch (e: BackupFormatException) {
            assertEquals(true, e.newer)
        }
    }
}
