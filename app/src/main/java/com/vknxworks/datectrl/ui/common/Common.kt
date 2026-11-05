package com.vknxworks.datectrl.ui.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.vknxworks.datectrl.R
import com.vknxworks.datectrl.data.settings.WeekStart
import com.vknxworks.datectrl.ui.components.MessageView
import com.vknxworks.datectrl.ui.lock.AppLock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

fun Context.hasPermission(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

/**
 * Shows [content] once the calendar permissions are granted; until then, a screen that explains why and asks.
 * After Android stops showing the question (denied twice), the button opens the app's settings instead.
 */
@Composable
fun CalendarPermissionGate(onGranted: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val perms = arrayOf(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR)
    var granted by remember { mutableStateOf(perms.all { context.hasPermission(it) }) }
    var asked by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked++
        granted = perms.all { context.hasPermission(it) }
        if (granted) onGranted()
    }
    // Notifications are asked for together with the calendar, for reminders; refusing them changes nothing else.
    val all = perms + android.Manifest.permission.POST_NOTIFICATIONS
    if (granted) {
        content()
        return
    }
    val activity = context as? Activity
    val blocked = asked > 0 && activity != null && perms.none { activity.shouldShowRequestPermissionRationale(it) }
    MessageView(
        icon = Icons.Outlined.CalendarMonth,
        title = stringResource(R.string.perm_title),
        body = stringResource(if (blocked) R.string.perm_body_settings else R.string.perm_body),
        actionLabel = stringResource(if (blocked) R.string.perm_open_settings else R.string.perm_allow),
        onAction = {
            if (blocked) {
                openAppSettings(context)
            } else {
                AppLock.expectReturn()
                launcher.launch(all)
            }
        },
    )
}

fun openAppSettings(context: Context) {
    AppLock.expectReturn()
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Starts another app; that trip doesn't lock DateCtrl. False when no app can do it. */
fun Context.launch(intent: Intent): Boolean = try {
    AppLock.expectReturn()
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

// ---------- Dates and times ----------

fun firstDay(start: WeekStart): DayOfWeek = when (start) {
    WeekStart.PHONE -> WeekFields.of(Locale.getDefault()).firstDayOfWeek
    WeekStart.MONDAY -> DayOfWeek.MONDAY
    WeekStart.SATURDAY -> DayOfWeek.SATURDAY
    WeekStart.SUNDAY -> DayOfWeek.SUNDAY
}

fun weekDays(first: DayOfWeek): List<DayOfWeek> = (0 until 7).map { first.plus(it.toLong()) }

fun dayNarrow(d: DayOfWeek): String = d.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.getDefault()).ifBlank { d.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1) }

fun dayShort(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT, Locale.getDefault())

/** ISO week number, as most calendars show it. */
fun weekNumber(d: LocalDate): Int = d.get(WeekFields.ISO.weekOfWeekBasedYear())

fun monthName(year: Int, month: Int): String =
    java.time.Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** "14:05" or "2:05 PM", following the phone's 24-hour setting. */
fun timeText(context: Context, millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val t = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return t.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

/** "Thursday 5 November 2026" in the phone's language. */
fun dateLong(d: LocalDate): String = d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()))

/** "5 Nov 2026". */
fun dateMedium(d: LocalDate): String = d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))

/** "Today", "Tomorrow", "Yesterday" or the date. */
fun relativeDay(context: Context, d: LocalDate, long: Boolean = false): String {
    val today = LocalDate.now()
    return when (d) {
        today -> context.getString(R.string.today)
        today.plusDays(1) -> context.getString(R.string.tomorrow)
        today.minusDays(1) -> context.getString(R.string.yesterday)
        else -> if (long) dateLong(d) else d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "EEE d MMM" else "EEE d MMM yyyy", Locale.getDefault()))
    }
}

/** When an occurrence happens, in one line: "Today 14:00 – 15:00", "Mon 9 Nov – Wed 11 Nov", "Tomorrow · All day". */
fun timeRange(context: Context, begin: Long, end: Long, allDay: Boolean): String {
    val zone = ZoneId.systemDefault()
    if (allDay) {
        val first = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
        val last = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1).let { if (it.isBefore(first)) first else it }
        return if (first == last) "${relativeDay(context, first)} · ${context.getString(R.string.all_day)}"
        else "${relativeDay(context, first)} – ${relativeDay(context, last)}"
    }
    val a = Instant.ofEpochMilli(begin).atZone(zone)
    val b = Instant.ofEpochMilli(end.coerceAtLeast(begin)).atZone(zone)
    return if (a.toLocalDate() == b.toLocalDate() || end - begin < 60_000L) {
        "${relativeDay(context, a.toLocalDate())} ${timeText(context, begin)} – ${timeText(context, end)}".let { if (end <= begin) "${relativeDay(context, a.toLocalDate())} ${timeText(context, begin)}" else it }
    } else {
        "${relativeDay(context, a.toLocalDate())} ${timeText(context, begin)} – ${relativeDay(context, b.toLocalDate())} ${timeText(context, end)}"
    }
}

/** "09:00 – 10:00" for the day list, or "All day"; a multi-day event shows where it starts or ends. */
fun dayTimes(context: Context, begin: Long, end: Long, allDay: Boolean, day: LocalDate): String {
    if (allDay) return context.getString(R.string.all_day)
    val zone = ZoneId.systemDefault()
    val a = Instant.ofEpochMilli(begin).atZone(zone)
    val b = Instant.ofEpochMilli(end).atZone(zone)
    val startsToday = a.toLocalDate() == day
    val endsToday = b.toLocalDate() == day || (b.toLocalDate() == day.plusDays(1) && b.toLocalTime() == java.time.LocalTime.MIDNIGHT)
    return when {
        startsToday && endsToday -> if (end <= begin) timeText(context, begin) else "${timeText(context, begin)} – ${timeText(context, end)}"
        startsToday -> "${timeText(context, begin)} →"
        endsToday -> "→ ${timeText(context, end)}"
        else -> context.getString(R.string.all_day)
    }
}

/** "At the time of the event", "10 minutes before", "1 day before". */
fun reminderText(context: Context, minutes: Int): String = when {
    minutes <= 0 -> context.getString(R.string.reminder_at_time)
    minutes % (7 * 1440) == 0 -> context.resources.getQuantityString(R.plurals.weeks_before, minutes / (7 * 1440), minutes / (7 * 1440))
    minutes % 1440 == 0 -> context.resources.getQuantityString(R.plurals.days_before, minutes / 1440, minutes / 1440)
    minutes % 60 == 0 -> context.resources.getQuantityString(R.plurals.hours_before, minutes / 60, minutes / 60)
    else -> context.resources.getQuantityString(R.plurals.minutes_before, minutes, minutes)
}

/** The reminders the editor offers. */
val REMINDER_CHOICES = listOf(0, 5, 10, 15, 30, 60, 120, 1440, 2880, 10080)

/** Colours for new calendars (the Ctrl accent first). */
val CALENDAR_COLORS = listOf(
    0xFFB48CFF.toInt(), 0xFF4F8EF7.toInt(), 0xFF2BB673.toInt(), 0xFFF2B63D.toInt(),
    0xFFF2704D.toInt(), 0xFFE0558F.toInt(), 0xFF35B8C4.toInt(), 0xFF8A8F98.toInt(),
)

fun Int.asColor(): Color = Color(this or 0xFF000000.toInt())
