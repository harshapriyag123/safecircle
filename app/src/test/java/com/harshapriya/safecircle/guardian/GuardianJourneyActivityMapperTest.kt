package com.harshapriya.safecircle.guardian

import com.harshapriya.safecircle.model.OwnerTimelineEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class GuardianJourneyActivityMapperTest {
    @Test
    fun pendingInviteBecomesAcknowledged() {
        val activity = GuardianJourneyActivityMapper.map(
            listOf(OwnerTimelineEvent("GUARDIAN_ACKNOWLEDGED", 100L)), false, true
        )
        assertEquals(GuardianJourneyStatus.ACKNOWLEDGED, activity.status)
        assertEquals(100L, activity.occurredAt)
    }

    @Test
    fun checkInRequestWinsOverAcknowledgement() {
        val activity = GuardianJourneyActivityMapper.map(
            listOf(
                OwnerTimelineEvent("GUARDIAN_CHECK_IN_REQUESTED", 200L),
                OwnerTimelineEvent("GUARDIAN_ACKNOWLEDGED", 100L),
            ), false, true
        )
        assertEquals(GuardianJourneyStatus.CHECK_IN_REQUESTED, activity.status)
        assertEquals(200L, activity.occurredAt)
    }

    @Test
    fun resolvedSessionCannotAppearActive() {
        val activity = GuardianJourneyActivityMapper.map(
            listOf(OwnerTimelineEvent("GUARDIAN_CHECK_IN_REQUESTED", 200L)), true, true
        )
        assertEquals(GuardianJourneyStatus.RESOLVED, activity.status)
    }
}
