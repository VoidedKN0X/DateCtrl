package com.vknxworks.datectrl.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.datectrl.R
import com.vknxworks.datectrl.container
import com.vknxworks.datectrl.ui.lock.AppLock
import com.vknxworks.datectrl.ui.theme.CtrlColors
import com.vknxworks.datectrl.ui.theme.CtrlShapes
import com.vknxworks.datectrl.ui.theme.CtrlType
import com.vknxworks.datectrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset


// ---------- Fields ----------

@Composable
fun FieldHeading(text: String) {
    SectionLabel(text, Modifier.padding(start = 4.dp, top = 8.dp))
}

/** A note under a group of fields. */
@Composable
fun FieldNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
fun TextInput(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    words: Boolean = false,
    sentences: Boolean = false,
    suffix: String? = null,
    placeholder: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    CtrlTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) {
            { Text(placeholder) }
        } else {
            null
        },
        trailingIcon = if (suffix != null) {
            { Text(suffix, style = CtrlType.Data, color = CtrlColors.Muted, modifier = Modifier.padding(end = 12.dp)) }
        } else {
            null
        },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = when {
                words -> KeyboardCapitalization.Words
                sentences -> KeyboardCapitalization.Sentences
                else -> KeyboardCapitalization.None
            },
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** A number field: accepts digits with a comma or a period, nothing else. */
@Composable
fun NumberInput(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    decimals: Boolean = true,
    placeholder: String? = null,
) {
    TextInput(
        label,
        value,
        onChange = { t ->
            val ok = if (decimals) t.all { it.isDigit() || it == ',' || it == '.' || it == '-' } else t.all { it.isDigit() }
            if (ok && t.length <= 14) onChange(t)
        },
        modifier = modifier,
        suffix = suffix,
        placeholder = placeholder,
        keyboard = if (decimals) KeyboardType.Decimal else KeyboardType.Number,
    )
}

/** A tappable field row in the field style (date pickers, choices). */
@Composable
fun PickerField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClear: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(CtrlShapes.Field)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.ControlOutline, CtrlShapes.Field)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = CtrlColors.Text)
        }
        if (onClear != null) {
            CtrlSquareButton(Icons.Outlined.Close, stringResource(R.string.clear), onClick = onClear, size = 36.dp, tint = CtrlColors.Muted)
        } else if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = CtrlColors.Muted)
        }
    }
}

/** A choice field that opens a sheet with the options. */
@Composable
fun <T> ChoiceInput(label: String, options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    PickerField(label, options.firstOrNull { it.first == selected }?.second ?: "", onClick = { open = true })
    if (open) ChoiceSheet(label, options, selected, onDismiss = { open = false }) { onPick(it); open = false }
}

/** A switch row in a card, for forms. */
@Composable
fun SwitchInput(label: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    CtrlCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        GroupSwitch(label, subtitle, checked = checked, onChange = onChange)
    }
}



/** A row in a log list: title and details on the left, a value on the right. */
@Composable
fun EntryRow(title: String, subtitle: String?, value: String?, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    GroupRow(title, subtitle, value = value, onClick = onClick, trailing = trailing, showChevron = trailing == null && value == null)
}

/** A small stat: mono label above a value. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null) {
    CtrlCard(modifier, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        SectionLabel(label)
        Spacer(Modifier.size(6.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = CtrlColors.Text, maxLines = 1)
        if (sub != null) Text(sub, style = CtrlType.Data, color = CtrlColors.Muted, maxLines = 1)
    }
}

/** Two stat tiles side by side. */
@Composable
fun StatRow(a: @Composable (Modifier) -> Unit, b: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        a(Modifier.weight(1f))
        b(Modifier.weight(1f))
    }
}

/** How numbers look, following Settings (decimal separator, grouping, currency). */
