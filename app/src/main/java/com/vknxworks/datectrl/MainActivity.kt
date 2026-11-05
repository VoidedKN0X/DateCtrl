package com.vknxworks.datectrl

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.datectrl.ui.lock.AppLockGate
import com.vknxworks.datectrl.ui.lock.lockCovering
import com.vknxworks.datectrl.ui.nav.AppRoot
import com.vknxworks.datectrl.ui.theme.DateCtrlTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) take(intent)
        setContent { CtrlWindow(this) { AppRoot() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        take(intent)
    }

    private fun take(intent: Intent?) {
        Launch.from(intent)?.let { container.pending.value = it }
    }
}

/**
 * The Ctrl window: follows the phone's dark theme, applies "Hide in screenshots" / "Hide in recent apps",
 * and shows nothing of the app while the app lock is locked.
 */
@Composable
fun CtrlWindow(activity: ComponentActivity, content: @Composable () -> Unit) {
    val c = activity.container
    val dark = isSystemInDarkTheme()
    DisposableEffect(dark) {
        val bars = if (dark) SystemBarStyle.dark(DARK_BAR) else SystemBarStyle.light(LIGHT_BAR, DARK_BAR)
        activity.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        onDispose { }
    }
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    // "Hide in screenshots": FLAG_SECURE (also blanks the Recents preview). Always on while a lock or PIN screen shows.
    val secure = settings.hideScreenshots || lockCovering(c.vault)
    // "Hide in recent apps": a blank Recents card. Always with the app lock on.
    val blankRecents = settings.hideRecents || c.vault.enabled
    LaunchedEffect(secure, blankRecents) {
        if (secure) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        activity.setRecentsScreenshotEnabled(!blankRecents)
    }
    DateCtrlTheme(dark) {
        AppLockGate { content() }
    }
}

/** The Ctrl / Signal backgrounds behind the status and navigation bars: dark #08080A, light #F5F5F1. */
private val DARK_BAR = android.graphics.Color.rgb(8, 8, 10)
private val LIGHT_BAR = android.graphics.Color.rgb(245, 245, 241)
