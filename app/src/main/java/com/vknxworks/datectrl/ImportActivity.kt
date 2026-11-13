package com.vknxworks.datectrl

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.Bundle

/**
 * Opens an .ics file another app opens with DateCtrl ("Open with") or shares with it. Its only job is to let
 * Android check the link: on Android 15+ the system refuses to start it when the app that sent the link can't
 * read that file itself (`requireContentUriPermissionFromCaller` in the manifest), so no app can make DateCtrl
 * read a file the app couldn't read on its own. Only content:// links are taken (from the intent's data or its
 * clip data, which is what Android checks). Then it hands the link to [MainActivity], which shows the import.
 */
class ImportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            else -> null
        }
        if (uri != null && uri.scheme.equals("content", ignoreCase = true)) {
            // MainActivity only imports the link it was told about here, never one another app sends it directly.
            container.pendingImport = uri
            val open = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClass(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            open.clipData = ClipData.newRawUri("", uri)
            runCatching { startActivity(open) }
        }
        finish()
    }
}
