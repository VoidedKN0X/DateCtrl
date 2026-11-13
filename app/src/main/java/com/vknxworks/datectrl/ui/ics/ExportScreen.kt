package com.vknxworks.datectrl.ui.ics

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.datectrl.R
import com.vknxworks.datectrl.container
import com.vknxworks.datectrl.data.ics.IcsEvent
import com.vknxworks.datectrl.data.ics.IcsWriter
import com.vknxworks.datectrl.ui.common.dateMedium
import com.vknxworks.datectrl.ui.common.launch
import com.vknxworks.datectrl.ui.components.CtrlButton
import com.vknxworks.datectrl.ui.components.Group
import com.vknxworks.datectrl.ui.components.GroupChoice
import com.vknxworks.datectrl.ui.components.GroupRow
import com.vknxworks.datectrl.ui.components.MessageView
import com.vknxworks.datectrl.ui.components.ScreenTopBar
import com.vknxworks.datectrl.ui.event.DateDialog
import com.vknxworks.datectrl.ui.lock.AppLock
import com.vknxworks.datectrl.ui.nav.LocalNavigator
import com.vknxworks.datectrl.ui.theme.CtrlColors
import com.vknxworks.datectrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Saves a calendar, or the part of it in a date range, as an .ics file where you choose. */
@Composable
fun ExportScreen(initialCalendar: Long) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val calendars by c.calendar.calendars.collectAsStateWithLifecycle()
    var calendarId by rememberSaveable { mutableLongStateOf(initialCalendar) }
    var ranged by rememberSaveable { mutableStateOf(false) }
    var from by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var to by rememberSaveable { mutableLongStateOf(LocalDate.now().plusMonths(3).toEpochDay()) }
    var picking by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<Int?>(null) }
    var pending by remember { mutableStateOf<List<IcsEvent>?>(null) }

    LaunchedEffect(calendars) {
        if (calendars.none { it.id == calendarId }) calendarId = (calendars.firstOrNull { it.visible } ?: calendars.firstOrNull())?.id ?: -1L
    }
    val cal = calendars.firstOrNull { it.id == calendarId }
    val valid = !ranged || to >= from

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        val events = pending
        pending = null
        if (uri == null || events == null) {
            busy = false
            return@rememberLauncherForActivityResult
        }
        c.appScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(IcsWriter.write(events).toByteArray(Charsets.UTF_8)) } != null
                }.getOrDefault(false)
            }
            busy = false
            if (ok) done = events.size else Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.export_title))
        val d = done
        if (d != null) {
            MessageView(
                Icons.Outlined.CheckCircle,
                pluralStringResource(R.plurals.export_done, d, d),
                stringResource(R.string.export_done_body),
                actionLabel = stringResource(R.string.done),
                onAction = { nav.back() },
            )
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Group(footer = stringResource(R.string.export_footer)) {
                GroupChoice(
                    stringResource(R.string.calendar),
                    calendars.map { it.id to calendarLabel(context, it) },
                    calendarId,
                    icon = Icons.Outlined.Layers,
                ) { calendarId = it }
                GroupChoice(
                    stringResource(R.string.export_what),
                    listOf(false to stringResource(R.string.export_everything), true to stringResource(R.string.export_range)),
                    ranged,
                    icon = Icons.Outlined.DateRange,
                ) { ranged = it }
                if (ranged) {
                    GroupRow(stringResource(R.string.export_from), dateMedium(LocalDate.ofEpochDay(from)), icon = Icons.Outlined.Event, showChevron = true, onClick = { picking = 0 })
                    GroupRow(stringResource(R.string.export_to), dateMedium(LocalDate.ofEpochDay(to)), icon = Icons.Outlined.Event, showChevron = true, onClick = { picking = 1 })
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!valid) Text(stringResource(R.string.end_before_start), color = CtrlColors.Error, style = MaterialTheme.typography.bodySmall)
                CtrlButton(
                    stringResource(if (busy) R.string.export_running else R.string.export_button),
                    icon = Icons.Outlined.SaveAlt,
                    enabled = cal != null && valid && !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val target = cal ?: return@CtrlButton
                        busy = true
                        scope.launch {
                            val zone = ZoneId.systemDefault()
                            val events = runCatching {
                                if (ranged) {
                                    c.ics.exportCalendar(
                                        target.id,
                                        LocalDate.ofEpochDay(from).atStartOfDay(zone).toInstant().toEpochMilli(),
                                        LocalDate.ofEpochDay(to).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                                    )
                                } else {
                                    c.ics.exportCalendar(target.id, null, null)
                                }
                            }.getOrNull()
                            when {
                                events == null -> {
                                    busy = false
                                    Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                                }
                                events.isEmpty() -> {
                                    busy = false
                                    Toast.makeText(context, context.getString(R.string.export_nothing), Toast.LENGTH_SHORT).show()
                                }
                                else -> {
                                    pending = events
                                    AppLock.expectReturn()
                                    val ok = runCatching { saver.launch(fileName(target.name)) }.isSuccess
                                    if (!ok) {
                                        pending = null
                                        busy = false
                                        Toast.makeText(context, context.getString(R.string.no_app), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    when (picking) {
        0 -> DateDialog(LocalDate.ofEpochDay(from), onDismiss = { picking = null }) { from = it.toEpochDay() }
        1 -> DateDialog(LocalDate.ofEpochDay(to), onDismiss = { picking = null }) { to = it.toEpochDay() }
    }
}

/** A safe file name from a calendar or event title: "Work.ics". */
internal fun fileName(name: String): String =
    name.replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim().ifBlank { "calendar" }.take(60) + ".ics"

/**
 * Shares one event as an .ics file, readable only by the app you pick and only for that share. The file sits in
 * DateCtrl's private cache and is replaced on the next share.
 */
suspend fun shareEvent(context: Context, eventId: Long, title: String) {
    val c = context.container
    val uri: Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val events = c.ics.exportEvent(eventId)
            if (events.isEmpty()) return@runCatching null
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, fileName(title.ifBlank { context.getString(R.string.event) }))
            file.writeText(IcsWriter.write(events), Charsets.UTF_8)
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.getOrNull()
    }
    if (uri == null) {
        Toast.makeText(context, context.getString(R.string.share_failed), Toast.LENGTH_SHORT).show()
        return
    }
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/calendar")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri("", uri)
    if (!context.launch(Intent.createChooser(send, context.getString(R.string.share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
        Toast.makeText(context, context.getString(R.string.no_app), Toast.LENGTH_SHORT).show()
    }
}
