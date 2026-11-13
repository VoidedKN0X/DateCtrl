package com.vknxworks.datectrl.widget

import com.vknxworks.datectrl.data.calendar.Instance
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class AgendaPlanTest {
    private val zone = ZoneId.of("Europe/Brussels")
    private val today = LocalDate.of(2026, 11, 13)

    private fun at(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private fun timed(title: String, day: LocalDate, from: Int, to: Int, endDay: LocalDate = day) =
        Instance(1, at(day, from), at(endDay, to), title, "", false, 1, 0, false)
    private fun allDay(title: String, first: LocalDate, days: Long = 1) = Instance(
        2, first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), first.plusDays(days).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        title, "", true, 1, 0, false,
    )

    private fun titles(rows: List<AgendaPlan.Row>) = rows.map {
        when (it) {
            is AgendaPlan.Row.Day -> "# ${it.day}"
            is AgendaPlan.Row.Event -> it.instance.title
        }
    }

    @Test
    fun groupsByDayWithAllDayFirst() {
        val rows = AgendaPlan.rows(
            listOf(
                timed("Lunch", today, 12, 13),
                allDay("Birthday", today),
                timed("Gym", today.plusDays(2), 18, 19),
                timed("Breakfast", today, 8, 9),
            ),
            today, zone,
        )
        assertEquals(listOf("# 2026-11-13", "Birthday", "Breakfast", "Lunch", "# 2026-11-15", "Gym"), titles(rows))
    }

    @Test
    fun ongoingEventsShowUnderToday() {
        val rows = AgendaPlan.rows(listOf(timed("Trip", today.minusDays(2), 9, 17, endDay = today.plusDays(1)), allDay("Week", today.minusDays(3), 7)), today, zone)
        assertEquals(listOf("# 2026-11-13", "Week", "Trip"), titles(rows))
    }

    @Test
    fun onlyTheComingWeekAndAtMostTheLimit() {
        val list = (0 until 20).map { timed("E$it", today.plusDays(it.toLong()), 9, 10) }
        val rows = AgendaPlan.rows(list, today, zone)
        assertEquals(AgendaPlan.DAYS, rows.count { it is AgendaPlan.Row.Event })
        val capped = AgendaPlan.rows((0 until 80).map { timed("E$it", today, 9, 10) }, today, zone, max = 50)
        assertEquals(50, capped.count { it is AgendaPlan.Row.Event })
        assertEquals(1, capped.count { it is AgendaPlan.Row.Day })
    }

    @Test
    fun pastEventsAreLeftOut() {
        val rows = AgendaPlan.rows(listOf(timed("Yesterday", today.minusDays(1), 9, 10), allDay("Was", today.minusDays(1))), today, zone)
        assertEquals(emptyList<String>(), titles(rows))
    }

    @Test
    fun nextMidnight() {
        val now = ZonedDateTime.of(LocalDateTime.of(2026, 11, 13, 22, 30), zone)
        assertEquals(LocalDate.of(2026, 11, 14).atStartOfDay(zone).plusSeconds(5).toInstant().toEpochMilli(), AgendaPlan.nextMidnight(now))
        // The night the clocks go back is 25 hours long; midnight is still midnight.
        val dst = ZonedDateTime.of(LocalDateTime.of(2026, 10, 24, 23, 59), zone)
        assertEquals(LocalDateTime.of(2026, 10, 25, 0, 0, 5).atZone(zone).toInstant().toEpochMilli(), AgendaPlan.nextMidnight(dst))
        // Just after midnight: the next one, not the one that just passed.
        val early = ZonedDateTime.of(LocalDateTime.of(2026, 11, 14, 0, 0, 1), zone)
        assertEquals(LocalDate.of(2026, 11, 15).atStartOfDay(zone).plusSeconds(5).toInstant().toEpochMilli(), AgendaPlan.nextMidnight(early))
    }
}
