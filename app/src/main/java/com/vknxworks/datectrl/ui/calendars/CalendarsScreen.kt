package com.vknxworks.datectrl.ui.calendars

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.datectrl.R
import com.vknxworks.datectrl.container
import com.vknxworks.datectrl.data.calendar.CalendarInfo
import com.vknxworks.datectrl.ui.common.CALENDAR_COLORS
import com.vknxworks.datectrl.ui.common.asColor
import com.vknxworks.datectrl.ui.common.launch
import com.vknxworks.datectrl.ui.components.ConfirmDialog
import com.vknxworks.datectrl.ui.components.CtrlAlertDialog
import com.vknxworks.datectrl.ui.components.CtrlSquareButton
import com.vknxworks.datectrl.ui.components.CtrlSwitch
import com.vknxworks.datectrl.ui.components.CtrlTextField
import com.vknxworks.datectrl.ui.components.MessageView
import com.vknxworks.datectrl.ui.components.SectionLabel
import com.vknxworks.datectrl.ui.components.TabTitle
import com.vknxworks.datectrl.ui.components.bottomClearance
import com.vknxworks.datectrl.ui.components.topDivider
import com.vknxworks.datectrl.ui.theme.CtrlColors
import com.vknxworks.datectrl.ui.theme.CtrlShapes
import com.vknxworks.datectrl.ui.theme.CtrlType
import com.vknxworks.datectrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.SaveAlt
import com.vknxworks.datectrl.ui.components.Group
import com.vknxworks.datectrl.ui.components.GroupRow
import com.vknxworks.datectrl.ui.lock.AppLock
import com.vknxworks.datectrl.ui.nav.ExportRoute
import com.vknxworks.datectrl.ui.nav.ImportRoute
import com.vknxworks.datectrl.ui.nav.LocalNavigator

/** The file types the import picker offers (some file managers call .ics files "octet-stream"). */
private val ICS_TYPES = arrayOf("text/calendar", "application/ics", "text/x-vcalendar", "application/octet-stream")

/** Every calendar on the phone, by account: show or hide each one, and manage the ones on this phone. */
@Composable
fun CalendarsScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val calendars by c.calendar.calendars.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CalendarInfo?>(null) }
    var creating by remember { mutableStateOf(false) }
    val nav = LocalNavigator.current
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) nav.navigate(ImportRoute(uri.toString()))
    }
    val groups = remember(calendars) {
        calendars.groupBy { if (it.local) "" else it.accountName }.toSortedMap(compareBy<String> { it.isNotEmpty() }.thenBy { it.lowercase() })
    }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            TabTitle(
                stringResource(R.string.tab_calendars),
                trailing = { CtrlSquareButton(Icons.Outlined.Add, stringResource(R.string.add_calendar), onClick = { creating = true }) },
            )
        }
        if (calendars.isEmpty()) {
            item {
                MessageView(
                    Icons.Outlined.Layers, stringResource(R.string.no_calendars), stringResource(R.string.no_calendars_body),
                    modifier = Modifier.heightIn(min = 280.dp),
                    actionLabel = stringResource(R.string.add_calendar), onAction = { creating = true },
                )
            }
        }
        groups.forEach { (account, list) ->
            item(key = "h_$account") {
                SectionLabel(
                    (if (account.isEmpty()) stringResource(R.string.on_this_phone) else account).uppercase(),
                    Modifier.padding(start = Gutter + 4.dp, end = Gutter, top = 16.dp, bottom = 8.dp),
                )
            }
            item(key = "g_$account") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = Gutter).clip(CtrlShapes.Card).background(CtrlColors.Surface)
                        .border(1.dp, CtrlColors.Outline, CtrlShapes.Card),
                ) {
                    list.forEachIndexed { i, cal ->
                        CalendarRow(cal, divider = i > 0, onToggle = { c.calendar.setVisible(cal.id, it) }, onEdit = if (cal.local) ({ editing = cal }) else null)
                    }
                }
            }
        }
        item(key = "files") {
            Group(stringResource(R.string.group_files), Modifier.padding(top = 8.dp)) {
                GroupRow(
                    stringResource(R.string.import_ics), stringResource(R.string.import_ics_sub), icon = Icons.Outlined.FileOpen, showChevron = true,
                    onClick = {
                        AppLock.expectReturn()
                        if (runCatching { importPicker.launch(ICS_TYPES) }.isFailure) Toast.makeText(context, context.getString(R.string.no_app), Toast.LENGTH_SHORT).show()
                    },
                )
                GroupRow(
                    stringResource(R.string.export_ics), stringResource(R.string.export_ics_sub), icon = Icons.Outlined.SaveAlt, showChevron = true,
                    onClick = if (calendars.isNotEmpty()) ({ nav.navigate(ExportRoute()) }) else null,
                )
            }
        }
        item {
            Column(Modifier.padding(horizontal = Gutter + 4.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.calendars_footer), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
                TextButton(onClick = {
                    if (!context.launch(Intent(Settings.ACTION_SYNC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
                        Toast.makeText(context, context.getString(R.string.no_app), Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp), tint = CtrlColors.AccentText)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.accounts_settings), color = CtrlColors.AccentText)
                }
            }
        }
        item { Spacer(Modifier.bottomClearance(0.dp)) }
    }

    if (creating) {
        CalendarDialog(
            title = stringResource(R.string.add_calendar),
            initialName = "",
            initialColor = CALENDAR_COLORS[calendars.count { it.local } % CALENDAR_COLORS.size],
            onDismiss = { creating = false },
            onDelete = null,
        ) { name, color ->
            creating = false
            scope.launch {
                if (c.calendar.createLocal(name, color) == null) Toast.makeText(context, context.getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
    editing?.let { cal ->
        var confirmDelete by remember(cal.id) { mutableStateOf(false) }
        var count by remember(cal.id) { mutableIntStateOf(0) }
        if (!confirmDelete) {
            CalendarDialog(
                title = stringResource(R.string.edit_calendar),
                initialName = cal.name,
                initialColor = cal.color,
                onDismiss = { editing = null },
                onDelete = {
                    scope.launch {
                        count = c.calendar.eventCount(cal.id)
                        confirmDelete = true
                    }
                },
            ) { name, color ->
                editing = null
                scope.launch { if (!c.calendar.changeLocal(cal, name, color)) Toast.makeText(context, context.getString(R.string.save_failed), Toast.LENGTH_SHORT).show() }
            }
        } else {
            ConfirmDialog(
                title = stringResource(R.string.delete_calendar_title, cal.name),
                body = context.resources.getQuantityString(R.plurals.delete_calendar_body, count, count),
                confirm = stringResource(R.string.delete),
                onDismiss = { editing = null },
                onConfirm = {
                    editing = null
                    scope.launch { if (!c.calendar.deleteLocal(cal)) Toast.makeText(context, context.getString(R.string.delete_failed), Toast.LENGTH_SHORT).show() }
                },
            )
        }
    }
}

@Composable
private fun CalendarRow(cal: CalendarInfo, divider: Boolean, onToggle: (Boolean) -> Unit, onEdit: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (divider) Modifier.topDivider(CtrlColors.Outline) else Modifier)
            .heightIn(min = 62.dp)
            .clickable { onToggle(!cal.visible) }
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(18.dp).clip(CtrlShapes.Dot).background(cal.color.asColor().copy(alpha = if (cal.visible) 1f else 0.35f)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(cal.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = if (cal.visible) CtrlColors.Text else CtrlColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!cal.writable) Text(stringResource(R.string.read_only), style = CtrlType.Data, color = CtrlColors.Muted)
        }
        if (onEdit != null) {
            CtrlSquareButton(Icons.Outlined.Edit, stringResource(R.string.edit_calendar), onClick = onEdit, size = 36.dp, tint = CtrlColors.Muted)
            Spacer(Modifier.width(10.dp))
        }
        CtrlSwitch(cal.visible, onCheckedChange = null)
    }
}

/** Name and colour of a calendar on this phone; Delete when it already exists. */
@Composable
private fun CalendarDialog(title: String, initialName: String, initialColor: Int, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (String, Int) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableIntStateOf(initialColor) }
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CtrlTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    placeholder = { Text(stringResource(R.string.calendar_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CALENDAR_COLORS.forEach { col ->
                        val on = (col or 0xFF000000.toInt()) == (color or 0xFF000000.toInt())
                        Box(
                            Modifier.size(24.dp).clip(CtrlShapes.Chip).background(col.asColor())
                                .then(if (on) Modifier.border(2.dp, CtrlColors.Text, CtrlShapes.Chip) else Modifier)
                                .clickable { color = col }
                                .semantics { contentDescription = "#%06X".format(col and 0xFFFFFF) },
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = CtrlColors.OnAccent) }
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp), tint = CtrlColors.Error)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_calendar), color = CtrlColors.Error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), color) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) } },
    )
}
