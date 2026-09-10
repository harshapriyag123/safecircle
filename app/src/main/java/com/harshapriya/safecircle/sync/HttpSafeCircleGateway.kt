package com.harshapriya.safecircle.sync

import com.harshapriya.safecircle.reliability.DeliveryReceipt
import com.harshapriya.safecircle.reliability.QueuedEvent
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class HttpSafeCircleGateway(
    private val baseUrl: String,
    private val bearerToken: String
) : SafeCircleGateway {

    override suspend fun upload(events: List<QueuedEvent>): List<String> {
        if (events.isEmpty()) return emptyList()

        val body = JSONObject().apply {
            put("events", JSONArray().apply {
                events.forEach { event ->
                    put(JSONObject().apply {
                        put("id", event.id)
                        put("session_id", JSONObject.NULL)
                        put("event_type", event.type)
                        put("payload", parsePayload(event.payload))
                        put("created_at", event.createdAt)
                    })
                }
            })
        }

        val response = post("/v1/events/batch", body)
        val ids = response.optJSONArray("acknowledged_event_ids") ?: return emptyList()
        return (0 until ids.length()).map { ids.getString(it) }
    }

    override suspend fun sendGuardianAlert(
        sessionId: String,
        recipientId: String,
        message: String
    ): DeliveryReceipt {
        val eventId = UUID.randomUUID().toString()
        val body = JSONObject().apply {
            put("events", JSONArray().put(JSONObject().apply {
                put("id", eventId)
                put("session_id", sessionId)
                put("event_type", "GUARDIAN_ALERT_REQUEST")
                put("payload", JSONObject().apply {
                    put("recipient_id", recipientId)
                    put("message", message)
                })
                put("created_at", System.currentTimeMillis())
            }))
        }
        val response = post("/v1/events/batch", body)
        val ack = response.optJSONArray("acknowledged_event_ids")
        val accepted = ack != null && (0 until ack.length()).any { ack.getString(it) == eventId }
        return DeliveryReceipt(
            eventId = eventId,
            channel = "backend_queue",
            recipientId = recipientId,
            acceptedAt = System.currentTimeMillis(),
            deliveredAt = null,
            status = if (accepted) DeliveryReceipt.Status.ACCEPTED else DeliveryReceipt.Status.FAILED
        )
    }

    override suspend fun publishGuardianSnapshot(
        sessionId: String,
        payloadJson: String
    ): String {
        val eventId = UUID.randomUUID().toString()
        val body = JSONObject().apply {
            put("events", JSONArray().put(JSONObject().apply {
                put("id", eventId)
                put("session_id", sessionId)
                put("event_type", "GUARDIAN_SNAPSHOT")
                put("payload", parsePayload(payloadJson))
                put("created_at", System.currentTimeMillis())
            }))
        }
        post("/v1/events/batch", body)
        return baseUrl + "/guardian/"
    }

    private fun parsePayload(raw: String): JSONObject {
        return runCatching { JSONObject(raw) }.getOrElse {
            JSONObject().put("raw", raw)
        }
    }

    private fun post(path: String, json: JSONObject): JSONObject {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken)
            connection.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("SafeCircle API HTTP " + code + ": " + text.take(240))
            }
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}
