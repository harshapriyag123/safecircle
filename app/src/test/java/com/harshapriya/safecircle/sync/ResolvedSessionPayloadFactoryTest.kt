package com.harshapriya.safecircle.sync

import com.harshapriya.safecircle.model.SafetySession
import com.harshapriya.safecircle.model.SafetyState
import com.harshapriya.safecircle.model.SessionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvedSessionPayloadFactoryTest {
    @Test
    fun resolutionPayloadIsCanonicalAndPrivacyRedacted() {
        val session = SafetySession(
            id = "session-judge-demo",
            mode = SessionMode.WALK_HOME,
            startedAt = 1_000L,
            expectedEndAt = 2_000L,
            lastCheckInAt = 1_500L,
            state = SafetyState.RESOLVED,
            resolved = true,
            destinationLabel = "Home",
            resolvedAt = 1_900L
        )

        val payload = ResolvedSessionPayloadFactory.create(
            session,
            ownerId = "sc_owner",
            privacyMode = "PRECISE_ON_ESCALATION"
        )

        assertTrue(payload.resolved)
        assertEquals("RESOLVED", payload.state)
        assertEquals(1_900L, payload.resolvedAt)
        assertNull(payload.latitude)
        assertNull(payload.longitude)
        assertNull(payload.locationAccuracy)
        assertNull(payload.capsuleJson)
    }
}
