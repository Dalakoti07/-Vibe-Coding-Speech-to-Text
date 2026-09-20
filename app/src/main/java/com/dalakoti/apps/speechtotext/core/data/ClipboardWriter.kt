package com.dalakoti.apps.speechtotext.core.data

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle

/**
 * The only place in the app that touches the clipboard.
 *
 * Nothing calls this automatically. Copying happens when the user presses
 * "Copy to clipboard" and at no other time — a dictation app that silently takes over
 * your clipboard is worse than one that makes you ask.
 */
object ClipboardWriter {

    fun copy(context: Context, text: String, label: String = "Transcript") {
        val clip = ClipData.newPlainText(label, text).apply {
            // Dictated text can contain anything; this suppresses the system preview.
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
        // No toast: Android 13+ shows its own confirmation, and two is one too many.
    }
}
