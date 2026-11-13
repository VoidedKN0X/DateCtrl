package com.vknxworks.datectrl.data.ics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExistingEventsTest {
    private fun ev(uid: String, start: Long, title: String = "") = IcsEvent(uid = uid, title = title, start = start, end = start)

    @Test
    fun sameUidAndStartIsADuplicate() {
        val ex = ExistingEvents.of(listOf(ExistingEvents.Row("abc", 1000, "Lunch")))
        assertTrue(ex.contains(ev("abc", 1000)))
        // The same UID at another time is another occurrence or event.
        assertFalse(ex.contains(ev("abc", 2000)))
        assertFalse(ex.contains(ev("xyz", 1000)))
    }

    @Test
    fun eventsWithoutUidMatchByTitleAndStart() {
        val ex = ExistingEvents.of(listOf(ExistingEvents.Row(null, 1000, "Lunch "), ExistingEvents.Row("", 5000, "Gym")))
        assertTrue(ex.contains(ev("new-uid", 1000, "lunch")))
        assertTrue(ex.contains(ev("other", 5000, "Gym")))
        assertFalse(ex.contains(ev("new-uid", 1000, "Dinner")))
    }

    @Test
    fun aTitleMatchDoesNotCountWhenTheExistingEventHasAUid() {
        val ex = ExistingEvents.of(listOf(ExistingEvents.Row("abc", 1000, "Lunch")))
        assertFalse(ex.contains(ev("def", 1000, "Lunch")))
    }

    @Test
    fun addedEventsCountAsExisting() {
        val ex = ExistingEvents.of(emptyList())
        val e = ev("n", 1)
        assertFalse(ex.contains(e))
        ex.add(e)
        assertTrue(ex.contains(e))
    }
}
