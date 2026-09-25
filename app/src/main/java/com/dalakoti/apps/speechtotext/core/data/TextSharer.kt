package com.dalakoti.apps.speechtotext.core.data

import android.content.Context
import android.content.Intent

/**
 * Hands a transcript to another app through the system share sheet.
 *
 * Like ClipboardWriter, this only runs when the user presses "Share". The chooser lists
 * every installed app that accepts plain text; which one gets the transcript is the user's
 * call, not ours.
 */
object TextSharer {

    fun share(context: Context, text: String, title: String = "Share transcript") {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        // Always the chooser, never a remembered default: each transcript may go somewhere else.
        val chooser = Intent.createChooser(send, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
