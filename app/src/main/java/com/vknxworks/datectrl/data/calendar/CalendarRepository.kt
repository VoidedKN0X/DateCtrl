package com.vknxworks.datectrl.data.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Android's calendar storage (CalendarContract). DateCtrl keeps no copy of events: every screen reads them from
 * the provider, and [version] goes up whenever anything changes (an edit here, a sync, another app).
 */
class CalendarRepository(private val context: Context, private val scope: CoroutineScope) {
    private val resolver: ContentResolver = context.contentResolver

    private val _calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    val calendars: StateFlow<List<CalendarInfo>> = _calendars

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private var observing = false
    private var reloadJob: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = scheduleReload()
    }

    fun canRead(): Boolean = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    fun canWrite(): Boolean = ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Starts watching the calendars once the permission is granted. */
    fun start() {
        if (!canRead()) return
        if (!observing) {
            runCatching { resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer) }
            observing = true
        }
        scheduleReload(0)
    }

    /** A sync brings many changes at once: they cause one reload. */
    private fun scheduleReload(delayMs: Long = 300) {
        reloadJob?.cancel()
        reloadJob = scope.launch {
            delay(delayMs)
            val list = withContext(Dispatchers.IO) { runCatching { loadCalendars() }.getOrNull() }
            if (list != null) _calendars.value = list
            _version.value++
        }
    }

    private fun loadCalendars(): List<CalendarInfo> {
        val out = ArrayList<CalendarInfo>()
        val proj = arrayOf(
            Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE,
            Calendars.CALENDAR_COLOR, Calendars.VISIBLE, Calendars.CALENDAR_ACCESS_LEVEL,
        )
        resolver.query(Calendars.CONTENT_URI, proj, null, null, "${Calendars.ACCOUNT_NAME}, ${Calendars.CALENDAR_DISPLAY_NAME}")?.use { c ->
            while (c.moveToNext()) {
                out += CalendarInfo(
                    id = c.getLong(0),
                    name = c.getString(1).orEmpty().ifBlank { c.getString(2).orEmpty() },
                    accountName = c.getString(2).orEmpty(),
                    accountType = c.getString(3).orEmpty(),
                    color = if (c.isNull(4)) DEFAULT_COLOR else c.getInt(4),
                    visible = c.getInt(5) != 0,
                    accessLevel = c.getInt(6),
                )
            }
        }
        return out
    }

    // ---------- Reading ----------

    /** The calendars as they are right now (also updates [calendars]). */
    suspend fun calendarsNow(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext emptyList()
        runCatching { loadCalendars() }.getOrNull()?.also { _calendars.value = it } ?: _calendars.value
    }

    /** Every occurrence between [from] and [to] (milliseconds) in visible calendars, sorted by start. */
    suspend fun instances(from: Long, to: Long): List<Instance> = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext emptyList()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val proj = arrayOf(
            Instances.EVENT_ID, Instances.BEGIN, Instances.END, Instances.TITLE, Instances.EVENT_LOCATION,
            Instances.ALL_DAY, Instances.CALENDAR_ID, Instances.DISPLAY_COLOR, Instances.SELF_ATTENDEE_STATUS, Instances.STATUS,
        )
        val out = ArrayList<Instance>()
        runCatching {
            resolver.query(uri, proj, "${Instances.VISIBLE}=1", null, "${Instances.BEGIN} ASC, ${Instances.END} DESC")?.use { c ->
                while (c.moveToNext()) {
                    if (!c.isNull(9) && c.getInt(9) == Events.STATUS_CANCELED) continue
                    out += Instance(
                        eventId = c.getLong(0),
                        begin = c.getLong(1),
                        end = c.getLong(2),
                        title = c.getString(3).orEmpty(),
                        location = c.getString(4).orEmpty(),
                        allDay = c.getInt(5) != 0,
                        calendarId = c.getLong(6),
                        color = if (c.isNull(7)) DEFAULT_COLOR else c.getInt(7),
                        declined = !c.isNull(8) && c.getInt(8) == Attendees.ATTENDEE_STATUS_DECLINED,
                    )
                }
            }
        }
        out
    }

    /** Occurrences on the days [first]..[last] in the phone's time zone (all-day events by their own date). */
    suspend fun instancesForDays(first: LocalDate, last: LocalDate): List<Instance> {
        val zone = ZoneId.systemDefault()
        // A day wider on both sides: all-day events are stored at UTC midnight, which can fall on the day before.
        val from = first.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = last.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        return instances(from, to).filter { !it.lastDay(zone).isBefore(first) && !it.firstDay(zone).isAfter(last) }
    }

    suspend fun event(id: Long): EventDetail? = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext null
        runCatching { loadEvent(id) }.getOrNull()
    }

    private fun loadEvent(id: Long): EventDetail? {
        val proj = arrayOf(
            Events.CALENDAR_ID, Events.CALENDAR_DISPLAY_NAME, Events.DISPLAY_COLOR, Events.CALENDAR_ACCESS_LEVEL,
            Events.ACCOUNT_NAME, Events.ACCOUNT_TYPE, Events.TITLE, Events.EVENT_LOCATION, Events.DESCRIPTION,
            Events.DTSTART, Events.DTEND, Events.DURATION, Events.ALL_DAY, Events.RRULE, Events.EVENT_TIMEZONE,
            Events.ORIGINAL_ID, Events.ORGANIZER, Events.DELETED,
        )
        val base = resolver.query(ContentUris.withAppendedId(Events.CONTENT_URI, id), proj, null, null, null)?.use { c ->
            if (!c.moveToFirst() || c.getInt(17) != 0) return null
            EventDetail(
                id = id,
                calendarId = c.getLong(0),
                calendarName = c.getString(1).orEmpty(),
                color = if (c.isNull(2)) DEFAULT_COLOR else c.getInt(2),
                accessLevel = c.getInt(3),
                accountName = c.getString(4).orEmpty(),
                accountType = c.getString(5).orEmpty(),
                title = c.getString(6).orEmpty(),
                location = c.getString(7).orEmpty(),
                description = c.getString(8).orEmpty(),
                dtStart = c.getLong(9),
                dtEnd = if (c.isNull(10)) null else c.getLong(10),
                duration = c.getString(11),
                allDay = c.getInt(12) != 0,
                rrule = c.getString(13)?.ifBlank { null },
                timeZone = c.getString(14),
                originalId = if (c.isNull(15)) null else c.getLong(15),
                organizer = c.getString(16),
                reminders = emptyList(),
                attendees = emptyList(),
            )
        } ?: return null
        val reminders = ArrayList<Int>()
        resolver.query(Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES, Reminders.METHOD), "${Reminders.EVENT_ID}=?", arrayOf(id.toString()), "${Reminders.MINUTES} ASC")?.use { c ->
            while (c.moveToNext()) {
                val method = c.getInt(1)
                if (method == Reminders.METHOD_ALERT || method == Reminders.METHOD_DEFAULT) {
                    val m = c.getInt(0)
                    if (m !in reminders) reminders += m
                }
            }
        }
        val attendees = ArrayList<Attendee>()
        resolver.query(
            Attendees.CONTENT_URI,
            arrayOf(Attendees.ATTENDEE_NAME, Attendees.ATTENDEE_EMAIL, Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_RELATIONSHIP),
            "${Attendees.EVENT_ID}=?", arrayOf(id.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val email = c.getString(1).orEmpty()
                val name = c.getString(0).orEmpty()
                if (email.isBlank() && name.isBlank()) continue
                attendees += Attendee(name, email, c.getInt(2), c.getInt(3) == Attendees.RELATIONSHIP_ORGANIZER)
            }
        }
        // A lone attendee who is the calendar's own account isn't a guest list.
        val guests = if (attendees.size == 1 && attendees[0].email.equals(base.accountName, ignoreCase = true)) emptyList() else attendees
        return base.copy(reminders = reminders, attendees = guests)
    }

    // ---------- Writing ----------

    /** Values for the time fields of [d]. A repeating event has DURATION and no DTEND (Android's rule). */
    private fun timeValues(d: EventDraft, rrule: String?): ContentValues = ContentValues().apply {
        val start: Long
        val end: Long
        if (d.allDay) {
            start = d.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            end = d.endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            put(Events.EVENT_TIMEZONE, "UTC")
        } else {
            start = d.start
            end = d.end.coerceAtLeast(d.start)
            put(Events.EVENT_TIMEZONE, d.timeZone)
        }
        put(Events.ALL_DAY, if (d.allDay) 1 else 0)
        put(Events.DTSTART, start)
        if (rrule.isNullOrBlank()) {
            put(Events.DTEND, end)
            putNull(Events.DURATION)
            putNull(Events.RRULE)
        } else {
            putNull(Events.DTEND)
            put(Events.DURATION, if (d.allDay) "P${((end - start) / DAY_MS).coerceAtLeast(1)}D" else "P${(end - start) / 1000}S")
            put(Events.RRULE, rrule)
        }
    }

    /** Adds a new event; returns its id, or null when Android refused it. */
    suspend fun insert(d: EventDraft): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val v = timeValues(d, d.rrule).apply {
                put(Events.CALENDAR_ID, d.calendarId)
                put(Events.TITLE, d.title)
                put(Events.EVENT_LOCATION, d.location)
                put(Events.DESCRIPTION, d.description)
                // A repeating event without RRULE has no DTEND set to null; drop the nulls Android rejects on insert.
                if (d.rrule.isNullOrBlank()) { remove(Events.DURATION); remove(Events.RRULE) } else remove(Events.DTEND)
            }
            val uri = resolver.insert(Events.CONTENT_URI, v) ?: return@runCatching null
            val id = ContentUris.parseId(uri)
            setReminders(id, emptyList(), d.reminders)
            id
        }.getOrNull()
    }

    /** Changes a whole event (every occurrence of a repeating one). Only columns that changed are written. */
    suspend fun update(old: EventDetail, d: EventDraft): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val v = ContentValues()
            if (d.title != old.title) v.put(Events.TITLE, d.title)
            if (d.location != old.location) v.put(Events.EVENT_LOCATION, d.location)
            if (d.description != old.description) v.put(Events.DESCRIPTION, d.description)
            if (d.calendarId != old.calendarId) v.put(Events.CALENDAR_ID, d.calendarId)
            val oldTimes = timeValuesOf(old)
            val newTimes = timeValues(d, d.rrule)
            if (!sameTimes(oldTimes, newTimes)) v.putAll(newTimes)
            if (v.size() > 0) {
                val n = resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, old.id), v, null, null)
                if (n <= 0) return@runCatching false
            }
            setReminders(old.id, old.reminders, d.reminders)
            true
        }.getOrDefault(false)
    }

    private fun timeValuesOf(e: EventDetail): ContentValues = ContentValues().apply {
        put(Events.EVENT_TIMEZONE, e.timeZone)
        put(Events.ALL_DAY, if (e.allDay) 1 else 0)
        put(Events.DTSTART, e.dtStart)
        if (e.rrule.isNullOrBlank()) {
            put(Events.DTEND, e.dtEnd ?: (e.dtStart + e.lengthMs))
            putNull(Events.DURATION)
            putNull(Events.RRULE)
        } else {
            putNull(Events.DTEND)
            put(Events.DURATION, e.duration)
            put(Events.RRULE, e.rrule)
        }
    }

    private fun sameTimes(a: ContentValues, b: ContentValues): Boolean {
        val keys = listOf(Events.EVENT_TIMEZONE, Events.ALL_DAY, Events.DTSTART, Events.DTEND, Events.RRULE)
        if (keys.any { a.getAsString(it) != b.getAsString(it) }) return false
        // "P3600S" and "PT1H" are the same length.
        return parseDuration(a.getAsString(Events.DURATION)) == parseDuration(b.getAsString(Events.DURATION))
    }

    /**
     * Changes one occurrence of a repeating event, as Android's "exception": a separate event that replaces
     * the occurrence starting at [instanceBegin]. Returns the new event's id.
     */
    suspend fun updateOccurrence(series: EventDetail, instanceBegin: Long, d: EventDraft): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val v = timeValues(d, null).apply {
                remove(Events.DURATION); remove(Events.RRULE)
                put(Events.ORIGINAL_INSTANCE_TIME, instanceBegin)
                put(Events.TITLE, d.title)
                put(Events.EVENT_LOCATION, d.location)
                put(Events.DESCRIPTION, d.description)
                put(Events.STATUS, Events.STATUS_CONFIRMED)
            }
            val uri = resolver.insert(ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, series.id), v) ?: return@runCatching null
            val id = ContentUris.parseId(uri)
            if (id <= 0) return@runCatching null
            val existing = reminderMinutes(id)
            setReminders(id, existing, d.reminders)
            id
        }.getOrNull()
    }

    private fun reminderMinutes(eventId: Long): List<Int> {
        val out = ArrayList<Int>()
        resolver.query(Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES), "${Reminders.EVENT_ID}=?", arrayOf(eventId.toString()), null)?.use { c ->
            while (c.moveToNext()) out += c.getInt(0)
        }
        return out
    }

    /** Writes only the reminders that changed. */
    private fun setReminders(eventId: Long, old: List<Int>, new: List<Int>) {
        val removed = old.filter { it !in new }.distinct()
        val added = new.filter { it !in old }.distinct()
        for (m in removed) {
            resolver.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID}=? AND ${Reminders.MINUTES}=?", arrayOf(eventId.toString(), m.toString()))
        }
        for (m in added) {
            resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
                put(Reminders.EVENT_ID, eventId)
                put(Reminders.MINUTES, m)
                put(Reminders.METHOD, Reminders.METHOD_ALERT)
            })
        }
    }

    /** Deletes a whole event (every occurrence). Events in local calendars are removed at once. */
    suspend fun delete(e: EventDetail): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val uri = ContentUris.withAppendedId(
                if (e.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL) asSyncAdapter(Events.CONTENT_URI, e.accountName, e.accountType) else Events.CONTENT_URI,
                e.id,
            )
            resolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
    }

    /**
     * Removes a changed occurrence of a repeating event (an exception). Deleting the exception itself would bring
     * the original occurrence back, so it's marked cancelled instead.
     */
    suspend fun cancelException(e: EventDetail): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            resolver.update(
                ContentUris.withAppendedId(Events.CONTENT_URI, e.id),
                ContentValues().apply { put(Events.STATUS, Events.STATUS_CANCELED) }, null, null,
            ) > 0
        }.getOrDefault(false)
    }

    /** Cancels one occurrence of a repeating event; the others stay. */
    suspend fun deleteOccurrence(series: EventDetail, instanceBegin: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val v = ContentValues().apply {
                put(Events.ORIGINAL_INSTANCE_TIME, instanceBegin)
                put(Events.STATUS, Events.STATUS_CANCELED)
            }
            resolver.insert(ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, series.id), v) != null
        }.getOrDefault(false)
    }

    // ---------- Calendars ----------

    fun setVisible(id: Long, visible: Boolean) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                resolver.update(ContentUris.withAppendedId(Calendars.CONTENT_URI, id), ContentValues().apply { put(Calendars.VISIBLE, if (visible) 1 else 0) }, null, null)
            }
        }
    }

    /** A new calendar only on this phone. Returns its id. */
    suspend fun createLocal(name: String, color: Int): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val v = ContentValues().apply {
                put(Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
                put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(Calendars.NAME, name)
                put(Calendars.CALENDAR_DISPLAY_NAME, name)
                put(Calendars.CALENDAR_COLOR, color)
                put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
                put(Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT)
                put(Calendars.VISIBLE, 1)
                put(Calendars.SYNC_EVENTS, 1)
                put(Calendars.CALENDAR_TIME_ZONE, ZoneId.systemDefault().id)
                put(Calendars.ALLOWED_REMINDERS, "${Reminders.METHOD_ALERT}")
            }
            resolver.insert(asSyncAdapter(Calendars.CONTENT_URI, LOCAL_ACCOUNT, CalendarContract.ACCOUNT_TYPE_LOCAL), v)?.let { ContentUris.parseId(it) }
        }.getOrNull().also { scheduleReload(0) }
    }

    /** Renames or recolours a calendar on this phone. */
    suspend fun changeLocal(cal: CalendarInfo, name: String, color: Int): Boolean = withContext(Dispatchers.IO) {
        if (!cal.local) return@withContext false
        runCatching {
            val v = ContentValues()
            if (name != cal.name) v.put(Calendars.CALENDAR_DISPLAY_NAME, name)
            if (color != cal.color) v.put(Calendars.CALENDAR_COLOR, color)
            v.size() == 0 || resolver.update(
                ContentUris.withAppendedId(asSyncAdapter(Calendars.CONTENT_URI, cal.accountName, cal.accountType), cal.id), v, null, null,
            ) > 0
        }.getOrDefault(false)
    }

    /** Deletes a calendar on this phone with all its events. Account calendars are left to their own app. */
    suspend fun deleteLocal(cal: CalendarInfo): Boolean = withContext(Dispatchers.IO) {
        if (!cal.local) return@withContext false
        runCatching {
            resolver.delete(ContentUris.withAppendedId(asSyncAdapter(Calendars.CONTENT_URI, cal.accountName, cal.accountType), cal.id), null, null) > 0
        }.getOrDefault(false).also { scheduleReload(0) }
    }

    suspend fun eventCount(calendarId: Long): Int = withContext(Dispatchers.IO) {
        runCatching {
            resolver.query(Events.CONTENT_URI, arrayOf(Events._ID), "${Events.CALENDAR_ID}=? AND ${Events.DELETED}=0", arrayOf(calendarId.toString()), null)?.use { it.count } ?: 0
        }.getOrDefault(0)
    }

    fun refreshNow() = scheduleReload(0)

    companion object {
        /** The account name of calendars DateCtrl makes on this phone. */
        const val LOCAL_ACCOUNT = "DateCtrl"
        const val DEFAULT_COLOR = 0xFFB48CFF.toInt()

        fun asSyncAdapter(uri: Uri, account: String, type: String): Uri = uri.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, account)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, type)
            .build()
    }
}
