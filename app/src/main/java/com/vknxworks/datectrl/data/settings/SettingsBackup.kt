package com.vknxworks.datectrl.data.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A DateCtrl settings file: the month view, new-event defaults and reminder settings. Never the app lock, its PIN
 * or "Lock again", and never the default calendar (calendar ids only mean something on one phone). No events:
 * those are exported as .ics files. Every field is optional, so files from older or newer versions restore what
 * they can.
 */
@Serializable
data class SettingsBackup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val appVersion: String? = null,
    val createdAt: String? = null,
    val weekStart: String? = null,
    val weekNumbers: Boolean? = null,
    val defaultReminder: Int? = null,
    val defaultDuration: Int? = null,
    val reminders: Boolean? = null,
    /** Restoring can only turn these on, never off. */
    val hideScreenshots: Boolean? = null,
    val hideRecents: Boolean? = null,
) {
    companion object {
        const val FORMAT = "datectrl-settings"
        const val VERSION = 1
    }
}

class BackupFormatException(val newer: Boolean) : Exception()

object SettingsBackups {
    const val MAX_BYTES = 256 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun from(s: AppSettings, appVersion: String, now: String) = SettingsBackup(
        appVersion = appVersion,
        createdAt = now,
        weekStart = s.weekStart.name,
        weekNumbers = s.weekNumbers,
        defaultReminder = s.defaultReminder,
        defaultDuration = s.defaultDuration,
        reminders = s.reminders,
        hideScreenshots = s.hideScreenshots,
        hideRecents = s.hideRecents,
    )

    fun export(b: SettingsBackup): String = json.encodeToString(SettingsBackup.serializer(), b)

    fun parse(text: String): SettingsBackup {
        val b = runCatching { json.decodeFromString(SettingsBackup.serializer(), text) }.getOrNull()
            ?: throw BackupFormatException(newer = false)
        return check(b)
    }

    fun check(b: SettingsBackup): SettingsBackup {
        if (b.format != SettingsBackup.FORMAT) throw BackupFormatException(newer = false)
        if (b.version > SettingsBackup.VERSION) throw BackupFormatException(newer = true)
        return b
    }

    /** [b] applied to [cur]; anything missing or out of range stays as it is, the hide switches only turn on. */
    fun apply(b: SettingsBackup, cur: AppSettings): AppSettings = cur.copy(
        weekStart = b.weekStart?.let { n -> WeekStart.entries.firstOrNull { it.name == n } } ?: cur.weekStart,
        weekNumbers = b.weekNumbers ?: cur.weekNumbers,
        // -1 is "no reminder"; four weeks is the longest the editor sets.
        defaultReminder = b.defaultReminder?.takeIf { it in -1..(4 * 7 * 1440) } ?: cur.defaultReminder,
        defaultDuration = b.defaultDuration?.takeIf { it in 5..(24 * 60) } ?: cur.defaultDuration,
        reminders = b.reminders ?: cur.reminders,
        hideScreenshots = cur.hideScreenshots || b.hideScreenshots == true,
        hideRecents = cur.hideRecents || b.hideRecents == true,
    )
}
