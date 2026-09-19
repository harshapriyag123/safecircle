package com.harshapriya.safecircle.sync

import android.content.Context
import com.harshapriya.safecircle.auth.AuthRepository
import com.harshapriya.safecircle.model.SafetySession
import com.harshapriya.safecircle.privacy.SafetyPreferencesRepository

object ResolvedSessionPayloadFactory {
    fun create(session: SafetySession, ownerId: String, privacyMode: String): SessionSyncPayload {
        require(session.resolved) { "Only resolved sessions can use the resolution payload" }
        val resolvedAt = requireNotNull(session.resolvedAt) { "Resolved sessions require resolvedAt" }
        return SessionSyncPayload(
            id = session.id,
            ownerId = ownerId,
            mode = session.mode.name,
            destination = session.destinationLabel,
            startedAt = session.startedAt,
            expectedEndAt = session.expectedEndAt,
            lastCheckInAt = session.lastCheckInAt,
            state = "RESOLVED",
            batteryPercent = session.batteryPercent,
            latitude = null,
            longitude = null,
            locationAccuracy = null,
            privacyMode = privacyMode,
            resolved = true,
            capsuleJson = null,
            resolvedAt = resolvedAt
        )
    }
}

class ResolvedSessionSync(private val context: Context) {
    suspend fun push(session: SafetySession): Boolean {
        val auth = requireNotNull(AuthRepository(context).state()) {
            "Sign in is required to resolve the shared Safety Session"
        }
        require(NetworkConfig.hasBackend) { "SafeCircle backend is not configured" }
        val privacyMode = SafetyPreferencesRepository(context).privacy().locationMode.name
        return HttpSafeCircleGateway(NetworkConfig.baseUrl, auth.accessToken).upsertSession(
            ResolvedSessionPayloadFactory.create(session, auth.userId, privacyMode)
        )
    }
}
