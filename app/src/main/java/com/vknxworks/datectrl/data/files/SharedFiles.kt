package com.vknxworks.datectrl.data.files

import java.io.File

/**
 * The .ics files DateCtrl writes to `cache/shared` to share an event. Each is only needed while the app you
 * shared with reads it; this removes the ones left over.
 */
object SharedFiles {
    /** A shared file older than this is left over even in the same run. */
    const val MAX_AGE_MS = 60 * 60_000L

    /**
     * Deletes the files in [dir] written before [processStart] (left from an earlier run) or more than
     * [MAX_AGE_MS] before [now]. Returns how many were deleted.
     */
    fun clearLeftovers(dir: File, processStart: Long, now: Long = System.currentTimeMillis()): Int {
        var deleted = 0
        dir.listFiles()?.forEach { f ->
            val modified = f.lastModified()
            if (f.isFile && (modified < processStart || now - modified > MAX_AGE_MS) && f.delete()) deleted++
        }
        return deleted
    }
}
