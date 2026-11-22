package com.vknxworks.datectrl.data.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SharedFilesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun removesFilesFromAnEarlierRunAndOldOnes() {
        val dir = tmp.newFolder("shared")
        val start = 50_000_000L
        val now = start + 10 * 60_000L
        val earlierRun = File(dir, "a.ics").apply { writeText("x"); setLastModified(start - 1_000) }
        val thisRun = File(dir, "b.ics").apply { writeText("x"); setLastModified(start + 60_000) }
        assertEquals(1, SharedFiles.clearLeftovers(dir, start, now))
        assertFalse(earlierRun.exists())
        assertTrue(thisRun.exists())
        // An hour later the file from this run goes too.
        assertEquals(1, SharedFiles.clearLeftovers(dir, start, start + 60_000 + SharedFiles.MAX_AGE_MS + 1))
        assertFalse(thisRun.exists())
    }

    @Test
    fun missingFolderIsFine() {
        assertEquals(0, SharedFiles.clearLeftovers(File(tmp.root, "none"), 0L, 0L))
    }
}
