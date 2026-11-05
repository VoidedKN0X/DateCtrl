package com.vknxworks.datectrl.ui.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.ViewParent
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import java.util.WeakHashMap

/**
 * Extra protection for a window while a lock, PIN or destructive confirmation screen is on it:
 * - other apps' floating windows (chat heads, overlays) are hidden over it (Android 12+, HIDE_OVERLAY_WINDOWS),
 *   so nothing can be drawn on top to trick a tap or read the PIN pad;
 * - it is marked sensitive for accessibility (Android 14+), so only real accessibility tools such as
 *   screen readers can read it, not other apps that use the accessibility service API.
 *
 * Counted per window, so nested screens (a dialog over the lock screen, the lock over a PIN screen) work:
 * the window goes back to normal only when the last one closes. Everything else in the app is unaffected.
 */
object SensitiveWindow {
    private val counts = WeakHashMap<Window, Int>()

    fun acquire(window: Window) {
        val n = (counts[window] ?: 0) + 1
        counts[window] = n
        if (n == 1) apply(window, true)
    }

    fun release(window: Window) {
        val n = (counts[window] ?: 0) - 1
        if (n > 0) {
            counts[window] = n
        } else {
            counts.remove(window)
            apply(window, false)
        }
    }

    private fun apply(window: Window, on: Boolean) {
        runCatching { window.setHideOverlayWindows(on) }
        if (Build.VERSION.SDK_INT >= 34) {
            runCatching {
                window.decorView.setAccessibilityDataSensitive(
                    if (on) View.ACCESSIBILITY_DATA_SENSITIVE_YES else View.ACCESSIBILITY_DATA_SENSITIVE_AUTO,
                )
            }
        }
    }
}

/** Applies [SensitiveWindow] to the window this composable is shown in (a dialog's own window, or the activity's) while [enabled]. */
@Composable
fun SensitiveWindowEffect(enabled: Boolean = true) {
    val view = LocalView.current
    val window = remember(view) { view.hostWindow() }
    DisposableEffect(window, enabled) {
        if (window == null || !enabled) return@DisposableEffect onDispose { }
        SensitiveWindow.acquire(window)
        onDispose { SensitiveWindow.release(window) }
    }
}

private fun View.hostWindow(): Window? {
    var p: Any? = this
    while (p != null) {
        if (p is DialogWindowProvider) return p.window
        p = (p as? View)?.parent ?: (p as? ViewParent)?.parent
    }
    var c: Context? = context
    while (c != null) {
        if (c is Activity) return c.window
        c = (c as? ContextWrapper)?.baseContext
    }
    return null
}
