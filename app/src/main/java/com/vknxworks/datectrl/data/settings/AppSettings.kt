package com.vknxworks.datectrl.data.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vknxworks.datectrl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "datectrl")

/** The first day of the week in the month view. */
enum class WeekStart(@StringRes val label: Int) {
    PHONE(R.string.week_phone),
    MONDAY(R.string.week_monday),
    SATURDAY(R.string.week_saturday),
    SUNDAY(R.string.week_sunday),
}

/** How long DateCtrl can be in the background before the app lock asks again (-1: only after a restart). */
enum class LockTimeout(@StringRes val label: Int, val millis: Long) {
    IMMEDIATELY(R.string.lock_again_immediately, 0L),
    MINUTE_1(R.string.lock_again_1m, 60_000L),
    MINUTES_5(R.string.lock_again_5m, 5 * 60_000L),
    MINUTES_15(R.string.lock_again_15m, 15 * 60_000L),
    HOUR_1(R.string.lock_again_1h, 60 * 60_000L),
    RESTART(R.string.lock_again_restart, -1L),
}

data class AppSettings(
    val weekStart: WeekStart = WeekStart.PHONE,
    /** Reminder for new events, in minutes before (-1: none). */
    val defaultReminder: Int = 10,
    /** Length of new events, in minutes. */
    val defaultDuration: Int = 60,
    /** Calendar for new events (-1: the first one that can be written). */
    val defaultCalendar: Long = -1L,
    /** DateCtrl shows event reminders as notifications. */
    val reminders: Boolean = true,
    /** Week numbers next to the month view. */
    val weekNumbers: Boolean = false,
    /** App lock: when it asks again after DateCtrl was in the background. */
    val lockTimeout: LockTimeout = LockTimeout.IMMEDIATELY,
    /** "Hide in screenshots": FLAG_SECURE on the app's windows (screenshots, recordings and the Recents preview). */
    val hideScreenshots: Boolean = false,
    /** "Hide in recent apps": the Recents view shows a blank card. Screenshots still work. */
    val hideRecents: Boolean = false,
)

class SettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val weekStart = stringPreferencesKey("week_start")
        val defaultReminder = intPreferencesKey("default_reminder")
        val defaultDuration = intPreferencesKey("default_duration")
        val defaultCalendar = longPreferencesKey("default_calendar")
        val reminders = booleanPreferencesKey("reminders")
        val weekNumbers = booleanPreferencesKey("week_numbers")
        val lockTimeout = stringPreferencesKey("lock_timeout")
        val hideScreenshots = booleanPreferencesKey("hide_screenshots")
        val hideRecents = booleanPreferencesKey("hide_recents")
    }

    private val prefs: Flow<Preferences> = context.appDataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settings: StateFlow<AppSettings> = prefs
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private fun Preferences.toSettings() = AppSettings(
        weekStart = enumOr(this[K.weekStart], WeekStart.PHONE),
        defaultReminder = this[K.defaultReminder] ?: 10,
        defaultDuration = this[K.defaultDuration] ?: 60,
        defaultCalendar = this[K.defaultCalendar] ?: -1L,
        reminders = this[K.reminders] ?: true,
        weekNumbers = this[K.weekNumbers] ?: false,
        lockTimeout = enumOr(this[K.lockTimeout], LockTimeout.IMMEDIATELY),
        hideScreenshots = this[K.hideScreenshots] ?: false,
        hideRecents = this[K.hideRecents] ?: false,
    )

    /** Saves a change. If the phone's storage fails, the change is dropped instead of closing the app. */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        try {
            context.appDataStore.edit { p ->
                val s = transform(p.toSettings())
                p[K.weekStart] = s.weekStart.name
                p[K.defaultReminder] = s.defaultReminder
                p[K.defaultDuration] = s.defaultDuration
                p[K.defaultCalendar] = s.defaultCalendar
                p[K.reminders] = s.reminders
                p[K.weekNumbers] = s.weekNumbers
                p[K.lockTimeout] = s.lockTimeout.name
                p[K.hideScreenshots] = s.hideScreenshots
                p[K.hideRecents] = s.hideRecents
            }
        } catch (e: IOException) {
            // Nothing to do: the settings stay as they were.
        }
    }
}

private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
