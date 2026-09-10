package com.harshapriya.safecircle.sync

import com.harshapriya.safecircle.reliability.DeliveryReceipt
import com.harshapriya.safecircle.reliability.QueuedEvent

class LocalDemoGateway : SafeCircleGateway {
    override suspend fun upsertSession(payload: SessionSyncPayload): Boolean = true

    override suspend fun upload(events: List<QueuedEvent>): List<String> = events.map { it.id }

    override suspend fun sendGuardianAlert(
        sessionId: String,
        recipientId: String,
        message: String
    ): DeliveryReceipt = DeliveryReceipt(
        eventId = sessionId,
        channel = "demo",
        recipientId = recipientId,
        acceptedAt = System.currentTimeMillis(),
        deliveredAt = System.currentTimeMillis(),
        status = DeliveryReceipt.Status.DELIVERED,
    )

    override suspend fun publishGuardianSnapshot(sessionId: String, payloadJson: String): String =
        "https://safecircle.app/session/$sessionId"
}
