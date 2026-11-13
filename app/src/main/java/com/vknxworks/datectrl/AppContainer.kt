package com.vknxworks.datectrl

import android.content.Context
import com.vknxworks.datectrl.data.calendar.CalendarRepository
import com.vknxworks.datectrl.data.calendar.EventDraft
import com.vknxworks.datectrl.data.calendar.IcsStore
import com.vknxworks.datectrl.widget.AgendaWidget
import com.vknxworks.datectrl.data.lock.LockVault
import com.vknxworks.datectrl.data.settings.AppSettings
import com.vknxworks.datectrl.data.settings.SettingsRepository
import com.vknxworks.datectrl.ui.lock.AppLock
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    /** Background work that outlives a screen. A failure there must never close the app. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, _ -> })

    val settings = SettingsRepository(context, appScope)
    val calendar = CalendarRepository(context, appScope)

    /** Importing and exporting .ics files. */
    val ics by lazy { IcsStore(context) }

    /** The .ics link ImportActivity handed to MainActivity (checked by Android first); taken once. */
    @Volatile var pendingImport: android.net.Uri? = null

    /** What another app (or a reminder notification) asked to open; the main screen picks it up. */
    val pending = MutableStateFlow<Launch?>(null)

    /** The day picked in the month view; kept here so the Agenda's "show in month" and other apps can set it. */
    val selectedDay = MutableStateFlow(java.time.LocalDate.now())

    /** Fields another app filled in for a new event ("Add to calendar"). */
    var prefill: EventDraft? = null

    /** The app lock's PIN and fingerprint key (the shared Ctrl series lock). */
    val vault = LockVault(context, "datectrl")

    suspend fun enableLock(pin: String) {
        AppLock.markUnlocked()
        withContext(Dispatchers.Default) { vault.setUp(pin) }
        // The widget now shows only "Locked".
        AgendaWidget.requestUpdate(context)
    }

    fun disableLock() {
        vault.disable()
        AgendaWidget.requestUpdate(context)
    }

    /**
     * "Forgot PIN?": the PIN can't be recovered. Your events belong to the phone's calendar storage (every app
     * and account shares them), so they are left alone; DateCtrl's own settings are reset and the lock turned off.
     */
    suspend fun forgetEverything() {
        settings.update { AppSettings() }
        vault.disable()
        AppLock.markUnlocked()
        AgendaWidget.requestUpdate(context)
    }

    fun onLocked() {
        vault.close()
    }

    fun onUnlocked() {
        AppLock.markUnlocked()
    }

    init {
        AppLock.onLocked = { onLocked() }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as DateCtrlApp).container
