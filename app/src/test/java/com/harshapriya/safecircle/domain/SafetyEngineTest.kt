package com.harshapriya.safecircle.domain

import com.harshapriya.safecircle.model.SafetySession
import com.harshapriya.safecircle.model.SafetyState
import com.harshapriya.safecircle.model.SessionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyEngineTest {
    private val now = 1_000_000L

    @Test
    fun normalSessionStaysNormal() {
        val s = SafetySession("1", SessionMode.WALK_HOME, now - 1_000, now + 60_000, now, batteryPercent = 80)
        val result = SafetyEngine.evaluate(s, now)
        assertEquals(SafetyState.NORMAL, result.state)
        assertTrue(result.score >= 80)
    }

    @Test
    fun overdueAndMissedCheckInEscalates() {
        val s = SafetySession(
            "1", SessionMode.WALK_HOME, now - 2_000_000, now - 20 * 60_000, now - 20 * 60_000,
            missedCheckIns = 2, batteryPercent = 10
        )
        val result = SafetyEngine.evaluate(s, now)
        assertEquals(SafetyState.ESCALATED, result.state)
        assertTrue(result.score < 50)
    }
}
