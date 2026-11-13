package com.vknxworks.datectrl

import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.vknxworks.datectrl.ui.common.CalendarPermissionGate
import com.vknxworks.datectrl.ui.event.EditScreen
import com.vknxworks.datectrl.ui.event.draftFrom
import com.vknxworks.datectrl.ui.nav.LocalNavigator
import com.vknxworks.datectrl.ui.nav.Navigator

/**
 * "Add to calendar" and "Edit event" for other apps. It returns the saved event to the app that asked, then
 * closes, so you end up back where you were.
 */
class EditActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val launch = Launch.from(intent)
        if (launch !is Launch.Insert && launch !is Launch.Edit) {
            finish()
            return
        }
        if (savedInstanceState == null && launch is Launch.Insert) container.prefill = draftFrom(this, launch)
        setContent {
            CtrlWindow(this) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val navigator = remember(navController) { ExitNavigator(navController) { done(null) } }
                    CompositionLocalProvider(LocalNavigator provides navigator) {
                        CalendarPermissionGate(onGranted = { container.calendar.start() }) {
                            when (launch) {
                                is Launch.Insert -> EditScreen(-1L, 0L, Long.MIN_VALUE, prefill = true, onDone = ::done)
                                is Launch.Edit -> EditScreen(launch.id, launch.begin ?: 0L, Long.MIN_VALUE, prefill = false, onDone = ::done)
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }

    /** Closes with the saved event (or nothing, when cancelled). */
    private fun done(saved: Long?) {
        container.prefill = null
        if (saved != null) setResult(RESULT_OK, Intent().setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, saved)))
        else setResult(RESULT_CANCELED)
        finish()
    }
}

/** The editor's back button closes the activity. */
private class ExitNavigator(nav: androidx.navigation.NavHostController, private val onExit: () -> Unit) : Navigator(nav) {
    override fun back() = onExit()
}
