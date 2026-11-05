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
 * Extra protection for a window while a lock, PIN, password or destructive confirmation screen is on it:
 * - other apps' floating windows (chat heads, overlays) are hidden over it (Android 12+, HIDE_OVERLAY_WINDOWS),
 *   so nothing can be drawn on top to trick a tap or read the PIN pad;
 * - with `sensitiveData` (only the app-lock windows: lock screen, PIN screens, "Forgot PIN?") it is also marked
 *   sensitive for accessibility (Android 14+), so only real accessibility tools such as screen readers can read
 *   it. Sign-in and password screens don't get that, or password managers that fill through accessibility
 *   would stop working there.
 *
 * Counted per window, so nested screens (a dialog over the lock screen, the lock over a PIN screen) work:
 * the window goes back to normal only when the last one closes. Everything else in the app is unaffected.
 */
object SensitiveWindow {
    // [0]: screens hiding overlays, [1]: of those, the ones also hidden from accessibility.
    private val counts = WeakHashMap<Window, IntArray>()

    fun acquire(window: Window, sensitiveData: Boolean = false) {
        val c = counts.getOrPut(window) { IntArray(2) }
        c[0]++
        if (sensitiveData) c[1]++
        apply(window, c)
    }

    fun release(window: Window, sensitiveData: Boolean = false) {
        val c = counts[window] ?: return
        c[0] = (c[0] - 1).coerceAtLeast(0)
        if (sensitiveData) c[1] = (c[1] - 1).coerceAtLeast(0)
        apply(window, c)
        if (c[0] == 0 && c[1] == 0) counts.remove(window)
    }

    private fun apply(window: Window, c: IntArray) {
        runCatching { window.setHideOverlayWindows(c[0] > 0) }
        if (Build.VERSION.SDK_INT >= 34) {
            runCatching {
                window.decorView.setAccessibilityDataSensitive(
                    if (c[1] > 0) View.ACCESSIBILITY_DATA_SENSITIVE_YES else View.ACCESSIBILITY_DATA_SENSITIVE_AUTO,
                )
            }
        }
    }
}

/**
 * Applies [SensitiveWindow] to the window this composable is shown in (a dialog's own window, or the activity's)
 * while [enabled]. [sensitiveData] only for the app-lock windows; see [SensitiveWindow].
 */
@Composable
fun SensitiveWindowEffect(enabled: Boolean = true, sensitiveData: Boolean = false) {
    val view = LocalView.current
    val window = remember(view) { view.hostWindow() }
    DisposableEffect(window, enabled, sensitiveData) {
        if (window == null || !enabled) return@DisposableEffect onDispose { }
        SensitiveWindow.acquire(window, sensitiveData)
        onDispose { SensitiveWindow.release(window, sensitiveData) }
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
