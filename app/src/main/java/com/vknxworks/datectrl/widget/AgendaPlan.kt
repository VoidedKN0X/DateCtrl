package com.vknxworks.datectrl.widget

import com.vknxworks.datectrl.data.calendar.Instance
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** What the agenda widget lists and when it refreshes; plain logic, tested on its own. */
object AgendaPlan {
    /** How many days the widget looks ahead (today included). */
    const val DAYS = 7

    /** At most this many events are listed. */
    const val MAX_EVENTS = 50

    sealed interface Row {
        data class Day(val day: LocalDate) : Row
        data class Event(val instance: Instance, val day: LocalDate) : Row
    }

    /**
     * The widget's rows: a heading per day, then that day's events. Like the Agenda tab, each event is listed under
     * the day it starts, or under today when it started earlier and is still going on.
     */
    fun rows(instances: List<Instance>, today: LocalDate, zone: ZoneId, days: Int = DAYS, max: Int = MAX_EVENTS): List<Row> {
        val last = today.plusDays((days - 1).toLong())
        val listed = instances
            .filter { !it.lastDay(zone).isBefore(today) && !it.firstDay(zone).isAfter(last) }
            .sortedWith(compareBy<Instance>({ maxOf(it.firstDay(zone), today) }, { !it.allDay }, { it.begin }, { it.title }))
            .take(max)
        val out = ArrayList<Row>()
        var current: LocalDate? = null
        for (e in listed) {
            val day = maxOf(e.firstDay(zone), today)
            if (day != current) {
                out += Row.Day(day)
                current = day
            }
            out += Row.Event(e, day)
        }
        return out
    }

    /** A few seconds after the next midnight in [zone], in milliseconds: the widget then moves on to the new day. */
    fun nextMidnight(now: ZonedDateTime): Long =
        now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusSeconds(5).toInstant().toEpochMilli()
}
