package com.vknxworks.datectrl.ui.settings

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.datectrl.R
import com.vknxworks.datectrl.container
import com.vknxworks.datectrl.data.lock.LockVault
import com.vknxworks.datectrl.data.settings.AppSettings
import com.vknxworks.datectrl.data.settings.LockTimeout
import com.vknxworks.datectrl.data.settings.WeekStart
import com.vknxworks.datectrl.ui.common.launch
import com.vknxworks.datectrl.ui.common.reminderText
import com.vknxworks.datectrl.ui.components.Group
import com.vknxworks.datectrl.ui.components.GroupChoice
import com.vknxworks.datectrl.ui.components.GroupRow
import com.vknxworks.datectrl.ui.components.GroupSwitch
import com.vknxworks.datectrl.ui.components.ScreenTopBar
import com.vknxworks.datectrl.ui.event.defaultCalendar
import com.vknxworks.datectrl.ui.lock.AppLock
import com.vknxworks.datectrl.ui.lock.PinRequest
import com.vknxworks.datectrl.ui.lock.findActivity
import kotlinx.coroutines.launch

/** Settings, opened from the You page (tap the profile picture). DateCtrl follows the phone's dark theme. */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    fun set(transform: (AppSettings) -> AppSettings) {
        scope.launch { c.settings.update(transform) }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.settings))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(top = 4.dp, bottom = 24.dp)) {
            ViewGroup(settings) { set(it) }
            NewEventsGroup(settings) { set(it) }
            RemindersGroup(settings) { set(it) }
            PrivacyAndSecurityGroup(settings)
        }
    }
}

@Composable
private fun ViewGroup(settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    Group(stringResource(R.string.tab_month)) {
        GroupChoice(
            stringResource(R.string.week_starts),
            WeekStart.entries.map { it to stringResource(it.label) },
            settings.weekStart,
            icon = Icons.Outlined.CalendarViewWeek,
        ) { w -> set { it.copy(weekStart = w) } }
        GroupSwitch(stringResource(R.string.week_numbers), stringResource(R.string.week_numbers_sub), settings.weekNumbers, Icons.Outlined.Numbers) { on -> set { it.copy(weekNumbers = on) } }
    }
}

@Composable
private fun NewEventsGroup(settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val calendars by c.calendar.calendars.collectAsStateWithLifecycle()
    val writable = calendars.filter { it.writable }
    Group(stringResource(R.string.group_new_events)) {
        if (writable.isNotEmpty()) {
            GroupChoice(
                stringResource(R.string.default_calendar),
                writable.map { it.id to it.name },
                defaultCalendar(settings, calendars)?.id,
                icon = Icons.Outlined.Layers,
            ) { id -> set { it.copy(defaultCalendar = id) } }
        }
        GroupChoice(
            stringResource(R.string.default_length),
            listOf(15, 30, 45, 60, 90, 120).map { it to lengthText(it) },
            settings.defaultDuration,
            icon = Icons.Outlined.Timer,
            fallbackLabel = lengthText(settings.defaultDuration),
        ) { m -> set { it.copy(defaultDuration = m) } }
        GroupChoice(
            stringResource(R.string.default_reminder),
            listOf(-1 to stringResource(R.string.no_reminders)) + listOf(0, 5, 10, 15, 30, 60, 1440).map { it to reminderText(context, it) },
            settings.defaultReminder,
            icon = Icons.Outlined.NotificationsNone,
            fallbackLabel = reminderText(context, settings.defaultReminder),
        ) { m -> set { it.copy(defaultReminder = m) } }
    }
}

@Composable
private fun lengthText(minutes: Int): String =
    if (minutes % 60 == 0) pluralStringResource(R.plurals.hours, minutes / 60, minutes / 60) else pluralStringResource(R.plurals.minutes, minutes, minutes)

@Composable
private fun RemindersGroup(settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val context = LocalContext.current
    Group(stringResource(R.string.reminders), footer = stringResource(R.string.reminders_footer)) {
        GroupSwitch(stringResource(R.string.show_reminders), stringResource(R.string.show_reminders_sub), settings.reminders, Icons.Outlined.Notifications) { on -> set { it.copy(reminders = on) } }
        GroupRow(
            stringResource(R.string.reminder_sound),
            stringResource(R.string.reminder_sound_sub),
            icon = Icons.Outlined.Tune,
            showChevron = true,
            onClick = {
                com.vknxworks.datectrl.reminders.Reminders.createChannel(context)
                val ok = context.launch(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, com.vknxworks.datectrl.reminders.Reminders.CHANNEL)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                if (!ok) Toast.makeText(context, context.getString(R.string.no_app), Toast.LENGTH_SHORT).show()
            },
        )
    }
}

/** Privacy and security: the shared Ctrl series app lock, then hiding DateCtrl in screenshots. */
@Composable
private fun PrivacyAndSecurityGroup(settings: AppSettings) {
    val context = LocalContext.current
    val c = context.container
    val bioAvailable = remember { AppLock.biometricAvailable(context) }
    val lockOn = c.vault.enabled
    Group(stringResource(R.string.group_privacy), footer = stringResource(R.string.app_lock_footer)) {
        GroupSwitch(
            stringResource(R.string.app_lock),
            stringResource(R.string.app_lock_subtitle),
            checked = lockOn,
            icon = Icons.Outlined.Lock,
        ) { on -> AppLock.request = if (on) PinRequest.ENABLE else PinRequest.DISABLE }
        if (lockOn) {
            GroupRow(
                stringResource(R.string.lock_change_pin),
                stringResource(R.string.lock_change_pin_subtitle, LockVault.PIN_MIN),
                icon = Icons.Outlined.Password,
                showChevron = true,
                onClick = { AppLock.request = PinRequest.CHANGE },
            )
            GroupSwitch(
                stringResource(R.string.lock_fingerprint),
                stringResource(if (bioAvailable || c.vault.biometricEnabled) R.string.lock_fingerprint_subtitle else R.string.lock_fingerprint_unavailable),
                checked = c.vault.biometricEnabled,
                icon = Icons.Outlined.Fingerprint,
            ) { on ->
                if (!on) {
                    c.vault.disableBiometric()
                } else if (bioAvailable) {
                    val activity = context.findActivity()
                    val cipher = c.vault.biometricEnrolCipher()
                    if (activity == null || cipher == null) {
                        Toast.makeText(context, context.getString(R.string.lock_fingerprint_setup_failed), Toast.LENGTH_SHORT).show()
                    } else {
                        AppLock.biometricPrompt(
                            activity,
                            context.getString(R.string.lock_fingerprint_enable_title),
                            context.getString(R.string.lock_fingerprint_enable_subtitle),
                            cipher,
                        ) { unlocked ->
                            if (!c.vault.finishBiometricEnrol(unlocked)) {
                                Toast.makeText(context, context.getString(R.string.lock_fingerprint_setup_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            GroupChoice(
                stringResource(R.string.lock_again),
                LockTimeout.entries.map { it to stringResource(it.label) },
                settings.lockTimeout,
                icon = Icons.Outlined.Timer,
            ) { t -> c.appScope.launch { c.settings.update { it.copy(lockTimeout = t) } } }
        }
        GroupSwitch(
            stringResource(R.string.hide_screenshots),
            stringResource(R.string.hide_screenshots_subtitle),
            checked = settings.hideScreenshots,
            icon = Icons.Outlined.VisibilityOff,
        ) { on -> c.appScope.launch { c.settings.update { it.copy(hideScreenshots = on) } } }
        GroupSwitch(
            stringResource(R.string.hide_recents),
            stringResource(R.string.hide_recents_subtitle),
            checked = settings.hideRecents,
            icon = Icons.Outlined.Layers,
        ) { on -> c.appScope.launch { c.settings.update { it.copy(hideRecents = on) } } }
    }
}
