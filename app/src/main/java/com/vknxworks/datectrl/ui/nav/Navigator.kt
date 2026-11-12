package com.vknxworks.datectrl.ui.nav

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController

/** One place that knows where things open. */
open class Navigator(val nav: NavHostController) {

    fun navigate(route: Any) = nav.navigate(route) { launchSingleTop = true }

    open fun back() {
        nav.popBackStack()
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator") }
