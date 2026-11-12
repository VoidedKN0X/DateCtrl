package com.vknxworks.datectrl.ui.month

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.datectrl.R
import com.vknxworks.datectrl.container
import com.vknxworks.datectrl.data.calendar.Instance
import com.vknxworks.datectrl.ui.common.asColor
import com.vknxworks.datectrl.ui.common.dateLong
import com.vknxworks.datectrl.ui.common.dayNarrow
import com.vknxworks.datectrl.ui.common.dayShort
import com.vknxworks.datectrl.ui.common.firstDay
import com.vknxworks.datectrl.ui.common.monthName
import com.vknxworks.datectrl.ui.common.relativeDay
import com.vknxworks.datectrl.ui.common.weekDays
import com.vknxworks.datectrl.ui.common.weekNumber
import com.vknxworks.datectrl.ui.components.CtrlSquareButton
import com.vknxworks.datectrl.ui.components.MessageView
import com.vknxworks.datectrl.ui.components.SectionLabel
import com.vknxworks.datectrl.ui.components.TabTitle
import com.vknxworks.datectrl.ui.components.bottomClearance
import com.vknxworks.datectrl.ui.event.InstanceRow
import com.vknxworks.datectrl.ui.nav.EditRoute
import com.vknxworks.datectrl.ui.nav.EventRoute
import com.vknxworks.datectrl.ui.nav.LocalNavigator
import com.vknxworks.datectrl.ui.theme.CtrlColors
import com.vknxworks.datectrl.ui.theme.CtrlShapes
import com.vknxworks.datectrl.ui.theme.CtrlType
import com.vknxworks.datectrl.ui.theme.Gutter
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private const val PAGES = 2401
private const val CENTER = 1200

@Composable
fun MonthScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val selected by c.selectedDay.collectAsStateWithLifecycle()
    val version by c.calendar.version.collectAsStateWithLifecycle()
    val base = remember { YearMonth.now() }
    val first = firstDay(settings.weekStart)

    fun pageOf(d: LocalDate) = (CENTER + ChronoUnit.MONTHS.between(base, YearMonth.from(d)).toInt()).coerceIn(0, PAGES - 1)
    val pager = rememberPagerState(initialPage = pageOf(selected)) { PAGES }
    val shownMonth = base.plusMonths((pager.currentPage - CENTER).toLong())

    // Picking a day in another month (Today, another app) turns the page; turning the page picks a day in it.
    LaunchedEffect(selected) {
        val target = pageOf(selected)
        if (target != pager.currentPage) pager.animateScrollToPage(target)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            val month = base.plusMonths((page - CENTER).toLong())
            val day = c.selectedDay.value
            if (YearMonth.from(day) != month) {
                c.selectedDay.value = if (month == YearMonth.now()) LocalDate.now() else month.atDay(1)
            }
        }
    }

    val dayEvents by produceState(emptyList<Instance>(), selected, version) {
        value = c.calendar.instancesForDays(selected, selected)
    }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            TabTitle(
                monthName(shownMonth.year, shownMonth.monthValue),
                label = shownMonth.year.toString(),
                trailing = {
                    CtrlSquareButton(Icons.Outlined.Today, stringResource(R.string.today), onClick = { c.selectedDay.value = LocalDate.now() })
                    CtrlSquareButton(Icons.Outlined.Add, stringResource(R.string.new_event), onClick = { nav.navigate(EditRoute(day = selected.toEpochDay())) })
                },
            )
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f)) {
                    if (settings.weekNumbers) Spacer(Modifier.width(WEEK_COL))
                    weekDays(first).forEach { d ->
                        Text(
                            dayNarrow(d), style = CtrlType.LabelStrong,
                            color = if (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY) CtrlColors.Muted else CtrlColors.Text,
                            textAlign = TextAlign.Center, modifier = Modifier.weight(1f).semantics { contentDescription = dayShort(d) },
                        )
                    }
                }
            }
        }
        item {
            HorizontalPager(pager, Modifier.fillMaxWidth().height(GRID_HEIGHT + 8.dp), beyondViewportPageCount = 1) { page ->
                val month = base.plusMonths((page - CENTER).toLong())
                MonthGrid(month, first, selected, settings.weekNumbers, version) { c.selectedDay.value = it }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = Gutter, end = 12.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionLabel(relativeDay(context, selected).uppercase())
                    Text(dateLong(selected), style = MaterialTheme.typography.titleMedium, color = CtrlColors.Text)
                }
                CtrlSquareButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.previous_day), onClick = { c.selectedDay.value = selected.minusDays(1) }, size = 40.dp, tint = CtrlColors.Muted)
                Spacer(Modifier.width(8.dp))
                CtrlSquareButton(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.next_day), onClick = { c.selectedDay.value = selected.plusDays(1) }, size = 40.dp, tint = CtrlColors.Muted)
            }
        }
        if (dayEvents.isEmpty()) {
            item {
                MessageView(
                    Icons.Outlined.EventAvailable, stringResource(R.string.no_events_day), null,
                    modifier = Modifier.heightIn(min = 180.dp),
                    actionLabel = stringResource(R.string.new_event),
                    onAction = { nav.navigate(EditRoute(day = selected.toEpochDay())) },
                )
            }
        } else {
            items(dayEvents, key = { it.key }) { e ->
                InstanceRow(e, selected, Modifier.padding(horizontal = Gutter, vertical = 4.dp)) { nav.navigate(EventRoute(e.eventId, e.begin, e.end)) }
            }
        }
        item { Spacer(Modifier.bottomClearance(0.dp)) }
    }
}

private val CELL_HEIGHT = 50.dp
private val GRID_HEIGHT = CELL_HEIGHT * 6
private val WEEK_COL = 26.dp

/** Six weeks starting on the week of the 1st, with a dot per event (up to three) under each day. */
@Composable
private fun MonthGrid(month: YearMonth, firstDay: DayOfWeek, selected: LocalDate, weekNumbers: Boolean, version: Int, onPick: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val start = month.atDay(1).let { d -> d.minusDays(((d.dayOfWeek.value - firstDay.value + 7) % 7).toLong()) }
    val end = start.plusDays(41)
    val events by produceState(emptyMap<LocalDate, List<Int>>(), month, firstDay, version) {
        val list = c.calendar.instancesForDays(start, end)
        val map = HashMap<LocalDate, MutableList<Int>>()
        for (e in list) {
            var d = maxOf(e.firstDay(), start)
            val last = minOf(e.lastDay(), end)
            while (!d.isAfter(last)) {
                map.getOrPut(d) { mutableListOf() } += e.color
                d = d.plusDays(1)
            }
        }
        value = map
    }
    val today = LocalDate.now()
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
        for (week in 0 until 6) {
            Row(Modifier.fillMaxWidth().height(CELL_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                if (weekNumbers) {
                    Text(
                        weekNumber(start.plusDays(week * 7L + 3)).toString(), style = CtrlType.Data.copy(fontSize = 10.sp),
                        color = CtrlColors.Inactive, modifier = Modifier.width(WEEK_COL),
                    )
                }
                for (dow in 0 until 7) {
                    val day = start.plusDays(week * 7L + dow)
                    val inMonth = YearMonth.from(day) == month
                    val isSel = day == selected
                    val isToday = day == today
                    val colors = events[day].orEmpty()
                    Column(
                        Modifier.weight(1f).height(CELL_HEIGHT).padding(2.dp).clip(CtrlShapes.Chip)
                            .then(
                                when {
                                    isSel -> Modifier.background(CtrlColors.Accent)
                                    isToday -> Modifier.border(1.dp, CtrlColors.Accent, CtrlShapes.Chip)
                                    else -> Modifier
                                },
                            )
                            .clickable { onPick(day) }
                            .semantics { contentDescription = dateLong(day); this.selected = isSel },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            day.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                            color = when {
                                isSel -> CtrlColors.OnAccent
                                isToday -> CtrlColors.AccentText
                                inMonth -> CtrlColors.Text
                                else -> CtrlColors.Inactive
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(Modifier.height(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            colors.take(3).forEach { col ->
                                Box(Modifier.size(5.dp).clip(CtrlShapes.Dot).background(if (isSel) CtrlColors.OnAccent else if (inMonth) col.asColor() else col.asColor().copy(alpha = 0.4f)))
                            }
                        }
                    }
                }
            }
        }
    }
}
