package com.harshapriya.safecircle.guardian

import android.content.Context
import com.harshapriya.safecircle.auth.AuthRepository
import com.harshapriya.safecircle.sync.HttpSafeCircleGateway
import com.harshapriya.safecircle.sync.NetworkConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GuardianTimelineCoordinator(private val context: Context) {
    suspend fun activity(sessionId: String, resolved: Boolean, hasInvite: Boolean): GuardianJourneyActivity =
        withContext(Dispatchers.IO) {
            val auth = AuthRepository(context).state()
                ?: throw IllegalStateException("Sign in to refresh Guardian activity.")
            val events = HttpSafeCircleGateway(NetworkConfig.baseUrl, auth.accessToken).fetchSessionEvents(sessionId)
            GuardianJourneyActivityMapper.map(events, resolved, hasInvite)
        }
}
