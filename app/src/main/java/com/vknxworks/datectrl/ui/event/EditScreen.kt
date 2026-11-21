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
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Public
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
import com.vknxworks.datectrl.data.calendar.Freq
import com.vknxworks.datectrl.data.calendar.Repeat
import com.vknxworks.datectrl.data.calendar.RepeatRule
import com.vknxworks.datectrl.data.calendar.RepeatRules
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
        // Timed events are shown in their own time zone.
        val ez = base.z()
        base.copy(
            startDate = Instant.ofEpochMilli(b).atZone(ez).toLocalDate(),
            endDate = Instant.ofEpochMilli(en).atZone(ez).toLocalDate(),
        )
    }
}

private fun zone() = ZoneId.systemDefault()
private fun ldt(ms: Long, z: ZoneId = zone()): LocalDateTime = Instant.ofEpochMilli(ms).atZone(z).toLocalDateTime()
private fun ms(t: LocalDateTime, z: ZoneId = zone()): Long = t.atZone(z).toInstant().toEpochMilli()

/** The event's own time zone (times in the editor are shown and picked in it); the phone's if it's unknown. */
internal fun EventDraft.z(): ZoneId = runCatching { ZoneId.of(timeZone) }.getOrDefault(zone())

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
    var customReminder by remember { mutableStateOf(false) }
    var pickZone by remember { mutableStateOf(false) }
    var editCustom by remember { mutableStateOf(false) }
    // A repeat the custom sheet can show and build again (every N, until, a number of times); null otherwise.
    var custom by remember { mutableStateOf(RepeatRules.parse(initial.rrule, initial.z())) }
    var askScope by remember { mutableStateOf(false) }
    var askDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val isException = original?.originalId != null
    val occurrence = original != null && original.repeating && begin > 0
    val originalCustom = remember { RepeatRules.parse(initial.rrule, initial.z()) }
    val changed = d != initial || repeat != originalRepeat || (repeat == Repeat.CUSTOM && custom != originalCustom)

    /** The rule written for "Custom": the one built in the custom sheet, or the event's own one kept as it is. */
    fun customRule(draft: EventDraft): String? {
        val r = custom ?: return original?.rrule ?: initial.rrule
        val day = if (draft.allDay) draft.startDate else Instant.ofEpochMilli(draft.start).atZone(draft.z()).toLocalDate()
        val built = RepeatRules.build(r, day, draft.allDay, draft.z())
        // Unchanged: keep the event's own text, so an unchanged repeat isn't rewritten.
        val own = original?.rrule
        return if (own != null && r == originalCustom && RepeatRules.parse(own, draft.z()) == r && repeat == originalRepeat) own else built
    }
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
                original == null -> c.calendar.insert(draft.withRule(repeat, customRule(draft)))
                occurrence && !all -> c.calendar.updateOccurrence(original, begin, draft.copy(rrule = null))
                occurrence -> seriesDraft(original, begin, draft).let { sd -> sd.withRule(repeat, customRule(sd)) }.let { if (c.calendar.update(original, it)) original.id else null }
                else -> draft.withRule(if (isException) Repeat.NEVER else repeat, customRule(draft)).let { if (c.calendar.update(original, it)) original.id else null }
            }
            saving = false
            if (savedId != null) done(savedId)
            else Toast.makeText(context, context.getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun trySave() {
        if (!valid) return
        // Changing the repeat only makes sense for the whole series.
        if (occurrence && repeat == originalRepeat && (repeat != Repeat.CUSTOM || custom == originalCustom)) askScope = true else save(all = true)
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
                    DateTimeRow(context, stringResource(R.string.starts), d.start, true, d.z(), { picker = Picker.START_DATE }, { picker = Picker.START_TIME })
                    DateTimeRow(context, stringResource(R.string.ends), d.end, valid, d.z(), { picker = Picker.END_DATE }, { picker = Picker.END_TIME })
                    // Times are picked in the event's own time zone; another one shows what that is here.
                    val here = ZoneId.systemDefault()
                    GroupRow(
                        stringResource(R.string.time_zone),
                        if (d.z().rules.getOffset(Instant.ofEpochMilli(d.start)) != here.rules.getOffset(Instant.ofEpochMilli(d.start))) {
                            stringResource(R.string.time_zone_here, timeText(context, d.start, here), timeText(context, d.end, here))
                        } else {
                            null
                        },
                        icon = Icons.Outlined.Public,
                        value = zoneLabel(d.z(), d.start),
                        onClick = { pickZone = true },
                    )
                }
                if (!isException) {
                    GroupRow(
                        stringResource(R.string.repeat), repeatLabel(rruleFor(repeat, d.startDate, customRule(d)), d.z()),
                        icon = Icons.Outlined.Repeat, showChevron = true, onClick = { pickRepeat = true },
                    )
                }
            }
            if (!valid) {
                WarningCard(stringResource(R.string.end_before_start), Icons.Outlined.Schedule, Modifier.padding(horizontal = Gutter, vertical = 4.dp))
            } else if (!d.allDay) {
                // Other events at the same time (not all-day ones, not ones you declined, not this event itself).
                val clashes by produceState(emptyList<com.vknxworks.datectrl.data.calendar.Instance>(), d.start, d.end) {
                    value = if (d.end > d.start) {
                        c.calendar.instances(d.start - 1, d.end + 1).filter {
                            !it.allDay && !it.declined && it.eventId != original?.id && it.eventId != original?.originalId &&
                                it.begin < d.end && it.end > d.start
                        }
                    } else {
                        emptyList()
                    }
                }
                if (clashes.isNotEmpty()) {
                    val list = clashes.take(3).joinToString("\n") { e ->
                        e.title.ifBlank { context.getString(R.string.no_title) } + " · " + timeText(context, e.begin) + "–" + timeText(context, e.end)
                    } + if (clashes.size > 3) "\n" + context.resources.getQuantityString(R.plurals.overlap_more, clashes.size - 3, clashes.size - 3) else ""
                    WarningCard(stringResource(R.string.overlap_warning, list), Icons.Outlined.EventBusy, Modifier.padding(horizontal = Gutter, vertical = 4.dp))
                }
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
                if (d.reminders.size < MAX_REMINDERS) {
                    GroupRow(stringResource(R.string.add_reminder), icon = Icons.Outlined.Add, iconTint = CtrlColors.AccentText, titleColor = CtrlColors.AccentText, onClick = { addReminder = true })
                }
            }
        }
    }

    when (picker) {
        Picker.START_DATE -> DateDialog(if (d.allDay) d.startDate else ldt(d.start, d.z()).toLocalDate(), { picker = null }) { day -> d = setStartDate(d, day) }
        Picker.END_DATE -> DateDialog(if (d.allDay) d.endDate else ldt(d.end, d.z()).toLocalDate(), { picker = null }) { day -> d = setEndDate(d, day) }
        Picker.START_TIME -> TimeDialog(ldt(d.start, d.z()).toLocalTime(), { picker = null }) { t -> d = setStartTime(d, t) }
        Picker.END_TIME -> TimeDialog(ldt(d.end, d.z()).toLocalTime(), { picker = null }) { t -> d = setEndTime(d, t) }
        null -> {}
    }
    if (pickCalendar) {
        ChoiceSheet(stringResource(R.string.calendar), writable.map { it.id to it.name }, d.calendarId, onDismiss = { pickCalendar = false }) { id ->
            d = d.copy(calendarId = id)
            pickCalendar = false
        }
    }
    if (pickRepeat) {
        // null stands for "Custom…", which opens the custom repeat sheet.
        val options = buildList<Pair<Repeat?, String>> {
            add(Repeat.NEVER to stringResource(R.string.repeat_never))
            add(Repeat.DAILY to stringResource(R.string.repeat_daily))
            add(Repeat.WEEKLY to stringResource(R.string.repeat_weekly_on, com.vknxworks.datectrl.ui.common.dayShort(d.startDate.dayOfWeek)))
            add(Repeat.MONTHLY to stringResource(R.string.repeat_monthly_on, d.startDate.dayOfMonth))
            add(Repeat.YEARLY to stringResource(R.string.repeat_yearly))
            // The event's own rule that the custom sheet can't show stays available as it is.
            if (originalRepeat == Repeat.CUSTOM && originalCustom == null) add(Repeat.CUSTOM to stringResource(R.string.repeat_custom_keep))
            add(null to stringResource(R.string.repeat_custom_build))
        }
        ChoiceSheet(stringResource(R.string.repeat), options, if (repeat == Repeat.CUSTOM && custom != null) null else repeat, onDismiss = { pickRepeat = false }) { r ->
            pickRepeat = false
            if (r == null) {
                editCustom = true
            } else {
                repeat = r
                if (r == Repeat.CUSTOM) custom = null
            }
        }
    }
    if (editCustom) {
        val startDay = if (d.allDay) d.startDate else ldt(d.start, d.z()).toLocalDate()
        RepeatSheet(
            initial = custom ?: RepeatRule(
                when (repeat) {
                    Repeat.DAILY -> Freq.DAILY
                    Repeat.MONTHLY -> Freq.MONTHLY
                    Repeat.YEARLY -> Freq.YEARLY
                    else -> Freq.WEEKLY
                },
            ),
            start = startDay,
            onDismiss = { editCustom = false },
        ) { r ->
            editCustom = false
            custom = r
            repeat = Repeat.CUSTOM
        }
    }
    if (pickZone) {
        ZoneSheet(d.z(), d.start, onDismiss = { pickZone = false }) { z ->
            pickZone = false
            d = changeZone(d, z)
        }
    }
    if (customReminder) {
        CustomReminderDialog(onDismiss = { customReminder = false }) { m ->
            customReminder = false
            d = d.copy(reminders = (d.reminders + m).distinct())
        }
    }
    if (addReminder) {
        ChoiceSheet(
            stringResource(R.string.add_reminder),
            REMINDER_CHOICES.filter { it !in d.reminders }.map { it to reminderText(context, it) } + (CUSTOM_REMINDER to stringResource(R.string.reminder_custom)),
            null,
            onDismiss = { addReminder = false },
        ) { m ->
            addReminder = false
            if (m == CUSTOM_REMINDER) customReminder = true else d = d.copy(reminders = (d.reminders + m).distinct())
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
private fun DateTimeRow(context: Context, label: String, millis: Long, ok: Boolean, zone: ZoneId, onDate: () -> Unit, onTime: () -> Unit) {
    GroupRow(
        label,
        titleColor = if (ok) CtrlColors.Text else CtrlColors.Error,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDate) { Text(dateMedium(ldt(millis, zone).toLocalDate()), color = CtrlColors.Text) }
                TextButton(onClick = onTime) { Text(timeText(context, millis, zone), color = CtrlColors.AccentText) }
            }
        },
    )
}

/** Marks "Custom…" in the reminder list. */
private const val CUSTOM_REMINDER = -1

/** The most reminders one event can have here (as before: calendar accounts often allow no more than 5). */
private const val MAX_REMINDERS = 5

/** Another time zone keeps the times on the clock: 14:00 stays 14:00, now in [z]. */
private fun changeZone(d: EventDraft, z: ZoneId): EventDraft {
    val old = d.z()
    val s = ldt(d.start, old).atZone(z).toInstant().toEpochMilli()
    val e = ldt(d.end, old).atZone(z).toInstant().toEpochMilli()
    return d.copy(timeZone = z.id, start = s, end = e)
}

private fun EventDraft.withRule(r: Repeat, custom: String?): EventDraft {
    val day = if (allDay) startDate else ldt(start, z()).toLocalDate()
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
        val first = ldt(d.start, d.z()).toLocalDate()
        val lastRaw = ldt((d.end - 1).coerceAtLeast(d.start), d.z()).toLocalDate()
        d.copy(allDay = true, startDate = first, endDate = if (lastRaw.isBefore(first)) first else lastRaw)
    } else {
        val s = ms(d.startDate.atTime(ldt(d.start, d.z()).toLocalTime()), d.z())
        val endOnLast = ms(d.endDate.atTime(ldt(d.end, d.z()).toLocalTime()), d.z())
        d.copy(allDay = false, start = s, end = if (endOnLast > s) endOnLast else s + defaultMinutes * 60_000L)
    }
}

private fun setStartDate(d: EventDraft, day: LocalDate): EventDraft {
    val len = ChronoUnit.DAYS.between(d.startDate, d.endDate).coerceAtLeast(0)
    val dur = (d.end - d.start).coerceAtLeast(0)
    val s = ms(day.atTime(ldt(d.start, d.z()).toLocalTime()), d.z())
    return d.copy(startDate = day, endDate = day.plusDays(len), start = s, end = s + dur)
}

private fun setEndDate(d: EventDraft, day: LocalDate): EventDraft =
    d.copy(endDate = day, end = ms(day.atTime(ldt(d.end, d.z()).toLocalTime()), d.z()))

private fun setStartTime(d: EventDraft, t: LocalTime): EventDraft {
    val dur = (d.end - d.start).coerceAtLeast(0)
    val s = ms(ldt(d.start, d.z()).toLocalDate().atTime(t), d.z())
    return d.copy(start = s, end = s + dur)
}

private fun setEndTime(d: EventDraft, t: LocalTime): EventDraft {
    var e = ms(ldt(d.end, d.z()).toLocalDate().atTime(t), d.z())
    // Picking an earlier time on the same day as the start usually means the next day (a late-night event).
    if (e < d.start && ldt(d.end, d.z()).toLocalDate() == ldt(d.start, d.z()).toLocalDate() && t.isBefore(ldt(d.start, d.z()).toLocalTime())) e = ms(ldt(d.end, d.z()).toLocalDate().plusDays(1).atTime(t), d.z())
    return d.copy(end = e)
}

@Composable
internal fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
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
