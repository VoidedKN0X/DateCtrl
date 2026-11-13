package com.vknxworks.datectrl.data.ics

/**
 * The events already in a calendar, to skip duplicates when importing: an event is already there when one with
 * the same UID starts at the same time. Events that don't keep a UID (some apps drop it) count as the same when
 * title and start match.
 */
class ExistingEvents private constructor(private val keys: MutableSet<String>) {

    /** One event in the calendar: its UID (if it has one), start and title. */
    data class Row(val uid: String?, val start: Long, val title: String)

    fun contains(e: IcsEvent): Boolean = uidKey(e.uid, e.start) in keys || titleKey(e.title, e.start) in keys

    /** Remembers an event just added, so the same event later in the file is skipped too. */
    fun add(e: IcsEvent) {
        keys += uidKey(e.uid, e.start)
    }

    companion object {
        fun of(rows: List<Row>): ExistingEvents {
            val keys = HashSet<String>(rows.size * 2)
            for (r in rows) {
                val uid = r.uid?.trim()
                if (uid.isNullOrEmpty()) keys += titleKey(r.title, r.start) else keys += uidKey(uid, r.start)
            }
            return ExistingEvents(keys)
        }

        private fun uidKey(uid: String, start: Long) = "u\u0000${uid.trim()}\u0000$start"
        private fun titleKey(title: String, start: Long) = "t\u0000${title.trim().lowercase()}\u0000$start"
    }
}
