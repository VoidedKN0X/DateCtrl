package com.vknxworks.datectrl.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import com.vknxworks.datectrl.data.calendar.SearchHit
import com.vknxworks.datectrl.ui.event.QuickAddSheet
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.datectrl.R
import com.vknxworks.datectrl.container
import com.vknxworks.datectrl.data.calendar.Instance
import com.vknxworks.datectrl.ui.common.rememberToday
import com.vknxworks.datectrl.ui.common.relativeDay
import com.vknxworks.datectrl.ui.components.CtrlSecondaryButton
import com.vknxworks.datectrl.ui.components.CtrlSquareButton
import com.vknxworks.datectrl.ui.components.CtrlTextField
import com.vknxworks.datectrl.ui.components.MessageView
import com.vknxworks.datectrl.ui.components.SectionLabel
import com.vknxworks.datectrl.ui.components.TabTitle
import com.vknxworks.datectrl.ui.components.bottomClearance
import com.vknxworks.datectrl.ui.event.InstanceRow
import com.vknxworks.datectrl.ui.nav.EditRoute
import com.vknxworks.datectrl.ui.nav.EventRoute
import com.vknxworks.datectrl.ui.nav.LocalNavigator
import com.vknxworks.datectrl.ui.theme.CtrlColors
import com.vknxworks.datectrl.ui.theme.Gutter
import java.time.LocalDate

/** Upcoming events, day by day, with search. Starts with three months and grows a year at a time. */
@Composable
fun AgendaScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val version by c.calendar.version.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var months by rememberSaveable { mutableIntStateOf(3) }
    // Follows the calendar: after midnight the list starts at the new day.
    val today = rememberToday()
    val until = today.plusMonths(months.toLong())
    val all by produceState<List<Instance>?>(null, version, months, today) {
        value = c.calendar.instancesForDays(today, until)
    }
    val q = query.trim().lowercase()
    // Searching looks through every event in your calendars, past and future, not only the months shown.
    val hits by produceState<List<SearchHit>?>(null, q, version) {
        value = null
        if (q.isNotEmpty()) {
            delay(250)
            value = c.calendar.searchEvents(q)
        }
    }
    val shown = remember(all, q) { if (q.isEmpty()) all.orEmpty() else emptyList() }
    var quickAdd by rememberSaveable { mutableStateOf(false) }
    // Each event under the day it starts, or today for one that's already going on.
    val byDay = remember(shown, today) { shown.groupBy { maxOf(it.firstDay(), today) }.toSortedMap() }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            TabTitle(
                stringResource(R.string.tab_agenda),
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CtrlSquareButton(Icons.Outlined.Bolt, stringResource(R.string.quick_add), onClick = { quickAdd = true })
                        CtrlSquareButton(Icons.Outlined.Add, stringResource(R.string.new_event), onClick = { nav.navigate(EditRoute(day = LocalDate.now().toEpochDay())) })
                    }
                },
            )
        }
        item {
            CtrlTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                placeholder = { Text(stringResource(R.string.search_events)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = if (query.isNotEmpty()) ({
                    CtrlSquareButton(Icons.Outlined.Close, stringResource(R.string.clear), onClick = { query = "" }, size = 36.dp, tint = CtrlColors.Muted)
                }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp),
            )
        }
        if (q.isNotEmpty()) {
            searchResults(hits, today) { h -> nav.navigate(EventRoute(h.eventId, h.begin, h.end)) }
        }
        if (q.isEmpty() && all != null && byDay.isEmpty()) {
            item {
                if (q.isEmpty()) {
                    MessageView(
                        Icons.Outlined.EventAvailable, stringResource(R.string.no_upcoming), stringResource(R.string.no_upcoming_body, months),
                        modifier = Modifier.heightIn(min = 260.dp),
                        actionLabel = stringResource(R.string.new_event), onAction = { nav.navigate(EditRoute(day = LocalDate.now().toEpochDay())) },
                    )
                } else {
                    MessageView(Icons.Outlined.SearchOff, stringResource(R.string.no_results), stringResource(R.string.no_results_body, months), modifier = Modifier.heightIn(min = 260.dp))
                }
            }
        }
        byDay.forEach { (day, events) ->
            item(key = "d$day") {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = Gutter + 4.dp, end = Gutter, top = 14.dp, bottom = 4.dp)) {
                    SectionLabel(relativeDay(context, day, long = true).uppercase())
                }
            }
            items(events, key = { "${day}_${it.key}" }) { e ->
                InstanceRow(e, day, Modifier.padding(horizontal = Gutter, vertical = 4.dp)) { nav.navigate(EventRoute(e.eventId, e.begin, e.end)) }
            }
        }
        if (q.isEmpty() && all != null && months < 60) {
            item {
                CtrlSecondaryButton(
                    onClick = { months += 12 },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 14.dp),
                ) { Text(stringResource(R.string.show_more_months)) }
            }
        }
        item { Spacer(Modifier.bottomClearance(0.dp)) }
    }
    if (quickAdd) QuickAddSheet(onDismiss = { quickAdd = false })
}

/** Search results: coming up first (soonest first), then repeating events, then the past (newest first). */
private fun LazyListScope.searchResults(hits: List<SearchHit>?, today: LocalDate, onOpen: (SearchHit) -> Unit) {
    if (hits == null) {
        item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CtrlColors.AccentText) } }
        return
    }
    if (hits.isEmpty()) {
        item {
            MessageView(Icons.Outlined.SearchOff, stringResource(R.string.no_results), stringResource(R.string.no_results_all_body), modifier = Modifier.heightIn(min = 260.dp))
        }
        return
    }
    val asInstance = { h: SearchHit -> Instance(h.eventId, h.begin, h.end, h.title, h.location, h.allDay, h.calendarId, h.color, declined = false) }
    val repeating = hits.filter { it.repeating }
    val single = hits.filter { !it.repeating }
    val upcoming = single.filter { !asInstance(it).lastDay().isBefore(today) }.sortedBy { it.begin }
    val past = single.filter { asInstance(it).lastDay().isBefore(today) }.sortedByDescending { it.begin }
    listOf(R.string.search_upcoming to upcoming, R.string.search_repeating to repeating, R.string.search_past to past).forEach { (label, list) ->
        if (list.isEmpty()) return@forEach
        item(key = "s$label") {
            Column(Modifier.fillMaxWidth().padding(start = Gutter + 4.dp, end = Gutter, top = 14.dp, bottom = 4.dp)) {
                SectionLabel(stringResource(label).uppercase())
            }
        }
        items(list, key = { "h${label}_${it.eventId}" }) { h ->
            val inst = asInstance(h)
            InstanceRow(inst, inst.firstDay(), Modifier.padding(horizontal = Gutter, vertical = 4.dp)) { onOpen(h) }
        }
    }
}
