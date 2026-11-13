package com.vknxworks.datectrl.data.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import com.vknxworks.datectrl.data.ics.ExistingEvents
import com.vknxworks.datectrl.data.ics.IcsDuration
import com.vknxworks.datectrl.data.ics.IcsEvent
import com.vknxworks.datectrl.data.ics.IcsParseResult
import com.vknxworks.datectrl.data.ics.IcsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Importing .ics files into a calendar and exporting events from one, through Android's calendar storage.
 * Imports only add events: nothing already in a calendar is changed or deleted.
 */
class IcsStore(context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    /** Reads an .ics file another app (or the file picker) handed over. */
    suspend fun read(uri: Uri): IcsParseResult = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.use { IcsParser.parse(it) } ?: throw IOException("Can't open the file")
    }

    /** UID, start and title of every event in a calendar, to find duplicates. */
    suspend fun existing(calendarId: Long): ExistingEvents = withContext(Dispatchers.IO) { loadExisting(calendarId) }

    private fun loadExisting(calendarId: Long): ExistingEvents {
        val rows = ArrayList<ExistingEvents.Row>()
        runCatching {
            resolver.query(
                Events.CONTENT_URI, arrayOf(Events.UID_2445, Events.DTSTART, Events.TITLE),
                "${Events.CALENDAR_ID}=? AND ${Events.DELETED}=0", arrayOf(calendarId.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) rows += ExistingEvents.Row(c.getString(0), c.getLong(1), c.getString(2).orEmpty())
            }
        }
        return ExistingEvents.of(rows)
    }

    data class ImportResult(val added: Int, val skipped: Int, val failed: Int)

    /** Adds [events] to [calendar], skipping the ones already in it. Only for calendars you can write to. */
    suspend fun import(calendar: CalendarInfo, events: List<IcsEvent>, onProgress: (Int) -> Unit): ImportResult = withContext(Dispatchers.IO) {
        if (!calendar.writable) return@withContext ImportResult(0, 0, events.size)
        val existing = loadExisting(calendar.id)
        var added = 0
        var skipped = 0
        var failed = 0
        events.forEachIndexed { i, e ->
            when {
                existing.contains(e) -> skipped++
                runCatching { insert(calendar.id, e) }.getOrDefault(false) -> {
                    added++
                    existing.add(e)
                }
                else -> failed++
            }
            if (i % 10 == 0) onProgress(i)
        }
        onProgress(events.size)
        ImportResult(added, skipped, failed)
    }

    private fun insert(calendarId: Long, e: IcsEvent): Boolean {
        val v = ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, e.title)
            put(Events.EVENT_LOCATION, e.location)
            put(Events.DESCRIPTION, e.description)
            put(Events.UID_2445, e.uid)
            putTimes(e)
            // Repeating events use DURATION and no DTEND (Android's rule).
            if (e.repeating) {
                put(Events.RRULE, e.rrule)
                put(Events.DURATION, androidDuration(e))
                if (e.exdates.isNotEmpty()) put(Events.EXDATE, e.exdates.sorted().joinToString(",") { UTC_STAMP.format(Instant.ofEpochMilli(it)) })
            } else {
                put(Events.DTEND, e.end)
            }
        }
        val uri = resolver.insert(Events.CONTENT_URI, v) ?: return false
        val id = ContentUris.parseId(uri)
        if (id <= 0) return false
        setReminders(id, emptyList(), e.reminders)
        if (e.repeating) for (o in e.overrides) runCatching { insertOverride(id, o) }
        return true
    }

    private fun ContentValues.putTimes(e: IcsEvent) {
        put(Events.ALL_DAY, if (e.allDay) 1 else 0)
        put(Events.DTSTART, e.start)
        put(Events.EVENT_TIMEZONE, if (e.allDay) "UTC" else e.timeZone)
    }

    /** A changed occurrence, as Android's exception to the series. */
    private fun insertOverride(seriesId: Long, o: IcsEvent) {
        val at = o.recurrenceId ?: return
        val v = ContentValues().apply {
            put(Events.ORIGINAL_INSTANCE_TIME, at)
            putTimes(o)
            put(Events.DTEND, o.end)
            put(Events.TITLE, o.title)
            put(Events.EVENT_LOCATION, o.location)
            put(Events.DESCRIPTION, o.description)
            put(Events.STATUS, Events.STATUS_CONFIRMED)
        }
        val uri = resolver.insert(ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, seriesId), v) ?: return
        val id = ContentUris.parseId(uri)
        if (id <= 0) return
        setReminders(id, reminderMinutes(id), o.reminders)
    }

    private fun androidDuration(e: IcsEvent): String =
        if (e.allDay) "P${(e.lengthMs / DAY_MS).coerceAtLeast(1)}D" else "P${e.lengthMs / 1000}S"

    private fun reminderMinutes(eventId: Long): List<Int> {
        val out = ArrayList<Int>()
        resolver.query(Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES), "${Reminders.EVENT_ID}=?", arrayOf(eventId.toString()), null)?.use { c ->
            while (c.moveToNext()) out += c.getInt(0)
        }
        return out
    }

    private fun setReminders(eventId: Long, old: List<Int>, new: List<Int>) {
        for (m in old.filter { it !in new }.distinct()) {
            resolver.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID}=? AND ${Reminders.MINUTES}=?", arrayOf(eventId.toString(), m.toString()))
        }
        for (m in new.filter { it !in old }.distinct()) {
            resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
                put(Reminders.EVENT_ID, eventId)
                put(Reminders.MINUTES, m)
                put(Reminders.METHOD, Reminders.METHOD_ALERT)
            })
        }
    }

    // ---------- Export ----------

    private class Row(
        val id: Long, val title: String, val location: String, val description: String, val start: Long, val end: Long?,
        val duration: String?, val allDay: Boolean, val rrule: String?, val exdate: String?, val timeZone: String?, val uid: String?,
        val originalId: Long?, val originalTime: Long?, val cancelled: Boolean, val syncId: String?, val originalSyncId: String?,
    )

    private val proj = arrayOf(
        Events._ID, Events.TITLE, Events.EVENT_LOCATION, Events.DESCRIPTION, Events.DTSTART, Events.DTEND, Events.DURATION,
        Events.ALL_DAY, Events.RRULE, Events.EXDATE, Events.EVENT_TIMEZONE, Events.UID_2445, Events.ORIGINAL_ID,
        Events.ORIGINAL_INSTANCE_TIME, Events.STATUS, Events._SYNC_ID, Events.ORIGINAL_SYNC_ID,
    )

    private fun rows(selection: String, args: Array<String>): List<Row> {
        val out = ArrayList<Row>()
        resolver.query(Events.CONTENT_URI, proj, "($selection) AND ${Events.DELETED}=0", args, "${Events.DTSTART} ASC")?.use { c ->
            fun str(i: Int) = if (c.isNull(i)) null else c.getString(i)
            fun long(i: Int) = if (c.isNull(i)) null else c.getLong(i)
            while (c.moveToNext()) {
                out += Row(
                    id = c.getLong(0), title = str(1).orEmpty(), location = str(2).orEmpty(), description = str(3).orEmpty(),
                    start = c.getLong(4), end = long(5), duration = str(6), allDay = c.getInt(7) != 0, rrule = str(8)?.ifBlank { null },
                    exdate = str(9), timeZone = str(10), uid = str(11)?.ifBlank { null }, originalId = long(12), originalTime = long(13),
                    cancelled = !c.isNull(14) && c.getInt(14) == Events.STATUS_CANCELED, syncId = str(15), originalSyncId = str(16),
                )
            }
        }
        return out
    }

    private fun remindersFor(ids: Collection<Long>): Map<Long, List<Int>> {
        if (ids.isEmpty()) return emptyMap()
        val wanted = ids.toHashSet()
        val out = HashMap<Long, MutableList<Int>>()
        val sel = if (ids.size <= 200) "${Reminders.EVENT_ID} IN (${ids.joinToString(",")})" else null
        resolver.query(Reminders.CONTENT_URI, arrayOf(Reminders.EVENT_ID, Reminders.MINUTES, Reminders.METHOD), sel, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (id !in wanted) continue
                val method = c.getInt(2)
                if (method != Reminders.METHOD_ALERT && method != Reminders.METHOD_DEFAULT) continue
                val list = out.getOrPut(id) { ArrayList() }
                val m = c.getInt(1)
                if (m !in list) list += m
            }
        }
        return out
    }

    private fun toIcs(r: Row, calendarId: Long, reminders: List<Int>): IcsEvent {
        val length = r.end?.let { (it - r.start).coerceAtLeast(0) } ?: IcsDuration.parse(r.duration)?.coerceAtLeast(0) ?: if (r.allDay) DAY_MS else 0L
        val zone = if (r.allDay) "UTC" else r.timeZone?.takeIf { runCatching { ZoneId.of(it) }.isSuccess } ?: ZoneId.systemDefault().id
        return IcsEvent(
            uid = r.uid ?: ("datectrl-" + UUID.nameUUIDFromBytes("$calendarId:${r.id}".toByteArray(Charsets.UTF_8))),
            title = r.title,
            location = r.location,
            description = r.description,
            allDay = r.allDay,
            start = r.start,
            end = r.start + if (r.allDay) length.coerceAtLeast(DAY_MS) else length,
            timeZone = zone,
            rrule = r.rrule,
            exdates = if (r.rrule != null) IcsParser.parseAndroidExdates(r.exdate, ZoneId.systemDefault()) else emptyList(),
            reminders = reminders.sorted(),
            recurrenceId = r.originalTime,
        )
    }

    /**
     * Series, single events and changed occurrences: changed occurrences go with their series (cancelled ones as
     * skipped dates), and ones whose series is missing become events of their own.
     */
    private fun assemble(rows: List<Row>, calendarId: Long): List<IcsEvent> {
        val reminders = remindersFor(rows.map { it.id })
        val masters = rows.filter { it.originalId == null && it.originalSyncId == null && !it.cancelled }
        val byId = masters.associateBy { it.id }
        val bySync = masters.filter { it.syncId != null }.associateBy { it.syncId!! }
        val events = LinkedHashMap<Long, IcsEvent>()
        for (m in masters) events[m.id] = toIcs(m, calendarId, reminders[m.id].orEmpty())
        val loose = ArrayList<IcsEvent>()
        for (x in rows) {
            if (x.originalId == null && x.originalSyncId == null) continue
            val master = x.originalId?.let { byId[it] } ?: x.originalSyncId?.let { bySync[it] }
            val series = master?.let { events[it.id] }
            if (master == null || series == null || !series.repeating || x.originalTime == null) {
                if (!x.cancelled) loose += toIcs(x, calendarId, reminders[x.id].orEmpty()).copy(recurrenceId = null, rrule = null, exdates = emptyList())
                continue
            }
            events[master.id] = if (x.cancelled) {
                series.copy(exdates = (series.exdates + x.originalTime).distinct())
            } else {
                series.copy(overrides = series.overrides + toIcs(x, calendarId, reminders[x.id].orEmpty()).copy(uid = series.uid, rrule = null, exdates = emptyList()))
            }
        }
        return (events.values + loose).sortedBy { it.start }
    }

    /**
     * The events of a calendar, or only those with an occurrence between [from] and [to] (milliseconds). A
     * repeating event with an occurrence in the range is exported whole.
     */
    suspend fun exportCalendar(calendarId: Long, from: Long?, to: Long?): List<IcsEvent> = withContext(Dispatchers.IO) {
        val all = rows("${Events.CALENDAR_ID}=?", arrayOf(calendarId.toString()))
        if (from == null || to == null) return@withContext assemble(all, calendarId)
        val inRange = HashSet<Long>()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        resolver.query(uri, arrayOf(Instances.EVENT_ID), "${Instances.CALENDAR_ID}=?", arrayOf(calendarId.toString()), null)?.use { c ->
            while (c.moveToNext()) inRange += c.getLong(0)
        }
        // An occurrence of a changed event brings its series along.
        val byId = all.associateBy { it.id }
        val bySync = all.filter { it.syncId != null }.associateBy { it.syncId!! }
        val wanted = HashSet<Long>()
        for (id in inRange) {
            val r = byId[id] ?: continue
            val master = r.originalId?.let { byId[it] } ?: r.originalSyncId?.let { bySync[it] }
            wanted += master?.id ?: r.id
        }
        val keep = all.filter { r ->
            r.id in wanted || (r.originalId ?: r.originalSyncId?.let { bySync[it]?.id })?.let { it in wanted } == true
        }
        assemble(keep, calendarId)
    }

    /** One event for sharing: the whole series for a repeating one, with its changed occurrences. */
    suspend fun exportEvent(eventId: Long): List<IcsEvent> = withContext(Dispatchers.IO) {
        val own = rows("${Events._ID}=?", arrayOf(eventId.toString())).firstOrNull() ?: return@withContext emptyList()
        val calendarId = resolver.query(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), arrayOf(Events.CALENDAR_ID), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L
        if (own.originalId != null || own.originalSyncId != null) {
            // A changed occurrence on its own: shared as a single event.
            val e = toIcs(own, calendarId, remindersFor(listOf(own.id))[own.id].orEmpty())
            return@withContext listOf(e.copy(recurrenceId = null, rrule = null, exdates = emptyList()))
        }
        val exceptions = if (own.rrule != null) rows("${Events.ORIGINAL_ID}=?", arrayOf(eventId.toString())) else emptyList()
        assemble(listOf(own) + exceptions, calendarId)
    }

    companion object {
        private const val DAY_MS = 86_400_000L
        private val UTC_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    }
}
