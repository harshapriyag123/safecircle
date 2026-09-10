package com.harshapriya.safecircle.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.harshapriya.safecircle.data.SafetyRepository
import com.harshapriya.safecircle.reliability.AuditEvent
import com.harshapriya.safecircle.reliability.AuditLog

class SafetyActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = SafetyRepository(context)
        val current = repo.currentSession() ?: return
        val requestedSessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return

        if (current.id != requestedSessionId || current.resolved) {
            AuditLog(context).append(
                AuditEvent(
                    System.currentTimeMillis(),
                    "STALE_NOTIFICATION_ACTION",
                    requestedSessionId,
                    "ignored because active session changed or resolved"
                )
            )
            return
        }

        when (intent.action) {
            ACTION_MARK_SAFE -> repo.markSafe()
            ACTION_CHECK_IN -> repo.checkIn()
        }
    }

    companion object {
        const val ACTION_MARK_SAFE = "com.harshapriya.safecircle.action.MARK_SAFE"
        const val ACTION_CHECK_IN = "com.harshapriya.safecircle.action.CHECK_IN"
        const val EXTRA_SESSION_ID = "session_id"
    }
}
