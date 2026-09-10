package com.harshapriya.safecircle.guardian

import com.harshapriya.safecircle.domain.SafetyEngine
import com.harshapriya.safecircle.model.SafetySession
import org.json.JSONObject

object GuardianSessionPublisher {
    fun publicSnapshot(session: SafetySession, includeLocationLabel: String? = null): String {
        val snapshot = SafetyEngine.evaluate(session)
        return JSONObject().apply {
            put("sessionId", session.id)
            put("mode", session.mode.name)
            put("expectedEndAt", session.expectedEndAt)
            put("state", snapshot.state.name)
            put("readiness", snapshot.score)
            put("batteryPercent", session.batteryPercent)
            put("location", includeLocationLabel ?: JSONObject.NULL)
            put("resolved", session.resolved)
        }.toString()
    }
}
