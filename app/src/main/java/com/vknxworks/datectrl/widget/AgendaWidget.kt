package com.vknxworks.datectrl.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.view.View
import android.widget.RemoteViews
import com.vknxworks.datectrl.EditActivity
import com.vknxworks.datectrl.MainActivity
import com.vknxworks.datectrl.R
import com.vknxworks.datectrl.container
import com.vknxworks.datectrl.ui.common.dayTimes
import com.vknxworks.datectrl.ui.common.relativeDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The agenda widget: today's and the coming days' events from the calendars you show, with + to add an event.
 * Tapping an event opens it in DateCtrl; tapping the date opens today.
 *
 * It refreshes when anything in the calendar storage changes (a JobScheduler content trigger, so nothing runs
 * while nothing changes), just after midnight, when the time or time zone changes, and every few hours as a
 * fallback. Not exported: only the system's widget service and DateCtrl itself can send it broadcasts.
 * While the app lock is on it shows only "Locked".
 */
class AgendaWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MIDNIGHT, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> refreshAsync(context)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refreshAsync(context)

    override fun onDisabled(context: Context) = AgendaRefresh.cancel(context)

    private fun refreshAsync(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { update(context.applicationContext) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MIDNIGHT = "com.vknxworks.datectrl.widget.MIDNIGHT"
        private const val ITEM_EVENT = "vnd.android.cursor.item/event"
        private const val DIR_EVENT = "vnd.android.cursor.dir/event"

        /** Redraws every agenda widget (if there are any), and keeps the refresh triggers set. */
        suspend fun update(context: Context, arm: Boolean = true) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, AgendaWidget::class.java))
            if (ids.isEmpty()) {
                AgendaRefresh.cancel(context)
                return
            }
            if (arm) AgendaRefresh.schedule(context, force = false)
            AgendaRefresh.scheduleMidnight(context)
            manager.updateAppWidget(ids, build(context))
        }

        /** For DateCtrl itself (the app lock was turned on or off, for example). */
        fun requestUpdate(context: Context) {
            context.container.appScope.launch(Dispatchers.IO) { runCatching { update(context.applicationContext) } }
        }

        private suspend fun build(context: Context): RemoteViews {
            val pkg = context.packageName
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val views = RemoteViews(pkg, R.layout.widget_agenda)
            val locale = Locale.getDefault()
            views.setTextViewText(R.id.widget_day, today.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale).uppercase(locale))
            // "13 November" (no year), in the order and words of the phone's language.
            val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMMM")
            views.setTextViewText(R.id.widget_date, today.format(DateTimeFormatter.ofPattern(pattern, locale)))
            val openToday = openDay(context, System.currentTimeMillis())
            views.setOnClickPendingIntent(R.id.widget_header, openToday)
            views.setOnClickPendingIntent(R.id.widget_message, openToday)
            views.setOnClickPendingIntent(R.id.widget_add, addEvent(context))

            val c = context.container
            val message: String? = when {
                // With the app lock on, nothing from your calendar shows on the home screen.
                c.vault.enabled -> context.getString(R.string.widget_locked)
                !c.calendar.canRead() -> context.getString(R.string.widget_no_access)
                else -> null
            }
            if (message != null) return views.showMessage(message)

            val rows = AgendaPlan.rows(c.calendar.instancesForDays(today, today.plusDays((AgendaPlan.DAYS - 1).toLong())), today, zone)
            if (rows.isEmpty()) return views.showMessage(context.getString(R.string.widget_empty))

            val items = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(false).setViewTypeCount(2)
            rows.forEachIndexed { i, row ->
                val item = when (row) {
                    is AgendaPlan.Row.Day -> RemoteViews(pkg, R.layout.widget_agenda_day).apply {
                        setTextViewText(R.id.day_label, relativeDay(context, row.day).uppercase(locale))
                    }
                    is AgendaPlan.Row.Event -> eventRow(context, row)
                }
                items.addItem(i.toLong(), item)
            }
            views.setViewVisibility(R.id.widget_message, View.GONE)
            views.setViewVisibility(R.id.widget_list, View.VISIBLE)
            views.setRemoteAdapter(R.id.widget_list, items.build())
            views.setPendingIntentTemplate(R.id.widget_list, eventTemplate(context))
            return views
        }

        private fun RemoteViews.showMessage(text: String): RemoteViews {
            setTextViewText(R.id.widget_message, text)
            setViewVisibility(R.id.widget_message, View.VISIBLE)
            setViewVisibility(R.id.widget_list, View.GONE)
            return this
        }

        private fun eventRow(context: Context, row: AgendaPlan.Row.Event): RemoteViews {
            val e = row.instance
            return RemoteViews(context.packageName, R.layout.widget_agenda_event).apply {
                setInt(R.id.event_bar, "setColorFilter", e.color or 0xFF000000.toInt())
                setTextViewText(R.id.event_title, e.title.ifBlank { context.getString(R.string.no_title) })
                if (e.declined) setTextColor(R.id.event_title, context.getColor(R.color.widget_muted))
                val sub = listOf(dayTimes(context, e.begin, e.end, e.allDay, row.day), e.location).filter { it.isNotBlank() }.joinToString(" · ")
                setTextViewText(R.id.event_time, sub)
                // Filled into the list's template: opens this occurrence in DateCtrl.
                setOnClickFillInIntent(
                    R.id.event_root,
                    Intent()
                        .setDataAndType(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.eventId), ITEM_EVENT)
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end),
                )
            }
        }

        /**
         * The list's click template. A list item can only add its event to it, so it must be mutable; it's explicit
         * (DateCtrl's own MainActivity, as an "open event" request) so nothing else can be started with it.
         */
        private fun eventTemplate(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 21,
            Intent(Intent.ACTION_VIEW).setClass(context, MainActivity::class.java),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun openDay(context: Context, millis: Long): PendingIntent = PendingIntent.getActivity(
            context, 22,
            Intent(Intent.ACTION_VIEW, Uri.parse("content://${CalendarContract.AUTHORITY}/time/$millis")).setClass(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun addEvent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 23,
            Intent(Intent.ACTION_INSERT).setType(DIR_EVENT).setClass(context, EditActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** The widget's refresh triggers: a content-trigger job on the calendar storage and an alarm after midnight. */
object AgendaRefresh {
    private const val JOB_CHANGES = 4101

    /** Arms the content-trigger job (one-shot by design; [AgendaRefreshJob] arms it again after each run). */
    fun schedule(context: Context, force: Boolean) {
        if (!hasWidgets(context)) return
        val js = context.getSystemService(JobScheduler::class.java) ?: return
        if (!force && js.getPendingJob(JOB_CHANGES) != null) return
        runCatching {
            js.schedule(
                JobInfo.Builder(JOB_CHANGES, ComponentName(context, AgendaRefreshJob::class.java))
                    .addTriggerContentUri(JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                    // A sync brings many changes at once: they cause one refresh.
                    .setTriggerContentUpdateDelay(2_000)
                    .setTriggerContentMaxDelay(15_000)
                    .build(),
            )
        }
    }

    /** Just after the next midnight (inexact, no special permission; it may come a little late in Doze). */
    fun scheduleMidnight(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC, AgendaPlan.nextMidnight(ZonedDateTime.now()), midnightIntent(context)) }
    }

    fun hasWidgets(context: Context): Boolean = runCatching {
        AppWidgetManager.getInstance(context)?.getAppWidgetIds(ComponentName(context, AgendaWidget::class.java))?.isNotEmpty() == true
    }.getOrDefault(false)

    fun cancel(context: Context) {
        context.getSystemService(JobScheduler::class.java)?.cancel(JOB_CHANGES)
        context.getSystemService(AlarmManager::class.java)?.cancel(midnightIntent(context))
    }

    private fun midnightIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 24,
        Intent(AgendaWidget.ACTION_MIDNIGHT).setClass(context, AgendaWidget::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Runs when the calendar storage changed: redraws the widget, then waits for the next change. */
class AgendaRefreshJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            try {
                runCatching { AgendaWidget.update(applicationContext, arm = false) }
            } finally {
                jobFinished(params, false)
                AgendaRefresh.schedule(applicationContext, force = true)
            }
        }
        return true
    }

    /** Stopped early (for example the system needed the memory): run again later. */
    override fun onStopJob(params: JobParameters): Boolean = true

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
