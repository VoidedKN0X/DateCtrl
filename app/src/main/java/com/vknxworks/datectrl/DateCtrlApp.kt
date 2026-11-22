package com.vknxworks.datectrl

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vknxworks.datectrl.reminders.Snoozes
import com.vknxworks.datectrl.ui.lock.AppLock

class DateCtrlApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Snooze alarms are lost when DateCtrl is force-stopped; set any that are missing again (off the main thread).
        val app = this
        Thread { runCatching { Snoozes.rearm(app) } }.start()
        // The app lock: locks after the chosen time away ("Lock again").
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppLock.onForeground(container.vault.enabled, container.settings.settings.value.lockTimeout.millis)
                // Back from another app: calendars may have synced meanwhile.
                container.calendar.start()
            }

            override fun onStop(owner: LifecycleOwner) {
                AppLock.onBackground(container.vault.enabled, container.settings.settings.value.lockTimeout.millis)
            }
        })
    }
}
