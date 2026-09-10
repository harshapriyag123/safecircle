package com.harshapriya.safecircle.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.harshapriya.safecircle.data.SafetyRepository

class SafetyActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MARK_SAFE -> SafetyRepository(context).markSafe()
            ACTION_CHECK_IN -> SafetyRepository(context).checkIn()
        }
    }

    companion object {
        const val ACTION_MARK_SAFE = "com.harshapriya.safecircle.action.MARK_SAFE"
        const val ACTION_CHECK_IN = "com.harshapriya.safecircle.action.CHECK_IN"
        const val EXTRA_SESSION_ID = "session_id"
    }
}
