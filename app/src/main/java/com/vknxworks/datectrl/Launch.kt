package com.vknxworks.datectrl

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Events

/** What another app (or a notification) asked DateCtrl to do. */
sealed interface Launch {
    /** Show one event; [begin] picks the occurrence of a repeating one. */
    data class ShowEvent(val id: Long, val begin: Long?, val end: Long?) : Launch

    /** Show the day of [millis] in the month view. */
    data class ShowDay(val millis: Long) : Launch

    /** "Add to calendar" with what the other app filled in. */
    data class Insert(
        val title: String?,
        val location: String?,
        val description: String?,
        val begin: Long?,
        val end: Long?,
        val allDay: Boolean,
        val rrule: String?,
        val calendarId: Long?,
    ) : Launch

    data class Edit(val id: Long, val begin: Long?) : Launch

    companion object {
        private const val ITEM_EVENT = "vnd.android.cursor.item/event"
        private const val DIR_EVENT = "vnd.android.cursor.dir/event"

        fun from(intent: Intent?): Launch? {
            intent ?: return null
            val data = intent.data
            return runCatching {
                when (intent.action) {
                    Intent.ACTION_VIEW -> view(intent, data)
                    Intent.ACTION_INSERT -> insert(intent)
                    Intent.ACTION_EDIT -> eventId(data)?.let { Edit(it, intent.longExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME)) }
                        ?: if (intent.type == DIR_EVENT || data == null) insert(intent) else null
                    else -> null
                }
            }.getOrNull()
        }

        private fun view(intent: Intent, data: Uri?): Launch? {
            data ?: return null
            if (data.authority != CalendarContract.AUTHORITY) return null
            val seg = data.pathSegments
            return when {
                seg.size >= 2 && seg[0] == "events" -> seg[1].toLongOrNull()?.takeIf { it > 0 }?.let {
                    ShowEvent(it, intent.longExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME), intent.longExtra(CalendarContract.EXTRA_EVENT_END_TIME))
                }
                seg.size >= 2 && seg[0] == "time" -> seg[1].toLongOrNull()?.let { ShowDay(it) }
                seg.size == 1 && seg[0] == "time" -> ShowDay(System.currentTimeMillis())
                else -> null
            }
        }

        private fun eventId(data: Uri?): Long? {
            data ?: return null
            if (data.authority != CalendarContract.AUTHORITY) return null
            val seg = data.pathSegments
            return if (seg.size >= 2 && seg[0] == "events") seg[1].toLongOrNull()?.takeIf { it > 0 } else null
        }

        private fun insert(intent: Intent): Insert {
            val allDay = intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false) ||
                intent.getBooleanExtra(Events.ALL_DAY, false)
            return Insert(
                title = intent.getStringExtra(Events.TITLE)?.take(500),
                location = intent.getStringExtra(Events.EVENT_LOCATION)?.take(500),
                description = intent.getStringExtra(Events.DESCRIPTION)?.take(10_000),
                begin = intent.longExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME) ?: intent.longExtra(Events.DTSTART),
                end = intent.longExtra(CalendarContract.EXTRA_EVENT_END_TIME) ?: intent.longExtra(Events.DTEND),
                allDay = allDay,
                rrule = intent.getStringExtra(Events.RRULE)?.take(500),
                calendarId = intent.longExtra(Events.CALENDAR_ID),
            )
        }

        /** A long extra; some apps send it as an int or a string. Zero and below count as missing. */
        private fun Intent.longExtra(key: String): Long? {
            @Suppress("DEPRECATION")
            val v = extras?.get(key) ?: return null
            val n = when (v) {
                is Long -> v
                is Int -> v.toLong()
                is String -> v.toLongOrNull()
                else -> null
            }
            return n?.takeIf { it > 0 }
        }

        fun isInsertOrEdit(intent: Intent?): Boolean = intent?.action == Intent.ACTION_INSERT || intent?.action == Intent.ACTION_EDIT

        @Suppress("unused")
        private val types = listOf(ITEM_EVENT, DIR_EVENT)
    }
}
