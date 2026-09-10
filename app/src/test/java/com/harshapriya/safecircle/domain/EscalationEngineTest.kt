package com.harshapriya.safecircle.domain

import com.harshapriya.safecircle.model.EscalationAction
import com.harshapriya.safecircle.model.SafetySession
import com.harshapriya.safecircle.model.SessionMode
import org.junit.Assert.assertTrue
import org.junit.Test

class EscalationEngineTest {
    @Test
    fun fifteenMinutesOverdueIncludesCapsuleRelease() {
        val now = 2_000_000L
        val session = SafetySession(
            id = "s1",
            mode = SessionMode.WALK_HOME,
            startedAt = now - 40 * 60_000L,
            expectedEndAt = now - 15 * 60_000L,
            lastCheckInAt = now - 20 * 60_000L,
            missedCheckIns = 1
        )
        val plan = EscalationEngine.plan(session, now)
        assertTrue(plan.any { it.action == EscalationAction.SHARE_SAFETY_CAPSULE })
    }
}
