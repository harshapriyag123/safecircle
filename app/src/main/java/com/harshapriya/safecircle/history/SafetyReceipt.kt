package com.harshapriya.safecircle.history

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SafetyReceipt {
    fun text(entry: SafetyHistoryEntry): String {
        val formatter = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
        val started = formatter.format(Date(entry.startedAt))
        val ended = entry.endedAt?.let { formatter.format(Date(it)) } ?: "Active"
        return buildString {
            appendLine("🛡 SafeCircle Safety Receipt")
            appendLine()
            appendLine("Session: " + entry.mode.replace('_', ' '))
            appendLine("Started: " + started)
            appendLine("Ended: " + ended)
            appendLine("Outcome: " + entry.outcome)
            appendLine()
            append("Made it safe. Someone noticed.")
        }
    }

    fun share(context: Context, entry: SafetyHistoryEntry) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "SafeCircle Safety Receipt")
            putExtra(Intent.EXTRA_TEXT, text(entry))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ContextCompat.startActivity(
            context,
            Intent.createChooser(intent, "Share Safety Receipt").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            null
        )
    }
}
