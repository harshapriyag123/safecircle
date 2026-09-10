package com.harshapriya.safecircle.sync

import com.harshapriya.safecircle.reliability.DeliveryReceipt
import com.harshapriya.safecircle.reliability.QueuedEvent

/**
 * Contract for production cloud sync. Android code depends on this interface,
 * allowing REST/Firebase/Supabase implementations without changing domain logic.
 */
interface SafeCircleGateway {
    suspend fun upsertSession(payload: SessionSyncPayload): Boolean
    suspend fun createGuardianInvite(sessionId: String, ownerId: String, role: String, ttlMinutes: Int): GuardianInviteLink
    suspend fun upload(events: List<QueuedEvent>): List<String>
    suspend fun sendGuardianAlert(sessionId: String, recipientId: String, message: String): DeliveryReceipt
    suspend fun publishGuardianSnapshot(sessionId: String, payloadJson: String): String
}
