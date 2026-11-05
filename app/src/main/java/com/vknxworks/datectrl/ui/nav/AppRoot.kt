package com.vknxworks.datectrl.ui.nav

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vknxworks.datectrl.R
import com.vknxworks.datectrl.container
import com.vknxworks.datectrl.ui.components.CtrlBottomBar
import com.vknxworks.datectrl.ui.components.LocalBottomClearance
import com.vknxworks.datectrl.ui.components.TabItem
import com.vknxworks.datectrl.Launch
import com.vknxworks.datectrl.ui.agenda.AgendaScreen
import com.vknxworks.datectrl.ui.calendars.CalendarsScreen
import com.vknxworks.datectrl.ui.common.CalendarPermissionGate
import com.vknxworks.datectrl.ui.event.EditScreen
import com.vknxworks.datectrl.ui.event.EventScreen
import com.vknxworks.datectrl.ui.month.MonthScreen
import com.vknxworks.datectrl.ui.settings.SettingsScreen
import java.time.Instant
import java.time.ZoneId
import com.vknxworks.datectrl.ui.settings.YouScreen
import com.vknxworks.datectrl.ui.theme.CtrlColors

@Composable
fun AppRoot() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        MainScaffold()
    }
}

private data class TopDest(val route: Any, @StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
private fun TopDest.item(): TabItem = TabItem(stringResource(label), icon, selectedIcon)

/** Only content tabs: Settings and About open from the profile picture. */
private val topDestinations = listOf(
    TopDest(MonthRoute, R.string.tab_month, Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    TopDest(AgendaRoute, R.string.tab_agenda, Icons.Outlined.ViewAgenda, Icons.Filled.ViewAgenda),
    TopDest(CalendarsRoute, R.string.tab_calendars, Icons.Outlined.Layers, Icons.Filled.Layers),
)

@Composable
private fun MainScaffold() {
    val context = LocalContext.current
    val c = context.container
    val navController = rememberNavController()
    val navigator = remember(navController) { Navigator(navController) }
    val backStack by navController.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val selectedIndex = topDestinations.indexOfFirst { top -> dest?.hierarchy?.any { it.hasRoute(top.route::class) } == true }

    fun selectTab(i: Int) {
        navController.navigate(topDestinations[i].route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Another app or a reminder asked to show an event or a day, or to add an event.
    val pending by c.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val p = pending ?: return@LaunchedEffect
        // Wait until the screens exist (opening the app from a notification).
        navController.currentBackStackEntryFlow.first()
        c.pending.value = null
        if (!c.calendar.canRead()) return@LaunchedEffect
        when (p) {
            is Launch.ShowEvent -> navigator.navigate(EventRoute(p.id, p.begin ?: 0L, p.end ?: 0L))
            is Launch.ShowDay -> {
                c.selectedDay.value = Instant.ofEpochMilli(p.millis).atZone(ZoneId.systemDefault()).toLocalDate()
                navController.popBackStack(navController.graph.findStartDestination().id, inclusive = false)
                selectTab(0)
            }
            is Launch.Insert -> {
                c.prefill = com.vknxworks.datectrl.ui.event.draftFrom(context, p)
                navigator.navigate(EditRoute(prefill = true))
            }
            is Launch.Edit -> navigator.navigate(EditRoute(p.id, p.begin ?: 0L))
        }
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (maxWidth >= 600.dp) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxHeight().statusBarsPadding(),
                    ) {
                        Spacer(Modifier.weight(1f))
                        topDestinations.forEachIndexed { i, d ->
                            val top = d.item()
                            val selected = i == selectedIndex
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectTab(i) },
                                icon = { Icon(if (selected) top.selectedIcon else top.icon, contentDescription = top.label) },
                                label = { Text(top.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = CtrlColors.OnAccent,
                                    selectedTextColor = CtrlColors.Text,
                                    indicatorColor = CtrlColors.Accent,
                                    unselectedIconColor = CtrlColors.Muted,
                                    unselectedTextColor = CtrlColors.Muted,
                                ),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().navigationBarsPadding()) { AppNavHost(navController) }
                }
            } else {
                PhoneLayout(navController, showTabs = selectedIndex >= 0, selectedIndex = selectedIndex, onSelect = ::selectTab)
            }
        }
    }
}

@Composable
private fun PhoneLayout(navController: NavHostController, showTabs: Boolean, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val density = LocalDensity.current
    var overlayHeight by remember { mutableStateOf(0.dp) }
    CompositionLocalProvider(LocalBottomClearance provides if (showTabs) overlayHeight else 0.dp) {
        Box(Modifier.fillMaxSize()) {
            AppNavHost(navController)
            AnimatedVisibility(
                visible = showTabs,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { overlayHeight = with(density) { it.height.toDp() } },
            ) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                    CtrlBottomBar(items = topDestinations.map { it.item() }, selectedIndex = selectedIndex, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = MonthRoute) {
        composable<MonthRoute> { Gate { MonthScreen() } }
        composable<AgendaRoute> { Gate { AgendaScreen() } }
        composable<CalendarsRoute> { Gate { CalendarsScreen() } }
        composable<YouRoute> { YouScreen() }
        composable<SettingsRoute> { SettingsScreen() }
        composable<EventRoute> { val r = it.toRoute<EventRoute>(); Gate { EventScreen(r.id, r.begin, r.end) } }
        composable<EditRoute> { val r = it.toRoute<EditRoute>(); Gate { EditScreen(r.id, r.begin, r.day, r.prefill) } }
    }
}

@Composable
private fun Gate(content: @Composable () -> Unit) {
    val c = LocalContext.current.container
    CalendarPermissionGate(onGranted = { c.calendar.start() }, content = content)
}
