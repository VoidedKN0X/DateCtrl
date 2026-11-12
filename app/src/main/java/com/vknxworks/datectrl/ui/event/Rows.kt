package com.vknxworks.datectrl.ui.event

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.datectrl.Launch
import com.vknxworks.datectrl.R
import com.vknxworks.datectrl.data.calendar.CalendarInfo
import com.vknxworks.datectrl.data.calendar.EventDraft
import com.vknxworks.datectrl.data.calendar.Instance
import com.vknxworks.datectrl.data.settings.AppSettings
import com.vknxworks.datectrl.ui.common.asColor
import com.vknxworks.datectrl.ui.common.dayTimes
import com.vknxworks.datectrl.ui.theme.CtrlColors
import com.vknxworks.datectrl.ui.theme.CtrlShapes
import com.vknxworks.datectrl.ui.theme.CtrlType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** One event on a day: colour bar, times, title and place. */
@Composable
fun InstanceRow(e: Instance, day: LocalDate, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).clip(CtrlShapes.Card).background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.Outline, CtrlShapes.Card).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(40.dp).clip(CtrlShapes.Dot).background(e.color.asColor().copy(alpha = if (e.declined) 0.4f else 1f)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                e.title.ifBlank { stringResource(R.string.no_title) },
                style = MaterialTheme.typography.titleMedium,
                color = if (e.declined) CtrlColors.Muted else CtrlColors.Text,
                textDecoration = if (e.declined) TextDecoration.LineThrough else null,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val sub = listOf(dayTimes(context, e.begin, e.end, e.allDay, day), e.location).filter { it.isNotBlank() }.joinToString(" · ")
            Text(sub, style = CtrlType.Data, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The calendar new events go to: the one picked in Settings, or the first that can be written (visible first). */
fun defaultCalendar(settings: AppSettings, calendars: List<CalendarInfo>): CalendarInfo? {
    val writable = calendars.filter { it.writable }
    return writable.firstOrNull { it.id == settings.defaultCalendar }
        ?: writable.firstOrNull { it.visible && !it.local }
        ?: writable.firstOrNull { it.visible }
        ?: writable.firstOrNull()
}

/** A new event on [day]: the next full hour today, 9:00 on other days, with the default length and reminder. */
fun newDraft(settings: AppSettings, calendars: List<CalendarInfo>, day: LocalDate): EventDraft {
    val zone = ZoneId.systemDefault()
    val now = java.time.ZonedDateTime.now(zone)
    val start = if (day == now.toLocalDate()) now.plusHours(1).withMinute(0).withSecond(0).withNano(0).let {
        // Late in the evening the next hour is tomorrow: stay on the day that was picked.
        if (it.toLocalDate() != day) day.atTime(23, 0).atZone(zone) else it
    } else day.atTime(9, 0).atZone(zone)
    val startMs = start.toInstant().toEpochMilli()
    return EventDraft(
        calendarId = defaultCalendar(settings, calendars)?.id ?: -1L,
        start = startMs,
        end = startMs + settings.defaultDuration * 60_000L,
        startDate = day,
        endDate = day,
        reminders = if (settings.defaultReminder >= 0) listOf(settings.defaultReminder) else emptyList(),
    )
}

/** "Add to calendar" from another app, with what it filled in (and the defaults for the rest). */
fun draftFrom(context: Context, p: Launch.Insert): EventDraft {
    val c = context.applicationContext as com.vknxworks.datectrl.DateCtrlApp
    val settings = c.container.settings.settings.value
    val calendars = c.container.calendar.calendars.value
    val zone = ZoneId.systemDefault()
    val base = newDraft(settings, calendars, p.begin?.let { Instant.ofEpochMilli(it).atZone(if (p.allDay) ZoneOffset.UTC else zone).toLocalDate() } ?: LocalDate.now())
    val begin = p.begin ?: base.start
    val end = p.end?.takeIf { it >= begin } ?: (begin + settings.defaultDuration * 60_000L)
    val allDayStart = Instant.ofEpochMilli(begin).atZone(if (p.allDay) ZoneOffset.UTC else zone).toLocalDate()
    // All-day events from other apps end at the next midnight (exclusive): the last day is the one before.
    val allDayEnd = if (p.end != null && p.allDay) Instant.ofEpochMilli(end - 1).atZone(ZoneOffset.UTC).toLocalDate().let { if (it.isBefore(allDayStart)) allDayStart else it } else allDayStart
    val writable = calendars.filter { it.writable }
    return base.copy(
        calendarId = p.calendarId?.takeIf { id -> writable.any { it.id == id } } ?: base.calendarId,
        title = p.title.orEmpty(),
        location = p.location.orEmpty(),
        description = p.description.orEmpty(),
        allDay = p.allDay,
        start = if (p.allDay) allDayStart.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli() else begin,
        end = if (p.allDay) allDayStart.atTime(LocalTime.of(10, 0)).atZone(zone).toInstant().toEpochMilli() else end,
        startDate = allDayStart,
        endDate = allDayEnd,
        rrule = p.rrule?.takeIf { it.isNotBlank() },
    )
}
