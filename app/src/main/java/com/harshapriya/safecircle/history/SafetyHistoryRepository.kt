package com.harshapriya.safecircle.history

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SafetyHistoryEntry(
    val sessionId: String,
    val mode: String,
    val startedAt: Long,
    val endedAt: Long?,
    val outcome: String
)

class SafetyHistoryRepository(context: Context) {
    private val prefs = context.getSharedPreferences("safecircle_history", Context.MODE_PRIVATE)

    fun recordStarted(sessionId: String, mode: String, startedAt: Long) {
        val current = all().filterNot { it.sessionId == sessionId }
        save(current + SafetyHistoryEntry(sessionId, mode, startedAt, null, "ACTIVE"))
    }

    fun recordResolved(sessionId: String, endedAt: Long, outcome: String) {
        val next = all().map {
            if (it.sessionId == sessionId) it.copy(endedAt = endedAt, outcome = outcome) else it
        }
        save(next)
    }

    fun all(): List<SafetyHistoryEntry> {
        val arr = JSONArray(prefs.getString("entries", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SafetyHistoryEntry(
                sessionId = o.getString("sessionId"),
                mode = o.getString("mode"),
                startedAt = o.getLong("startedAt"),
                endedAt = if (o.isNull("endedAt")) null else o.getLong("endedAt"),
                outcome = o.getString("outcome")
            )
        }.sortedByDescending { it.startedAt }
    }

    private fun save(entries: List<SafetyHistoryEntry>) {
        val arr = JSONArray()
        entries.takeLast(100).forEach { e ->
            arr.put(JSONObject().apply {
                put("sessionId", e.sessionId)
                put("mode", e.mode)
                put("startedAt", e.startedAt)
                put("endedAt", e.endedAt ?: JSONObject.NULL)
                put("outcome", e.outcome)
            })
        }
        prefs.edit().putString("entries", arr.toString()).apply()
    }
}
