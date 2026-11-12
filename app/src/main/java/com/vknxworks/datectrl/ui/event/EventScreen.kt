package com.vknxworks.datectrl.ui.event

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.datectrl.R
import com.vknxworks.datectrl.container
import com.vknxworks.datectrl.data.calendar.EventDetail
import com.vknxworks.datectrl.data.calendar.Repeat
import com.vknxworks.datectrl.ui.common.asColor
import com.vknxworks.datectrl.ui.common.launch
import com.vknxworks.datectrl.ui.common.reminderText
import com.vknxworks.datectrl.ui.common.timeRange
import com.vknxworks.datectrl.ui.components.ChoiceSheet
import com.vknxworks.datectrl.ui.components.ConfirmDialog
import com.vknxworks.datectrl.ui.components.CtrlHeroCard
import com.vknxworks.datectrl.ui.components.CtrlSquareButton
import com.vknxworks.datectrl.ui.components.Group
import com.vknxworks.datectrl.ui.components.GroupRow
import com.vknxworks.datectrl.ui.components.MessageView
import com.vknxworks.datectrl.ui.components.ScreenTopBar
import com.vknxworks.datectrl.ui.nav.EditRoute
import com.vknxworks.datectrl.ui.nav.LocalNavigator
import com.vknxworks.datectrl.ui.theme.CtrlColors
import com.vknxworks.datectrl.ui.theme.CtrlShapes
import com.vknxworks.datectrl.ui.theme.CtrlType
import com.vknxworks.datectrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/** Loading, then the event, or "not found" when it was deleted meanwhile. */
private sealed interface Loaded {
    data object Loading : Loaded
    data object Missing : Loaded
    data class Found(val e: EventDetail) : Loaded
}

@Composable
fun EventScreen(id: Long, begin: Long, end: Long) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val version by c.calendar.version.collectAsStateWithLifecycle()
    val state by produceState<Loaded>(Loaded.Loading, id, version) {
        value = c.calendar.event(id)?.let { Loaded.Found(it) } ?: Loaded.Missing
    }
    val e = (state as? Loaded.Found)?.e

    var askDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            stringResource(R.string.event),
            actions = {
                if (e != null && e.writable) {
                    CtrlSquareButton(Icons.Outlined.Delete, stringResource(R.string.delete), onClick = { askDelete = true }, tint = CtrlColors.Error)
                    CtrlSquareButton(Icons.Outlined.Edit, stringResource(R.string.edit), onClick = { nav.navigate(EditRoute(id, begin)) })
                }
            },
        )
        when (val s = state) {
            Loaded.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CtrlColors.AccentText) }
            Loaded.Missing -> MessageView(Icons.Outlined.EventBusy, stringResource(R.string.event_missing), stringResource(R.string.event_missing_body))
            is Loaded.Found -> Details(s.e, begin, end)
        }
    }

    if (askDelete && e != null) {
        fun done(ok: Boolean) {
            if (ok) nav.back() else Toast.makeText(context, context.getString(R.string.delete_failed), Toast.LENGTH_SHORT).show()
        }
        if (e.repeating && begin > 0) {
            ChoiceSheet(
                stringResource(R.string.delete_repeating),
                listOf(false to stringResource(R.string.scope_this), true to stringResource(R.string.scope_all)),
                null,
                onDismiss = { askDelete = false },
            ) { all ->
                askDelete = false
                scope.launch { done(if (all) c.calendar.delete(e) else c.calendar.deleteOccurrence(e, begin)) }
            }
        } else {
            ConfirmDialog(
                title = stringResource(R.string.delete_event_title),
                body = stringResource(R.string.delete_event_body, e.title.ifBlank { stringResource(R.string.no_title) }),
                confirm = stringResource(R.string.delete),
                onDismiss = { askDelete = false },
                onConfirm = {
                    askDelete = false
                    scope.launch { done(if (e.originalId != null) c.calendar.cancelException(e) else c.calendar.delete(e)) }
                },
            )
        }
    }
}

@Composable
private fun Details(e: EventDetail, begin: Long, end: Long) {
    val context = LocalContext.current
    val b = if (begin > 0) begin else e.dtStart
    val en = if (begin > 0 && end >= begin) end else b + e.lengthMs
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
        CtrlHeroCard(
            label = e.calendarName.ifBlank { stringResource(R.string.event) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp),
        ) {
            SelectionContainer {
                Text(e.title.ifBlank { stringResource(R.string.no_title) }, style = MaterialTheme.typography.headlineMedium, color = CtrlColors.OnAccent)
            }
            Text(timeRange(context, b, en, e.allDay), style = MaterialTheme.typography.titleMedium, color = CtrlColors.OnAccent)
        }
        Group {
            GroupRow(
                stringResource(R.string.calendar), e.calendarName.ifBlank { e.accountName },
                icon = Icons.Outlined.Layers,
                trailing = { Box(Modifier.size(14.dp).clip(CtrlShapes.Dot).background(e.color.asColor())) },
            )
            if (e.repeating) {
                GroupRow(stringResource(R.string.repeat), repeatLabel(e.rrule), icon = Icons.Outlined.Repeat)
            }
            if (e.location.isNotBlank()) {
                GroupRow(
                    stringResource(R.string.location), e.location, icon = Icons.Outlined.Place, showChevron = true,
                    onClick = {
                        val ok = context.launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(e.location))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        if (!ok) Toast.makeText(context, context.getString(R.string.no_map_app), Toast.LENGTH_SHORT).show()
                    },
                )
            }
            GroupRow(
                stringResource(R.string.reminders),
                if (e.reminders.isEmpty()) stringResource(R.string.no_reminders) else e.reminders.joinToString(", ") { reminderText(context, it) },
                icon = Icons.Outlined.NotificationsNone,
            )
        }
        if (e.description.isNotBlank()) {
            Group(stringResource(R.string.description)) {
                Row(Modifier.padding(16.dp)) {
                    androidx.compose.material3.Icon(Icons.AutoMirrored.Outlined.Notes, null, Modifier.size(22.dp), tint = CtrlColors.Text)
                    Spacer(Modifier.width(14.dp))
                    SelectionContainer { Text(e.description.take(20_000), style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Text) }
                }
            }
        }
        if (e.attendees.isNotEmpty()) {
            Group(stringResource(R.string.guests, e.attendees.size)) {
                e.attendees.take(200).forEach { a ->
                    GroupRow(
                        a.name.ifBlank { a.email },
                        listOfNotNull(a.email.takeIf { it.isNotBlank() && a.name.isNotBlank() }, attendeeStatus(a.status, a.organizer)).joinToString(" · "),
                        icon = Icons.Outlined.Group,
                    )
                }
            }
        }
        if (!e.writable) {
            Text(
                stringResource(R.string.read_only_event),
                style = CtrlType.Data, color = CtrlColors.Muted,
                modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun attendeeStatus(status: Int, organizer: Boolean): String? = when {
    organizer -> stringResource(R.string.guest_organizer)
    status == android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED -> stringResource(R.string.guest_yes)
    status == android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED -> stringResource(R.string.guest_no)
    status == android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE -> stringResource(R.string.guest_maybe)
    else -> null
}

@Composable
fun repeatLabel(rrule: String?): String = when (Repeat.of(rrule)) {
    Repeat.NEVER -> stringResource(R.string.repeat_never)
    Repeat.DAILY -> stringResource(R.string.repeat_daily)
    Repeat.WEEKLY -> stringResource(R.string.repeat_weekly)
    Repeat.MONTHLY -> stringResource(R.string.repeat_monthly)
    Repeat.YEARLY -> stringResource(R.string.repeat_yearly)
    Repeat.CUSTOM -> stringResource(R.string.repeat_custom)
}
