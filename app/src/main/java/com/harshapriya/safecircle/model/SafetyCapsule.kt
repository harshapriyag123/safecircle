package com.harshapriya.safecircle.model

data class SafetyCapsule(
    val sessionId: String,
    val createdAt: Long,
    val expiresAt: Long,
    val destinationLabel: String?,
    val lastKnownLocationLabel: String?,
    val batteryPercent: Int?,
    val guardianInstructions: String?,
    val preciseLocationAllowedAfterEscalation: Boolean,
)

object SafetyCapsuleFactory {
    fun fromSession(
        session: SafetySession,
        destinationLabel: String? = null,
        lastKnownLocationLabel: String? = null,
        guardianInstructions: String? = null,
    ): SafetyCapsule = SafetyCapsule(
        sessionId = session.id,
        createdAt = System.currentTimeMillis(),
        expiresAt = session.expectedEndAt + 24 * 60 * 60_000L,
        destinationLabel = destinationLabel,
        lastKnownLocationLabel = lastKnownLocationLabel,
        batteryPercent = session.batteryPercent,
        guardianInstructions = guardianInstructions,
        preciseLocationAllowedAfterEscalation = true,
    )
}
