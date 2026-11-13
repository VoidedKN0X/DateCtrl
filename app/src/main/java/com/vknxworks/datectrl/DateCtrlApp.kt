package com.vknxworks.datectrl

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vknxworks.datectrl.ui.lock.AppLock
import com.vknxworks.datectrl.widget.AgendaWidget

class DateCtrlApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // The app lock: locks after the chosen time away ("Lock again").
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppLock.onForeground(container.vault.enabled, container.settings.settings.value.lockTimeout.millis)
                // Back from another app: calendars may have synced meanwhile.
                container.calendar.start()
                // Keeps the agenda widget's refresh triggers set (they don't survive a restart of the phone).
                AgendaWidget.requestUpdate(this@DateCtrlApp)
            }

            override fun onStop(owner: LifecycleOwner) {
                AppLock.onBackground(container.vault.enabled, container.settings.settings.value.lockTimeout.millis)
            }
        })
    }
}
