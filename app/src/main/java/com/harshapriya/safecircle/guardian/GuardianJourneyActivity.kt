package com.harshapriya.safecircle.guardian

import com.harshapriya.safecircle.model.OwnerTimelineEvent

enum class GuardianJourneyStatus { NOT_SHARED, PENDING, ACKNOWLEDGED, CHECK_IN_REQUESTED, RESOLVED, SYNC_FAILED }

data class GuardianJourneyActivity(
    val status: GuardianJourneyStatus,
    val occurredAt: Long? = null,
)

object GuardianJourneyActivityMapper {
    fun map(events: List<OwnerTimelineEvent>, resolved: Boolean, hasInvite: Boolean): GuardianJourneyActivity {
        if (resolved) return GuardianJourneyActivity(GuardianJourneyStatus.RESOLVED)
        val request = events.firstOrNull { it.type == "GUARDIAN_CHECK_IN_REQUESTED" }
        if (request != null) return GuardianJourneyActivity(GuardianJourneyStatus.CHECK_IN_REQUESTED, request.createdAt)
        val acknowledgement = events.firstOrNull { it.type == "GUARDIAN_ACKNOWLEDGED" }
        if (acknowledgement != null) return GuardianJourneyActivity(GuardianJourneyStatus.ACKNOWLEDGED, acknowledgement.createdAt)
        return GuardianJourneyActivity(if (hasInvite) GuardianJourneyStatus.PENDING else GuardianJourneyStatus.NOT_SHARED)
    }
}
