package com.harshapriya.safecircle.guardian

import com.harshapriya.safecircle.model.OwnerTimelineEvent

enum class GuardianJourneyStatus { NOT_SHARED, PENDING, ACKNOWLEDGED, CHECK_IN_REQUESTED, RESOLVED, SYNC_FAILED }

data class GuardianJourneyActivity(
    val status: GuardianJourneyStatus,
    val occurredAt: Long? = null,
    val timeline: List<OwnerTimelineEvent> = emptyList(),
)

object GuardianJourneyActivityMapper {
    fun map(events: List<OwnerTimelineEvent>, resolved: Boolean, hasInvite: Boolean): GuardianJourneyActivity {
        val timeline = events
            .filter { it.type in TIMELINE_TYPES }
            .sortedBy { it.createdAt }
        if (resolved) return GuardianJourneyActivity(GuardianJourneyStatus.RESOLVED, timeline = timeline)
        val request = events.firstOrNull { it.type == "GUARDIAN_CHECK_IN_REQUESTED" }
        if (request != null) return GuardianJourneyActivity(GuardianJourneyStatus.CHECK_IN_REQUESTED, request.createdAt, timeline)
        val acknowledgement = events.firstOrNull { it.type == "GUARDIAN_ACKNOWLEDGED" }
        if (acknowledgement != null) return GuardianJourneyActivity(GuardianJourneyStatus.ACKNOWLEDGED, acknowledgement.createdAt, timeline)
        return GuardianJourneyActivity(
            if (hasInvite) GuardianJourneyStatus.PENDING else GuardianJourneyStatus.NOT_SHARED,
            timeline = timeline
        )
    }

    private val TIMELINE_TYPES = setOf(
        "GUARDIAN_ACKNOWLEDGED",
        "GUARDIAN_CHECK_IN_REQUESTED",
        "SESSION_RESOLVED",
    )
}
