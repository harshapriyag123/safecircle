package com.harshapriya.safecircle.guardian

import android.content.Context
import com.harshapriya.safecircle.auth.AuthRepository
import com.harshapriya.safecircle.data.SafetyRepository
import com.harshapriya.safecircle.domain.SafetyEngine
import com.harshapriya.safecircle.platform.LocationProvider
import com.harshapriya.safecircle.privacy.SafetyPreferencesRepository
import com.harshapriya.safecircle.security.SafetyCapsuleStore
import com.harshapriya.safecircle.sync.GuardianInviteLink
import com.harshapriya.safecircle.sync.HttpSafeCircleGateway
import com.harshapriya.safecircle.sync.NetworkConfig
import com.harshapriya.safecircle.sync.SessionSyncPayload

class GuardianInviteCoordinator(private val context: Context) {
    suspend fun createForActiveSession(
        role: String = "guardian",
        ttlMinutes: Int = 60
    ): GuardianInviteLink {
        val auth = AuthRepository(context).state()
            ?: throw IllegalStateException("Sign in to SafeCircle before creating a real Guardian link.")
        if (!NetworkConfig.hasBackend) {
            throw IllegalStateException("SafeCircle backend is not configured.")
        }

        val session = SafetyRepository(context).currentSession()
            ?: throw IllegalStateException("Start a Safety Session before creating a session-scoped Guardian link.")
        if (session.resolved) {
            throw IllegalStateException("The current Safety Session is already resolved. Start another session first.")
        }

        val gateway = HttpSafeCircleGateway(NetworkConfig.baseUrl, auth.accessToken)
        val snapshot = SafetyEngine.evaluate(session)
        val location = LocationProvider(context).lastKnown()
        val privacy = SafetyPreferencesRepository(context).privacy()

        gateway.upsertSession(
            SessionSyncPayload(
                id = session.id,
                ownerId = auth.userId,
                mode = session.mode.name,
                destination = session.destinationLabel,
                startedAt = session.startedAt,
                expectedEndAt = session.expectedEndAt,
                lastCheckInAt = session.lastCheckInAt,
                state = snapshot.state.name,
                batteryPercent = session.batteryPercent,
                latitude = location?.latitude,
                longitude = location?.longitude,
                locationAccuracy = location?.accuracyMeters,
                privacyMode = privacy.locationMode.name,
                resolved = session.resolved,
                capsuleJson = SafetyCapsuleStore(context).get(session.id)
            )
        )

        return gateway.createGuardianInvite(
            sessionId = session.id,
            ownerId = auth.userId,
            role = role,
            ttlMinutes = ttlMinutes
        )
    }
}
