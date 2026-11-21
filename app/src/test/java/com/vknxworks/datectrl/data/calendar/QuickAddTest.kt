package com.vknxworks.datectrl.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class QuickAddTest {
    // Saturday 21 November 2026
    private val today = LocalDate.of(2026, 11, 21)
    private fun p(s: String, now: LocalTime? = null) = QuickAdd.parse(s, today, now)!!
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test fun englishWeekdayAndTime() {
        val e = p("dentist friday 14:00")
        assertEquals("dentist", e.title)
        assertEquals(LocalDate.of(2026, 11, 27), e.date)
        assertEquals(t(14), e.start)
        assertNull(e.end)
        assertTrue(e.dateGiven)
    }

    @Test fun dutchWeekdayAndTime() {
        val e = p("Tandarts vrijdag om 14u30")
        assertEquals("Tandarts", e.title)
        assertEquals(LocalDate.of(2026, 11, 27), e.date)
        assertEquals(t(14, 30), e.start)
    }

    @Test fun ranges() {
        val a = p("lunch with Anna tomorrow 12:30-13:30")
        assertEquals("lunch with Anna", a.title)
        assertEquals(today.plusDays(1), a.date)
        assertEquals(t(12, 30), a.start)
        assertEquals(t(13, 30), a.end)
        val b = p("vergadering maandag van 14u tot 16u")
        assertEquals("vergadering", b.title)
        assertEquals(LocalDate.of(2026, 11, 23), b.date)
        assertEquals(t(14), b.start)
        assertEquals(t(16), b.end)
        val c = p("gym 2pm to 4pm")
        assertEquals(t(14), c.start)
        assertEquals(t(16), c.end)
        val d = p("call 2-4pm")
        assertEquals(t(14), d.start)
        assertEquals(t(16), d.end)
        val e = p("standup 9:00 - 9:15 monday")
        assertEquals(t(9), e.start)
        assertEquals(t(9, 15), e.end)
        assertEquals("standup", e.title)
    }

    @Test fun dates() {
        assertEquals(LocalDate.of(2026, 12, 3), p("party 3 dec").date)
        assertEquals(LocalDate.of(2026, 12, 3), p("party December 3rd").date)
        assertEquals(LocalDate.of(2027, 3, 3), p("verjaardag 3 maart").date)
        assertEquals(LocalDate.of(2027, 1, 5), p("x 5/1").date)
        assertEquals(LocalDate.of(2027, 12, 3), p("x 3-12-2027").date)
        assertEquals(LocalDate.of(2027, 12, 3), p("x 2027-12-03").date)
        assertEquals(LocalDate.of(2028, 2, 29), p("x 29 februari 2028").date)
        // 21 Nov is today; 20 Nov is next year.
        assertEquals(today, p("x 21 nov").date)
        assertEquals(LocalDate.of(2027, 11, 20), p("x 20 nov").date)
        assertEquals("party", p("party 3 dec").title)
    }

    @Test fun relativeDays() {
        assertEquals(today, p("vandaag boodschappen").date)
        assertEquals(today.plusDays(2), p("overmorgen kapper").date)
        assertEquals(today.plusDays(2), p("haircut day after tomorrow").date)
        assertEquals(today.plusDays(3), p("review in 3 days").date)
        assertEquals(today.plusWeeks(2), p("controle over 2 weken").date)
        // Saturday: "saturday" is today, "next saturday" is a week later.
        assertEquals(today, p("market saturday").date)
        assertEquals(today.plusDays(7), p("market next saturday").date)
        assertEquals("market", p("market next saturday").title)
        assertEquals(LocalDate.of(2026, 11, 23), p("week start next week").date)
    }

    @Test fun allDayWithoutTime() {
        val e = p("Mom's birthday 5 jan")
        assertNull(e.start)
        assertEquals("Mom's birthday", e.title)
    }

    @Test fun timeWithoutDay() {
        assertEquals(today, p("call 18:00", now = t(10)).date)
        assertEquals(today.plusDays(1), p("call 08:00", now = t(10)).date)
        assertFalse(p("call 08:00", now = t(10)).dateGiven)
    }

    @Test fun numbersInTitlesStayInTheTitle() {
        val e = p("room 12 meeting tomorrow")
        assertEquals("room 12 meeting", e.title)
        assertNull(e.start)
        assertEquals(t(12), p("lunch at 12").start)
        assertEquals("lunch", p("lunch at 12").title)
        assertEquals(t(14), p("14.00 tennis").start)
        assertEquals(t(9), p("meeting 9am").start)
        assertEquals(t(0, 30), p("x 12:30am").start)
    }

    @Test fun french() {
        val a = p("Dentiste vendredi à 14h30")
        assertEquals("Dentiste", a.title)
        assertEquals(LocalDate.of(2026, 11, 27), a.date)
        assertEquals(t(14, 30), a.start)
        assertTrue(a.dateGiven)
        val b = p("réunion lundi de 14h à 16h")
        assertEquals("réunion", b.title)
        assertEquals(LocalDate.of(2026, 11, 23), b.date)
        assertEquals(t(14), b.start)
        assertEquals(t(16), b.end)
        assertEquals(today, p("courses aujourd'hui").date)
        assertEquals(today, p("courses aujourd\u2019hui").date)
        assertEquals("courses", p("courses aujourd'hui").title)
        assertEquals(today.plusDays(1), p("coiffeur demain 9h").date)
        assertEquals(t(9), p("coiffeur demain 9h").start)
        assertEquals(today.plusDays(2), p("coiffeur après-demain").date)
        assertEquals(today, p("cinéma ce soir 20:15").date)
        assertEquals("cinéma", p("cinéma ce soir 20:15").title)
        // Saturday today: "samedi prochain" is a week later, "ce samedi" is today.
        assertEquals(today.plusDays(7), p("marché samedi prochain").date)
        assertEquals("marché", p("marché samedi prochain").title)
        assertEquals(today, p("marché ce samedi").date)
        assertEquals(LocalDate.of(2026, 11, 23), p("début la semaine prochaine").date)
        assertEquals("début", p("début la semaine prochaine").title)
        assertEquals(today.plusDays(3), p("contrôle dans 3 jours").date)
        assertEquals(today.plusWeeks(2), p("contrôle dans 2 semaines").date)
        assertEquals(LocalDate.of(2026, 12, 3), p("anniversaire le 3 décembre").date)
        assertEquals("anniversaire", p("anniversaire le 3 décembre").title)
        assertEquals(LocalDate.of(2027, 3, 1), p("fête 1er mars").date)
        assertEquals(LocalDate.of(2027, 8, 15), p("x 15 août").date)
        assertEquals(t(12), p("déjeuner à 12").start)
        assertEquals("déjeuner", p("déjeuner à 12").title)
    }

    @Test fun german() {
        val a = p("Zahnarzt Freitag um 14 Uhr")
        assertEquals("Zahnarzt", a.title)
        assertEquals(LocalDate.of(2026, 11, 27), a.date)
        assertEquals(t(14), a.start)
        assertNull(a.end)
        val b = p("Besprechung Montag von 14 bis 16 Uhr")
        assertEquals("Besprechung", b.title)
        assertEquals(LocalDate.of(2026, 11, 23), b.date)
        assertEquals(t(14), b.start)
        assertEquals(t(16), b.end)
        val c = p("Training am Dienstag 18:30 Uhr")
        assertEquals("Training", c.title)
        assertEquals(LocalDate.of(2026, 11, 24), c.date)
        assertEquals(t(18, 30), c.start)
        val d = p("Anruf 14-16 Uhr")
        assertEquals("Anruf", d.title)
        assertEquals(t(14), d.start)
        assertEquals(t(16), d.end)
        assertEquals(today, p("Einkaufen heute").date)
        assertEquals(today, p("Kino heute Abend").date)
        assertEquals("Kino", p("Kino heute Abend").title)
        assertEquals(today.plusDays(1), p("Friseur morgen 9:00").date)
        assertEquals(today.plusDays(2), p("Friseur übermorgen").date)
        assertEquals(today.plusDays(7), p("Markt nächsten Samstag").date)
        assertEquals("Markt", p("Markt nächsten Samstag").title)
        assertEquals(LocalDate.of(2026, 11, 23), p("Start nächste Woche").date)
        assertEquals(today.plusDays(3), p("Kontrolle in 3 Tagen").date)
        assertEquals(today.plusWeeks(2), p("Kontrolle in 2 Wochen").date)
        assertEquals(LocalDate.of(2027, 3, 3), p("Geburtstag 3. März").date)
        assertEquals("Geburtstag", p("Geburtstag am 3. März").title)
        assertEquals(LocalDate.of(2026, 12, 24), p("Feier 24. Dezember").date)
        // A number without "Uhr" or "um" stays in the title.
        assertEquals("Raum 12 Treffen", p("Raum 12 Treffen morgen").title)
        assertNull(p("Raum 12 Treffen morgen").start)
    }

    @Test fun englishAndDutchUnchangedByTheNewWords() {
        // "mar" stays March, not a French Tuesday.
        assertEquals(LocalDate.of(2027, 3, 3), p("x mar 3").date)
        assertEquals(t(14), p("tandarts vrijdag om 14u").start)
        assertEquals("tandarts", p("tandarts vrijdag om 14u").title)
    }

    @Test fun emptyIsNull() {
        assertNull(QuickAdd.parse("   ", today))
    }
}
