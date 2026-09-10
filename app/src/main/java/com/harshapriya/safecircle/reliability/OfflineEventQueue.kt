package com.harshapriya.safecircle.reliability

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class QueuedEvent(
    val id: String,
    val type: String,
    val payload: String,
    val createdAt: Long
)

class OfflineEventQueue(context: Context) {
    private val prefs = context.getSharedPreferences("safecircle_queue", Context.MODE_PRIVATE)

    fun enqueue(type: String, payload: String): QueuedEvent {
        val event = QueuedEvent(UUID.randomUUID().toString(), type, payload, System.currentTimeMillis())
        val current = JSONArray(prefs.getString("events", "[]"))
        current.put(JSONObject().apply {
            put("id", event.id)
            put("type", event.type)
            put("payload", event.payload)
            put("createdAt", event.createdAt)
        })
        prefs.edit().putString("events", current.toString()).apply()
        return event
    }

    fun all(): List<QueuedEvent> {
        val arr = JSONArray(prefs.getString("events", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            QueuedEvent(o.getString("id"), o.getString("type"), o.getString("payload"), o.getLong("createdAt"))
        }
    }

    fun acknowledge(id: String) {
        val remaining = all().filterNot { it.id == id }
        val arr = JSONArray()
        remaining.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id); put("type", e.type); put("payload", e.payload); put("createdAt", e.createdAt)
            })
        }
        prefs.edit().putString("events", arr.toString()).apply()
    }
}
