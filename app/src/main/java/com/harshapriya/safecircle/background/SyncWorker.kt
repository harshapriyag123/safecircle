package com.harshapriya.safecircle.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.harshapriya.safecircle.auth.AccountRepository
import com.harshapriya.safecircle.auth.AuthRepository
import com.harshapriya.safecircle.data.SafetyRepository
import com.harshapriya.safecircle.domain.SafetyEngine
import com.harshapriya.safecircle.platform.LocationProvider
import com.harshapriya.safecircle.privacy.SafetyPreferencesRepository
import com.harshapriya.safecircle.reliability.OfflineEventQueue
import com.harshapriya.safecircle.sync.HttpSafeCircleGateway
import com.harshapriya.safecircle.sync.LocalDemoGateway
import com.harshapriya.safecircle.sync.NetworkConfig
import com.harshapriya.safecircle.sync.SafeCircleGateway
import com.harshapriya.safecircle.sync.SessionSyncPayload
import com.harshapriya.safecircle.sync.SyncCoordinator
import com.harshapriya.safecircle.security.SafetyCapsuleStore

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val authToken = AuthRepository(applicationContext).state()?.accessToken
            val token = authToken ?: NetworkConfig.demoToken
            val gateway: SafeCircleGateway =
                if (NetworkConfig.hasBackend && token.isNotBlank()) {
                    HttpSafeCircleGateway(NetworkConfig.baseUrl, token)
                } else {
                    LocalDemoGateway()
                }

            val repo = SafetyRepository(applicationContext)
            val session = repo.currentSession()
            if (session != null) {
                val snapshot = SafetyEngine.evaluate(session)
                val location = LocationProvider(applicationContext).lastKnown()
                val privacy = SafetyPreferencesRepository(applicationContext).privacy()

                gateway.upsertSession(
                    SessionSyncPayload(
                        id = session.id,
                        ownerId = AccountRepository(applicationContext).stableUserId(),
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
                        capsuleJson = SafetyCapsuleStore(applicationContext).get(session.id),
                        resolvedAt = null
                    )
                )
            }

            SyncCoordinator(
                queue = OfflineEventQueue(applicationContext),
                gateway = gateway
            ).flush()

            Result.success()
        }.getOrElse {
            if (NetworkConfig.hasBackend) Result.retry() else Result.success()
        }
    }
}
