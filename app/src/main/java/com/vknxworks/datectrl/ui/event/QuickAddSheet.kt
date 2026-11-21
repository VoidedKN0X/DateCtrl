package com.vknxworks.datectrl.ui.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.vknxworks.datectrl.R
import com.vknxworks.datectrl.container
import com.vknxworks.datectrl.data.calendar.EventDraft
import com.vknxworks.datectrl.data.calendar.QuickAdd
import com.vknxworks.datectrl.data.calendar.QuickEvent
import com.vknxworks.datectrl.data.settings.AppSettings
import com.vknxworks.datectrl.data.calendar.CalendarInfo
import com.vknxworks.datectrl.ui.common.relativeDay
import com.vknxworks.datectrl.ui.common.timeText
import com.vknxworks.datectrl.ui.components.CtrlBottomSheet
import com.vknxworks.datectrl.ui.components.CtrlTextField
import com.vknxworks.datectrl.ui.nav.EditRoute
import com.vknxworks.datectrl.ui.nav.LocalNavigator
import com.vknxworks.datectrl.ui.theme.CtrlColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Quick add: type an event the way you'd say it ("dentist friday 14:00", "tandarts vrijdag om 14u") and see
 * what it understood; Continue opens the editor filled in, so nothing is saved before you check it. The
 * sentence is read on the phone only ([QuickAdd]). A sentence without a day goes on [day] (the selected day),
 * or today.
 */
@Composable
fun QuickAddSheet(onDismiss: () -> Unit, day: LocalDate? = null) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val today = LocalDate.now()
    val parsed: QuickEvent? = remember(text) {
        QuickAdd.parse(text, today, LocalTime.now())?.let { e ->
            // Without a day of its own: the day picked in the month view.
            if (!e.dateGiven && day != null && day != today) e.copy(date = day) else e
        }
    }

    fun go() {
        val e = parsed ?: return
        c.prefill = draftOf(e, c.settings.settings.value, c.calendar.calendars.value)
        onDismiss()
        nav.navigate(EditRoute(prefill = true))
    }

    CtrlBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.quick_add), style = MaterialTheme.typography.titleLarge)
            CtrlTextField(
                value = text,
                onValueChange = { text = it.take(200) },
                placeholder = { Text(stringResource(R.string.quick_add_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Bolt, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { go() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            val e = parsed
            Text(
                if (e == null) {
                    stringResource(R.string.quick_add_help)
                } else {
                    val zone = ZoneId.systemDefault()
                    val whenText = when {
                        e.start == null -> stringResource(R.string.quick_add_all_day, relativeDay(context, e.date, long = true))
                        else -> {
                            val s = e.date.atTime(e.start).atZone(zone).toInstant().toEpochMilli()
                            val endText = e.end?.let { en -> "–" + timeText(context, e.date.atTime(en).atZone(zone).toInstant().toEpochMilli()) } ?: ""
                            relativeDay(context, e.date, long = true) + " · " + timeText(context, s) + endText
                        }
                    }
                    stringResource(R.string.quick_add_preview, e.title.ifBlank { stringResource(R.string.no_title) }, whenText)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (e == null) CtrlColors.Muted else CtrlColors.Text,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) }
                TextButton(onClick = ::go, enabled = parsed != null) { Text(stringResource(R.string.quick_add_continue), color = CtrlColors.AccentText) }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** The editor's starting point for what the sentence said, with the default calendar, length and reminder. */
private fun draftOf(e: QuickEvent, settings: AppSettings, calendars: List<CalendarInfo>): EventDraft {
    val base = newDraft(settings, calendars, e.date)
    val zone = ZoneId.systemDefault()
    val title = e.title.take(500)
    val start = e.start
    if (start == null) return base.copy(title = title, allDay = true, startDate = e.date, endDate = e.date)
    val s = e.date.atTime(start).atZone(zone).toInstant().toEpochMilli()
    var end = e.end?.let { e.date.atTime(it).atZone(zone).toInstant().toEpochMilli() } ?: (s + settings.defaultDuration * 60_000L)
    // "22:00-01:00" ends the next day.
    if (end <= s && e.end != null) end = e.date.plusDays(1).atTime(e.end).atZone(zone).toInstant().toEpochMilli()
    val endDate = java.time.Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
    return base.copy(title = title, allDay = false, start = s, end = end, startDate = e.date, endDate = endDate)
}
