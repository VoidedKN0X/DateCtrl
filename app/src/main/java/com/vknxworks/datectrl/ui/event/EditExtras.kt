package com.vknxworks.datectrl.ui.event

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.datectrl.R
import com.vknxworks.datectrl.data.calendar.Freq
import com.vknxworks.datectrl.data.calendar.RepeatRule
import com.vknxworks.datectrl.data.calendar.RepeatRules
import com.vknxworks.datectrl.ui.common.dateMedium
import com.vknxworks.datectrl.ui.components.CtrlAlertDialog
import com.vknxworks.datectrl.ui.components.CtrlBottomSheet
import com.vknxworks.datectrl.ui.components.CtrlTextField
import com.vknxworks.datectrl.ui.components.SectionLabel
import com.vknxworks.datectrl.ui.components.topDivider
import com.vknxworks.datectrl.ui.theme.CtrlColors
import com.vknxworks.datectrl.ui.theme.CtrlShapes
import com.vknxworks.datectrl.ui.theme.CtrlType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

// ---------- Custom repeat ----------

private enum class EndKind { NEVER, UNTIL, COUNT }

/**
 * Builds a repeat: every N days, weeks, months or years, ending never, on a date or after a number of times.
 * Weekly repeats fall on the start day's weekday, monthly ones on its day of the month.
 */
@Composable
fun RepeatSheet(initial: RepeatRule, start: LocalDate, onDismiss: () -> Unit, onDone: (RepeatRule) -> Unit) {
    var freq by remember { mutableStateOf(initial.freq) }
    var every by remember { mutableStateOf(initial.interval.toString()) }
    var end by remember { mutableStateOf(if (initial.until != null) EndKind.UNTIL else if (initial.count != null) EndKind.COUNT else EndKind.NEVER) }
    var until by remember { mutableStateOf(initial.until ?: start.plusMonths(3)) }
    var times by remember { mutableStateOf((initial.count ?: 10).toString()) }
    var pickUntil by remember { mutableStateOf(false) }

    val n = every.toIntOrNull()?.takeIf { it in 1..RepeatRules.MAX_INTERVAL }
    val count = times.toIntOrNull()?.takeIf { it in 1..RepeatRules.MAX_COUNT }
    val untilOk = !until.isBefore(start)
    val rule = when {
        n == null -> null
        end == EndKind.UNTIL && untilOk -> RepeatRule(freq, n, until = until)
        end == EndKind.COUNT && count != null -> RepeatRule(freq, n, count = count)
        end == EndKind.NEVER -> RepeatRule(freq, n)
        else -> null
    }

    CtrlBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.repeat_custom_title), style = MaterialTheme.typography.titleLarge)
            SectionLabel(stringResource(R.string.repeat_every))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CtrlTextField(
                    value = every,
                    onValueChange = { v -> if (v.length <= 2 && v.all { it.isDigit() }) every = v },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(0.35f),
                )
                Chips(
                    listOf(
                        Freq.DAILY to stringResource(R.string.unit_days),
                        Freq.WEEKLY to stringResource(R.string.unit_weeks),
                        Freq.MONTHLY to stringResource(R.string.unit_months),
                        Freq.YEARLY to stringResource(R.string.unit_years),
                    ),
                    freq,
                    Modifier.weight(1f),
                ) { freq = it }
            }
            Text(
                when (freq) {
                    Freq.WEEKLY -> stringResource(R.string.repeat_on_weekday, start.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                    Freq.MONTHLY -> stringResource(R.string.repeat_on_monthday, start.dayOfMonth)
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
            )
            SectionLabel(stringResource(R.string.repeat_ends))
            Chips(
                listOf(
                    EndKind.NEVER to stringResource(R.string.repeat_ends_never),
                    EndKind.UNTIL to stringResource(R.string.repeat_ends_on),
                    EndKind.COUNT to stringResource(R.string.repeat_ends_after),
                ),
                end,
                Modifier.fillMaxWidth(),
            ) { end = it }
            when (end) {
                EndKind.UNTIL -> TextButton(onClick = { pickUntil = true }) {
                    Text(stringResource(R.string.repeat_last_day, dateMedium(until)), color = if (untilOk) CtrlColors.AccentText else CtrlColors.Error)
                }
                EndKind.COUNT -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CtrlTextField(
                        value = times,
                        onValueChange = { v -> if (v.length <= 3 && v.all { it.isDigit() }) times = v },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.weight(0.35f),
                    )
                    Text(stringResource(R.string.repeat_times_unit), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
                EndKind.NEVER -> Unit
            }
            Text(
                if (rule != null) repeatRuleLabel(rule) else stringResource(R.string.repeat_invalid),
                style = MaterialTheme.typography.bodyMedium,
                color = if (rule != null) CtrlColors.Text else CtrlColors.Error,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) }
                TextButton(onClick = { rule?.let(onDone) }, enabled = rule != null) { Text(stringResource(R.string.ok), color = CtrlColors.AccentText) }
            }
        }
    }
    if (pickUntil) DateDialog(until, { pickUntil = false }) { until = it }
}

@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: T, modifier: Modifier = Modifier, onPick: (T) -> Unit) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(CtrlShapes.Chip)
                    .background(if (on) CtrlColors.Accent else CtrlColors.Surface)
                    .border(1.dp, if (on) CtrlColors.Accent else CtrlColors.ControlOutline, CtrlShapes.Chip)
                    .clickable { onPick(value) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = CtrlType.LabelStrong, color = if (on) CtrlColors.OnAccent else CtrlColors.Text)
            }
        }
    }
}

// ---------- Time zone ----------

/** "Brussels · UTC+1" for the moment [at]. */
fun zoneLabel(z: ZoneId, at: Long): String {
    val city = z.id.substringAfterLast('/').replace('_', ' ')
    val offset = z.rules.getOffset(Instant.ofEpochMilli(at)).id.let { if (it == "Z") "" else it }
    return "$city · UTC$offset"
}

/** Every time zone the phone knows, searchable by city, region or offset; the phone's own first. */
@Composable
fun ZoneSheet(current: ZoneId, at: Long, onDismiss: () -> Unit, onPick: (ZoneId) -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = remember {
        ZoneId.getAvailableZoneIds()
            .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
            .sorted()
            .map { ZoneId.of(it) }
    }
    val here = remember { ZoneId.systemDefault() }
    val q = query.trim().lowercase()
    val shown = remember(q) {
        val base = listOf(here) + all.filter { it != here }
        if (q.isEmpty()) base else base.filter { z -> z.id.lowercase().replace('_', ' ').contains(q) || zoneLabel(z, at).lowercase().contains(q) }
    }
    CtrlBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.time_zone), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        CtrlTextField(
            value = query,
            onValueChange = { query = it.take(40) },
            placeholder = { Text(stringResource(R.string.time_zone_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(shown, key = { it.id }) { z ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .topDivider()
                        .clickable { onPick(z) }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            zoneLabel(z, at),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (z == current) CtrlColors.AccentText else CtrlColors.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (z == here) stringResource(R.string.time_zone_phone, z.id) else z.id,
                            style = MaterialTheme.typography.bodySmall,
                            color = CtrlColors.Muted,
                        )
                    }
                }
            }
        }
    }
}

// ---------- Custom reminder ----------

/** A reminder of any number of minutes, hours, days or weeks before (up to four weeks, Android's usual limit). */
@Composable
fun CustomReminderDialog(onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var amount by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf(60) }
    val n = amount.toIntOrNull()
    val minutes = n?.let { it * unit }?.takeIf { it in 1..MAX_REMINDER_MINUTES }
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_custom), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CtrlTextField(
                    value = amount,
                    onValueChange = { v -> if (v.length <= 4 && v.all { it.isDigit() }) amount = v },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Chips(
                    listOf(
                        1 to stringResource(R.string.unit_minutes),
                        60 to stringResource(R.string.unit_hours),
                        1440 to stringResource(R.string.unit_days),
                        10080 to stringResource(R.string.unit_weeks),
                    ),
                    unit,
                    Modifier.fillMaxWidth(),
                ) { unit = it }
                Text(
                    stringResource(if (minutes == null) R.string.reminder_custom_invalid else R.string.reminder_custom_before),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (minutes == null) CtrlColors.Error else CtrlColors.Muted,
                )
            }
        },
        confirmButton = { TextButton(onClick = { minutes?.let(onPick) }, enabled = minutes != null) { Text(stringResource(R.string.ok), color = CtrlColors.AccentText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) } },
    )
}

/** Four weeks: the longest reminder the editor sets. */
const val MAX_REMINDER_MINUTES = 4 * 7 * 1440
