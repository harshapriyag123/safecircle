package com.harshapriya.safecircle.domain

import com.harshapriya.safecircle.model.EscalationAction
import com.harshapriya.safecircle.model.SafetySession

data class EscalationStep(
    val minute: Int,
    val action: EscalationAction,
    val reason: String,
)

object EscalationEngine {
    fun plan(session: SafetySession, now: Long = System.currentTimeMillis()): List<EscalationStep> {
        val overdue = ((now - session.expectedEndAt) / 60_000L).coerceAtLeast(0).toInt()
        val steps = mutableListOf<EscalationStep>()

        if (overdue >= 0 && session.missedCheckIns > 0) {
            steps += EscalationStep(0, EscalationAction.PROMPT_USER, "A check-in was missed")
        }
        if (overdue >= 5) {
            steps += EscalationStep(5, EscalationAction.NOTIFY_PRIMARY_GUARDIAN, "Session is at least 5 minutes overdue")
        }
        if (overdue >= 10) {
            steps += EscalationStep(10, EscalationAction.NOTIFY_BACKUP_GUARDIAN, "Primary escalation has not resolved the session")
        }
        if (overdue >= 15) {
            steps += EscalationStep(15, EscalationAction.SHARE_SAFETY_CAPSULE, "Pre-authorized release threshold reached")
        }
        return steps
    }
}
