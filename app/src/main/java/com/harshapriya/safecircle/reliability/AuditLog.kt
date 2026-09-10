package com.harshapriya.safecircle.reliability

import android.content.Context

data class AuditEvent(
    val timestamp: Long,
    val type: String,
    val sessionId: String?,
    val details: String
)

class AuditLog(context: Context) {
    private val prefs = context.getSharedPreferences("safecircle_audit", Context.MODE_PRIVATE)

    fun append(event: AuditEvent) {
        val current = prefs.getStringSet("events", emptySet())?.toMutableSet() ?: mutableSetOf()
        current += "${event.timestamp}|${event.type}|${event.sessionId.orEmpty()}|${event.details.replace("|", "/")}"
        val bounded = current.toList().takeLast(250).toSet()
        prefs.edit().putStringSet("events", bounded).apply()
    }

    fun recent(limit: Int = 50): List<AuditEvent> =
        prefs.getStringSet("events", emptySet()).orEmpty()
            .mapNotNull { raw ->
                val p = raw.split("|", limit = 4)
                if (p.size != 4) null else AuditEvent(
                    timestamp = p[0].toLongOrNull() ?: return@mapNotNull null,
                    type = p[1],
                    sessionId = p[2].ifBlank { null },
                    details = p[3]
                )
            }
            .sortedByDescending { it.timestamp }
            .take(limit)
}
