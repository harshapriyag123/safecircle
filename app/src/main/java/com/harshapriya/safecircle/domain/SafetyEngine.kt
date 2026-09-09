package com.harshapriya.safecircle.domain

import com.harshapriya.safecircle.model.SafetySession
import com.harshapriya.safecircle.model.SafetySnapshot
import com.harshapriya.safecircle.model.SafetyState

object SafetyEngine {
    fun evaluate(session: SafetySession?, now: Long = System.currentTimeMillis()): SafetySnapshot {
        if (session == null || session.resolved) {
            return SafetySnapshot(92, SafetyState.RESOLVED, listOf("No unresolved safety session"))
        }

        var score = 100
        val reasons = mutableListOf<String>()
        val overdueMinutes = ((now - session.expectedEndAt) / 60_000L).coerceAtLeast(0)

        if (session.batteryPercent < 20) {
            score -= 20
            reasons += "Battery is below 20%"
        } else reasons += "Battery is sufficient"

        if (session.routeDeviation) {
            score -= 22
            reasons += "Route deviation signal detected"
        } else reasons += "Route signal is normal"

        if (session.missedCheckIns > 0) {
            score -= 18 * session.missedCheckIns.coerceAtMost(2)
            reasons += "${session.missedCheckIns} check-in(s) missed"
        } else reasons += "Check-ins are current"

        if (overdueMinutes > 0) {
            score -= when {
                overdueMinutes >= 15 -> 35
                overdueMinutes >= 5 -> 22
                else -> 12
            }
            reasons += "Session is ${overdueMinutes}m overdue"
        } else reasons += "ETA is on schedule"

        val state = when {
            session.missedCheckIns >= 2 || overdueMinutes >= 15 -> SafetyState.ESCALATED
            session.missedCheckIns >= 1 || overdueMinutes >= 8 || session.routeDeviation -> SafetyState.CONCERN
            overdueMinutes >= 3 -> SafetyState.CHECK_IN
            session.batteryPercent < 20 -> SafetyState.ATTENTION
            else -> SafetyState.NORMAL
        }
        return SafetySnapshot(score.coerceIn(0, 100), state, reasons)
    }
}
