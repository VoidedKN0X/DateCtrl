package com.vknxworks.datectrl.ui.event

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.datectrl.R
import com.vknxworks.datectrl.container
import com.vknxworks.datectrl.data.calendar.DAY_MS
import com.vknxworks.datectrl.data.calendar.EventDetail
import com.vknxworks.datectrl.data.calendar.EventDraft
import com.vknxworks.datectrl.data.calendar.Repeat
import com.vknxworks.datectrl.ui.common.CALENDAR_COLORS
import com.vknxworks.datectrl.ui.common.REMINDER_CHOICES
import com.vknxworks.datectrl.ui.common.asColor
import com.vknxworks.datectrl.ui.common.dateMedium
import com.vknxworks.datectrl.ui.common.reminderText
import com.vknxworks.datectrl.ui.common.timeText
import com.vknxworks.datectrl.ui.components.ChoiceSheet
import com.vknxworks.datectrl.ui.components.ConfirmDialog
import com.vknxworks.datectrl.ui.components.CtrlAlertDialog
import com.vknxworks.datectrl.ui.components.CtrlSquareButton
import com.vknxworks.datectrl.ui.components.CtrlTextField
import com.vknxworks.datectrl.ui.components.Group
import com.vknxworks.datectrl.ui.components.GroupRow
import com.vknxworks.datectrl.ui.components.GroupSwitch
import com.vknxworks.datectrl.ui.components.ScreenTopBar
import com.vknxworks.datectrl.ui.components.WarningCard
import com.vknxworks.datectrl.ui.nav.LocalNavigator
import com.vknxworks.datectrl.ui.theme.CtrlColors
import com.vknxworks.datectrl.ui.theme.CtrlShapes
import com.vknxworks.datectrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private sealed interface Start {
    data object Loading : Start
    data object Missing : Start
    data class Ready(val original: EventDetail?, val draft: EventDraft) : Start
}

/**
 * Adding (id -1) or changing an event. [begin] is the occurrence being changed (0: the event itself), [day] the
 * epoch day a new event goes on, [prefill] takes what another app filled in. [onDone] gets the saved event's id
 * (null when cancelled); by default it goes back.
 */
@Composable
fun EditScreen(id: Long, begin: Long, day: Long, prefill: Boolean, onDone: ((Long?) -> Unit)? = null) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val done: (Long?) -> Unit = onDone ?: { _ -> nav.back() }
    val start by produceState<Start>(Start.Loading, id) {
        val settings = c.settings.settings.value
        val calendars = c.calendar.calendarsNow()
        value = if (id >= 0) {
            val e = c.calendar.event(id)
            if (e == null || !e.writable) Start.Missing else Start.Ready(e, draftOf(e, begin))
        } else {
            // Another app's event goes to the default calendar unless it named one that can be written.
            val pre = (if (prefill) c.prefill else null)?.let { p ->
                if (calendars.any { it.writable && it.id == p.calendarId }) p else p.copy(calendarId = defaultCalendar(settings, calendars)?.id ?: -1L)
            }
            val d = if (day != Long.MIN_VALUE) LocalDate.ofEpochDay(day) else LocalDate.now()
            Start.Ready(null, pre ?: newDraft(settings, calendars, d))
        }
    }
    when (val s = start) {
        Start.Loading -> Column(Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(R.string.edit_event))
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CtrlColors.AccentText) }
        }
        Start.Missing -> {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                Toast.makeText(context, context.getString(R.string.event_missing), Toast.LENGTH_SHORT).show()
                done(null)
            }
        }
        is Start.Ready -> EditForm(s.original, begin, s.draft, done)
    }
}

/** The editor's starting point for an existing event (or one occurrence of it). */
private fun draftOf(e: EventDetail, begin: Long): EventDraft {
    val zone = ZoneId.systemDefault()
    val b = if (begin > 0 && e.originalId == null) begin else e.dtStart
    val en = b + e.lengthMs
    val base = EventDraft(
        calendarId = e.calendarId, title = e.title, location = e.location, description = e.description,
        allDay = e.allDay, start = b, end = en, startDate = LocalDate.now(), endDate = LocalDate.now(),
        rrule = e.rrule, reminders = e.reminders,
        timeZone = e.timeZone?.takeIf { !e.allDay && runCatching { ZoneId.of(it) }.isSuccess } ?: zone.id,
    )
    return if (e.allDay) {
        val first = Instant.ofEpochMilli(b).atZone(ZoneOffset.UTC).toLocalDate()
        val last = Instant.ofEpochMilli(en).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1).let { if (it.isBefore(first)) first else it }
        val s = first.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        base.copy(startDate = first, endDate = last, start = s, end = s + 3_600_000L)
    } else {
        base.copy(
            startDate = Instant.ofEpochMilli(b).atZone(zone).toLocalDate(),
            endDate = Instant.ofEpochMilli(en).atZone(zone).toLocalDate(),
        )
    }
}

private fun zone() = ZoneId.systemDefault()
private fun ldt(ms: Long): LocalDateTime = Instant.ofEpochMilli(ms).atZone(zone()).toLocalDateTime()
private fun ms(t: LocalDateTime): Long = t.atZone(zone()).toInstant().toEpochMilli()

/** The RRULE for a simple repeat, tied to the start day (weekday, day of the month). */
fun rruleFor(r: Repeat, start: LocalDate, custom: String?): String? = when (r) {
    Repeat.NEVER -> null
    Repeat.DAILY -> "FREQ=DAILY"
    Repeat.WEEKLY -> "FREQ=WEEKLY;BYDAY=" + byDay(start.dayOfWeek)
    Repeat.MONTHLY -> "FREQ=MONTHLY;BYMONTHDAY=${start.dayOfMonth}"
    Repeat.YEARLY -> "FREQ=YEARLY"
    Repeat.CUSTOM -> custom
}

private fun byDay(d: DayOfWeek) = when (d) {
    DayOfWeek.MONDAY -> "MO"; DayOfWeek.TUESDAY -> "TU"; DayOfWeek.WEDNESDAY -> "WE"; DayOfWeek.THURSDAY -> "TH"
    DayOfWeek.FRIDAY -> "FR"; DayOfWeek.SATURDAY -> "SA"; DayOfWeek.SUNDAY -> "SU"
}

private enum class Picker { START_DATE, START_TIME, END_DATE, END_TIME }

@Composable
private fun EditForm(original: EventDetail?, begin: Long, initial: EventDraft, done: (Long?) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val calendars by c.calendar.calendars.collectAsStateWithLifecycle()
    val writable = calendars.filter { it.writable }
    val originalRepeat = remember { Repeat.of(initial.rrule) }
    var d by remember { mutableStateOf(initial) }
    var repeat by remember { mutableStateOf(originalRepeat) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var pickCalendar by remember { mutableStateOf(false) }
    var pickRepeat by remember { mutableStateOf(false) }
    var addReminder by remember { mutableStateOf(false) }
    var askScope by remember { mutableStateOf(false) }
    var askDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val isException = original?.originalId != null
    val occurrence = original != null && original.repeating && begin > 0
    val changed = d != initial || repeat != originalRepeat
    val valid = if (d.allDay) !d.endDate.isBefore(d.startDate) else d.end >= d.start

    BackHandler(enabled = changed && !saving) { askDiscard = true }

    fun close() {
        if (changed) askDiscard = true else done(null)
    }

    fun save(all: Boolean) {
        if (saving) return
        saving = true
        scope.launch {
            var draft = d.copy(title = d.title.trim(), location = d.location.trim(), description = d.description.trim())
            val canWriteTo = c.calendar.calendarsNow().filter { it.writable }
            if (original == null && canWriteTo.none { it.id == draft.calendarId }) {
                val made = canWriteTo.firstOrNull()?.id ?: c.calendar.createLocal(context.getString(R.string.local_calendar_name), CALENDAR_COLORS.first())
                if (made == null) {
                    saving = false
                    Toast.makeText(context, context.getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                draft = draft.copy(calendarId = made)
            }
            val savedId: Long? = when {
                original == null -> c.calendar.insert(draft.withRule(repeat, initial.rrule))
                occurrence && !all -> c.calendar.updateOccurrence(original, begin, draft.copy(rrule = null))
                occurrence -> seriesDraft(original, begin, draft).withRule(repeat, original.rrule).let { if (c.calendar.update(original, it)) original.id else null }
                else -> draft.withRule(if (isException) Repeat.NEVER else repeat, original.rrule).let { if (c.calendar.update(original, it)) original.id else null }
            }
            saving = false
            if (savedId != null) done(savedId)
            else Toast.makeText(context, context.getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun trySave() {
        if (!valid) return
        // Changing the repeat only makes sense for the whole series.
        if (occurrence && repeat == originalRepeat) askScope = true else save(all = true)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            stringResource(if (original == null) R.string.new_event else R.string.edit_event),
            showBack = false,
            actions = {
                CtrlSquareButton(Icons.Outlined.Close, stringResource(R.string.cancel), onClick = ::close)
                CtrlSquareButton(Icons.Outlined.Check, stringResource(R.string.save), onClick = ::trySave, tint = CtrlColors.AccentText, enabled = valid && !saving)
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(bottom = 24.dp)) {
            CtrlTextField(
                value = d.title,
                onValueChange = { d = d.copy(title = it.take(500)) },
                placeholder = { Text(stringResource(R.string.title_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Title, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
            )

            Group(stringResource(R.string.when_label)) {
                GroupSwitch(stringResource(R.string.all_day), null, d.allDay, Icons.Outlined.WbSunny) { on -> d = toggleAllDay(d, on, c.settings.settings.value.defaultDuration) }
                if (d.allDay) {
                    GroupRow(stringResource(R.string.starts), value = dateMedium(d.startDate), icon = Icons.Outlined.Schedule, onClick = { picker = Picker.START_DATE })
                    GroupRow(stringResource(R.string.ends), value = dateMedium(d.endDate), titleColor = if (valid) CtrlColors.Text else CtrlColors.Error, onClick = { picker = Picker.END_DATE })
                } else {
                    DateTimeRow(context, stringResource(R.string.starts), d.start, true, { picker = Picker.START_DATE }, { picker = Picker.START_TIME })
                    DateTimeRow(context, stringResource(R.string.ends), d.end, valid, { picker = Picker.END_DATE }, { picker = Picker.END_TIME })
                }
                if (!isException) {
                    GroupRow(
                        stringResource(R.string.repeat), repeatLabel(rruleFor(repeat, d.startDate, original?.rrule)),
                        icon = Icons.Outlined.Repeat, showChevron = true, onClick = { pickRepeat = true },
                    )
                }
            }
            if (!valid) {
                WarningCard(stringResource(R.string.end_before_start), Icons.Outlined.Schedule, Modifier.padding(horizontal = Gutter, vertical = 4.dp))
            }

            Group {
                val cal = writable.firstOrNull { it.id == d.calendarId }
                GroupRow(
                    stringResource(R.string.calendar),
                    cal?.name ?: if (writable.isEmpty()) stringResource(R.string.local_calendar_new) else null,
                    icon = Icons.Outlined.Layers,
                    showChevron = original == null && writable.size > 1,
                    onClick = if (original == null && writable.size > 1) ({ pickCalendar = true }) else null,
                    trailing = if (cal != null) {
                        { Box(Modifier.size(14.dp).clip(CtrlShapes.Dot).background(cal.color.asColor())) }
                    } else null,
                )
            }

            Column(Modifier.padding(horizontal = Gutter, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CtrlTextField(
                    value = d.location,
                    onValueChange = { d = d.copy(location = it.take(500)) },
                    placeholder = { Text(stringResource(R.string.location)) },
                    leadingIcon = { Icon(Icons.Outlined.Place, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                CtrlTextField(
                    value = d.description,
                    onValueChange = { d = d.copy(description = it.take(10_000)) },
                    placeholder = { Text(stringResource(R.string.description)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Notes, null) },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Group(stringResource(R.string.reminders)) {
                d.reminders.sorted().forEach { m ->
                    GroupRow(
                        reminderText(context, m), icon = Icons.Outlined.NotificationsNone,
                        trailing = {
                            CtrlSquareButton(Icons.Outlined.Close, stringResource(R.string.remove_reminder), onClick = { d = d.copy(reminders = d.reminders - m) }, size = 36.dp, tint = CtrlColors.Muted)
                        },
                    )
                }
                if (d.reminders.size < 5) {
                    GroupRow(stringResource(R.string.add_reminder), icon = Icons.Outlined.Add, iconTint = CtrlColors.AccentText, titleColor = CtrlColors.AccentText, onClick = { addReminder = true })
                }
            }
        }
    }

    when (picker) {
        Picker.START_DATE -> DateDialog(if (d.allDay) d.startDate else ldt(d.start).toLocalDate(), { picker = null }) { day -> d = setStartDate(d, day) }
        Picker.END_DATE -> DateDialog(if (d.allDay) d.endDate else ldt(d.end).toLocalDate(), { picker = null }) { day -> d = setEndDate(d, day) }
        Picker.START_TIME -> TimeDialog(ldt(d.start).toLocalTime(), { picker = null }) { t -> d = setStartTime(d, t) }
        Picker.END_TIME -> TimeDialog(ldt(d.end).toLocalTime(), { picker = null }) { t -> d = setEndTime(d, t) }
        null -> {}
    }
    if (pickCalendar) {
        ChoiceSheet(stringResource(R.string.calendar), writable.map { it.id to it.name }, d.calendarId, onDismiss = { pickCalendar = false }) { id ->
            d = d.copy(calendarId = id)
            pickCalendar = false
        }
    }
    if (pickRepeat) {
        val options = buildList {
            add(Repeat.NEVER to stringResource(R.string.repeat_never))
            add(Repeat.DAILY to stringResource(R.string.repeat_daily))
            add(Repeat.WEEKLY to stringResource(R.string.repeat_weekly_on, com.vknxworks.datectrl.ui.common.dayShort(d.startDate.dayOfWeek)))
            add(Repeat.MONTHLY to stringResource(R.string.repeat_monthly_on, d.startDate.dayOfMonth))
            add(Repeat.YEARLY to stringResource(R.string.repeat_yearly))
            if (originalRepeat == Repeat.CUSTOM) add(Repeat.CUSTOM to stringResource(R.string.repeat_custom_keep))
        }
        ChoiceSheet(stringResource(R.string.repeat), options, repeat, onDismiss = { pickRepeat = false }) { r ->
            repeat = r
            pickRepeat = false
        }
    }
    if (addReminder) {
        ChoiceSheet(
            stringResource(R.string.add_reminder),
            REMINDER_CHOICES.filter { it !in d.reminders }.map { it to reminderText(context, it) },
            null,
            onDismiss = { addReminder = false },
        ) { m ->
            d = d.copy(reminders = (d.reminders + m).distinct())
            addReminder = false
        }
    }
    if (askScope && original != null) {
        ChoiceSheet(
            stringResource(R.string.edit_repeating),
            listOf(false to stringResource(R.string.scope_this), true to stringResource(R.string.scope_all)),
            null,
            onDismiss = { askScope = false },
        ) { all ->
            askScope = false
            save(all)
        }
    }
    if (askDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            body = stringResource(R.string.discard_body),
            confirm = stringResource(R.string.discard),
            onDismiss = { askDiscard = false },
            onConfirm = {
                askDiscard = false
                done(null)
            },
        )
    }
}

/** A start or end row: the date and the time, each tappable. */
@Composable
private fun DateTimeRow(context: Context, label: String, millis: Long, ok: Boolean, onDate: () -> Unit, onTime: () -> Unit) {
    GroupRow(
        label,
        titleColor = if (ok) CtrlColors.Text else CtrlColors.Error,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDate) { Text(dateMedium(ldt(millis).toLocalDate()), color = CtrlColors.Text) }
                TextButton(onClick = onTime) { Text(timeText(context, millis), color = CtrlColors.AccentText) }
            }
        },
    )
}

private fun EventDraft.withRule(r: Repeat, custom: String?): EventDraft {
    val day = if (allDay) startDate else ldt(start).toLocalDate()
    val rule = rruleFor(r, day, custom)
    // Keep the event's own rule when it means the same, so an unchanged repeat isn't rewritten.
    if (custom != null && rule != null && Repeat.of(custom) == r) {
        val a = ruleParts(rule) - "WKST"
        val b = ruleParts(custom) - "WKST"
        if (a == b || ("BYDAY" !in b && "BYMONTHDAY" !in b && a["FREQ"] == b["FREQ"])) return copy(rrule = custom)
    }
    return copy(rrule = rule)
}

private fun ruleParts(rule: String): Map<String, String> =
    rule.uppercase().split(';').mapNotNull { p -> p.split('=').takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()

/** Changing "all events" from one occurrence: the series moves by as much as that occurrence moved. */
private fun seriesDraft(series: EventDetail, begin: Long, d: EventDraft): EventDraft {
    if (d.allDay != series.allDay) return d
    return if (d.allDay) {
        val instDay = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
        val firstDay = Instant.ofEpochMilli(series.dtStart).atZone(ZoneOffset.UTC).toLocalDate()
        val shift = ChronoUnit.DAYS.between(instDay, d.startDate)
        val len = ChronoUnit.DAYS.between(d.startDate, d.endDate)
        val s = firstDay.plusDays(shift)
        d.copy(startDate = s, endDate = s.plusDays(len))
    } else {
        val s = series.dtStart + (d.start - begin)
        d.copy(start = s, end = s + (d.end - d.start))
    }
}

private fun toggleAllDay(d: EventDraft, on: Boolean, defaultMinutes: Int): EventDraft {
    if (on == d.allDay) return d
    return if (on) {
        val first = ldt(d.start).toLocalDate()
        val lastRaw = ldt((d.end - 1).coerceAtLeast(d.start)).toLocalDate()
        d.copy(allDay = true, startDate = first, endDate = if (lastRaw.isBefore(first)) first else lastRaw)
    } else {
        val s = ms(d.startDate.atTime(ldt(d.start).toLocalTime()))
        val endOnLast = ms(d.endDate.atTime(ldt(d.end).toLocalTime()))
        d.copy(allDay = false, start = s, end = if (endOnLast > s) endOnLast else s + defaultMinutes * 60_000L)
    }
}

private fun setStartDate(d: EventDraft, day: LocalDate): EventDraft {
    val len = ChronoUnit.DAYS.between(d.startDate, d.endDate).coerceAtLeast(0)
    val dur = (d.end - d.start).coerceAtLeast(0)
    val s = ms(day.atTime(ldt(d.start).toLocalTime()))
    return d.copy(startDate = day, endDate = day.plusDays(len), start = s, end = s + dur)
}

private fun setEndDate(d: EventDraft, day: LocalDate): EventDraft =
    d.copy(endDate = day, end = ms(day.atTime(ldt(d.end).toLocalTime())))

private fun setStartTime(d: EventDraft, t: LocalTime): EventDraft {
    val dur = (d.end - d.start).coerceAtLeast(0)
    val s = ms(ldt(d.start).toLocalDate().atTime(t))
    return d.copy(start = s, end = s + dur)
}

private fun setEndTime(d: EventDraft, t: LocalTime): EventDraft {
    var e = ms(ldt(d.end).toLocalDate().atTime(t))
    // Picking an earlier time on the same day as the start usually means the next day (a late-night event).
    if (e < d.start && ldt(d.end).toLocalDate() == ldt(d.start).toLocalDate() && t.isBefore(ldt(d.start).toLocalTime())) e = ms(ldt(d.end).toLocalDate().plusDays(1).atTime(t))
    return d.copy(end = e)
}

@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toEpochDay() * DAY_MS)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(Math.floorDiv(it, DAY_MS))) }
                onDismiss()
            }) { Text(stringResource(R.string.ok), color = CtrlColors.AccentText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) } },
        shape = CtrlShapes.Dialog,
        colors = DatePickerDefaults.colors(containerColor = CtrlColors.Surface),
    ) {
        DatePicker(
            state,
            colors = DatePickerDefaults.colors(
                containerColor = CtrlColors.Surface,
                selectedDayContainerColor = CtrlColors.Accent,
                selectedDayContentColor = CtrlColors.OnAccent,
                todayDateBorderColor = CtrlColors.Accent,
                todayContentColor = CtrlColors.AccentText,
                selectedYearContainerColor = CtrlColors.Accent,
                selectedYearContentColor = CtrlColors.OnAccent,
            ),
        )
    }
}

@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(initial.hour, initial.minute, android.text.format.DateFormat.is24HourFormat(context))
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) { Text(stringResource(R.string.ok), color = CtrlColors.AccentText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) } },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = CtrlColors.Background,
                    selectorColor = CtrlColors.Accent,
                    containerColor = CtrlColors.Surface,
                    clockDialSelectedContentColor = CtrlColors.OnAccent,
                    clockDialUnselectedContentColor = CtrlColors.Text,
                    periodSelectorSelectedContainerColor = CtrlColors.Accent,
                    periodSelectorSelectedContentColor = CtrlColors.OnAccent,
                    periodSelectorUnselectedContentColor = CtrlColors.Text,
                    periodSelectorBorderColor = CtrlColors.ControlOutline,
                    timeSelectorSelectedContainerColor = CtrlColors.Accent,
                    timeSelectorSelectedContentColor = CtrlColors.OnAccent,
                    timeSelectorUnselectedContainerColor = CtrlColors.Background,
                    timeSelectorUnselectedContentColor = CtrlColors.Text,
                ),
            )
        },
    )
}
