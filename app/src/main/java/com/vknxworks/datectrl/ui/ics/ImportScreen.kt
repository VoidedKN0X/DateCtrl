package com.vknxworks.datectrl.ui.ics

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.datectrl.R
import com.vknxworks.datectrl.container
import com.vknxworks.datectrl.data.calendar.CalendarInfo
import com.vknxworks.datectrl.data.calendar.IcsStore
import com.vknxworks.datectrl.data.ics.ExistingEvents
import com.vknxworks.datectrl.data.ics.IcsException
import com.vknxworks.datectrl.data.ics.IcsLimits
import com.vknxworks.datectrl.data.ics.IcsParseResult
import com.vknxworks.datectrl.ui.common.timeRange
import com.vknxworks.datectrl.ui.components.CtrlButton
import com.vknxworks.datectrl.ui.components.CtrlProgressBar
import com.vknxworks.datectrl.ui.components.Group
import com.vknxworks.datectrl.ui.components.GroupChoice
import com.vknxworks.datectrl.ui.components.GroupRow
import com.vknxworks.datectrl.ui.components.MessageView
import com.vknxworks.datectrl.ui.components.ScreenTopBar
import com.vknxworks.datectrl.ui.event.defaultCalendar
import com.vknxworks.datectrl.ui.event.repeatLabel
import com.vknxworks.datectrl.ui.nav.LocalNavigator
import com.vknxworks.datectrl.ui.theme.CtrlColors
import com.vknxworks.datectrl.ui.theme.Gutter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface Loaded {
    data object Loading : Loaded
    data class Failed(val message: Int) : Loaded
    data class Ready(val result: IcsParseResult) : Loaded
}

/** How many events of a file are listed before "and N more". */
private const val PREVIEW = 50

/** Imports the events in an .ics file into a calendar you pick, after showing what's in it. */
@Composable
fun ImportScreen(uriString: String) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val calendars by c.calendar.calendars.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val writable = remember(calendars) { calendars.filter { it.writable } }
    var loaded by remember { mutableStateOf<Loaded>(Loaded.Loading) }
    var calendarId by rememberSaveable { mutableLongStateOf(-1L) }
    var importing by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<IcsStore.ImportResult?>(null) }

    LaunchedEffect(uriString) {
        loaded = try {
            Loaded.Ready(c.ics.read(Uri.parse(uriString)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IcsException) {
            Loaded.Failed(if (e.reason == IcsException.Reason.TOO_BIG) R.string.import_too_big else R.string.import_not_ics)
        } catch (_: Exception) {
            Loaded.Failed(R.string.import_unreadable)
        }
    }
    LaunchedEffect(writable) {
        if (writable.none { it.id == calendarId }) calendarId = defaultCalendar(settings, calendars)?.id ?: -1L
    }
    val target = writable.firstOrNull { it.id == calendarId }
    val events = (loaded as? Loaded.Ready)?.result?.events.orEmpty()
    val existing by produceState<ExistingEvents?>(null, target?.id) { value = target?.let { c.ics.existing(it.id) } }
    val duplicates = remember(events, existing) { existing?.let { ex -> events.count { ex.contains(it) } } ?: 0 }
    val toAdd = events.size - duplicates

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.import_title))
        val r = result
        when (val l = loaded) {
            Loaded.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CtrlColors.AccentText) }
            is Loaded.Failed -> MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.import_failed), stringResource(l.message))
            is Loaded.Ready -> when {
                r != null -> MessageView(
                    Icons.Outlined.CheckCircle,
                    pluralStringResource(R.plurals.import_done, r.added, r.added),
                    listOfNotNull(
                        if (r.skipped > 0) pluralStringResource(R.plurals.import_skipped, r.skipped, r.skipped) else null,
                        if (r.failed > 0) pluralStringResource(R.plurals.import_failed_count, r.failed, r.failed) else null,
                    ).joinToString("\n").ifEmpty { null },
                    actionLabel = stringResource(R.string.done),
                    onAction = { nav.back() },
                )
                events.isEmpty() -> MessageView(Icons.Outlined.EventBusy, stringResource(R.string.import_none), stringResource(R.string.import_none_body))
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                    val notes = listOfNotNull(
                        stringResource(R.string.import_footer),
                        if (l.result.skipped > 0) pluralStringResource(R.plurals.import_unreadable_count, l.result.skipped, l.result.skipped) else null,
                        if (l.result.truncated) pluralStringResource(R.plurals.import_truncated, IcsLimits.MAX_EVENTS, IcsLimits.MAX_EVENTS) else null,
                    )
                    Group(pluralStringResource(R.plurals.import_found, events.size, events.size), footer = notes.joinToString(" ")) {
                        if (writable.isEmpty()) {
                            GroupRow(stringResource(R.string.import_no_calendar), stringResource(R.string.import_no_calendar_body), icon = Icons.Outlined.Layers)
                        } else {
                            GroupChoice(
                                stringResource(R.string.import_to),
                                writable.map { it.id to calendarLabel(context, it) },
                                calendarId,
                                icon = Icons.Outlined.Layers,
                            ) { calendarId = it }
                        }
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (importing) {
                            CtrlProgressBar(progress / events.size.coerceAtLeast(1).toFloat(), color = CtrlColors.Accent)
                            Text(stringResource(R.string.import_progress, progress, events.size), color = CtrlColors.Muted, style = MaterialTheme.typography.bodySmall)
                        } else {
                            CtrlButton(
                                if (toAdd > 0) pluralStringResource(R.plurals.import_button, toAdd, toAdd) else stringResource(R.string.import_nothing_new),
                                onClick = {
                                    val cal = target ?: return@CtrlButton
                                    importing = true
                                    progress = 0
                                    // In the app's scope: leaving the screen doesn't stop an import halfway.
                                    c.appScope.launch {
                                        val res = c.ics.import(cal, events) { progress = it }
                                        c.calendar.refreshNow()
                                        result = res
                                        importing = false
                                    }
                                },
                                enabled = target != null && toAdd > 0 && existing != null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (duplicates > 0) {
                                Text(pluralStringResource(R.plurals.import_duplicates, duplicates, duplicates), color = CtrlColors.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Group(stringResource(R.string.import_preview)) {
                        val ex = existing
                        events.take(PREVIEW).forEach { e ->
                            val time = timeRange(context, e.start, e.end, e.allDay)
                            GroupRow(
                                e.title.ifBlank { stringResource(R.string.no_title) },
                                if (e.repeating) "$time · ${repeatLabel(e.rrule)}" else time,
                                value = if (ex?.contains(e) == true) stringResource(R.string.import_already) else null,
                            )
                        }
                        if (events.size > PREVIEW) GroupRow(pluralStringResource(R.plurals.import_and_more, events.size - PREVIEW, events.size - PREVIEW))
                    }
                }
            }
        }
    }
}

/** "Work · me@example.com", or "Work · On this phone". */
internal fun calendarLabel(context: android.content.Context, cal: CalendarInfo): String =
    "${cal.name} · " + if (cal.local) context.getString(R.string.on_this_phone) else cal.accountName
