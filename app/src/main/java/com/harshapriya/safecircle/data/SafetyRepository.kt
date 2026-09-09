package com.harshapriya.safecircle.data

import android.content.Context
import com.harshapriya.safecircle.model.Guardian
import com.harshapriya.safecircle.model.SafetySession
import com.harshapriya.safecircle.model.SafetyState
import com.harshapriya.safecircle.model.SessionMode
import java.util.UUID

class SafetyRepository(context: Context) {
    private val prefs = context.getSharedPreferences("safecircle", Context.MODE_PRIVATE)

    fun startSession(mode: SessionMode): SafetySession {
        val now = System.currentTimeMillis()
        return SafetySession(
            id = UUID.randomUUID().toString(),
            mode = mode,
            startedAt = now,
            expectedEndAt = now + mode.minutes * 60_000L,
            lastCheckInAt = now,
        ).also(::saveSession)
    }

    fun currentSession(): SafetySession? {
        val id = prefs.getString("session_id", null) ?: return null
        val mode = runCatching { SessionMode.valueOf(prefs.getString("mode", "WALK_HOME")!!) }.getOrDefault(SessionMode.WALK_HOME)
        val state = runCatching { SafetyState.valueOf(prefs.getString("state", "NORMAL")!!) }.getOrDefault(SafetyState.NORMAL)
        return SafetySession(
            id = id,
            mode = mode,
            startedAt = prefs.getLong("started", 0L),
            expectedEndAt = prefs.getLong("expected", 0L),
            lastCheckInAt = prefs.getLong("checkin", 0L),
            missedCheckIns = prefs.getInt("missed", 0),
            routeDeviation = prefs.getBoolean("route_deviation", false),
            batteryPercent = prefs.getInt("battery", 72),
            state = state,
            resolved = prefs.getBoolean("resolved", false),
        )
    }

    fun checkIn(): SafetySession? = currentSession()?.copy(
        lastCheckInAt = System.currentTimeMillis(), missedCheckIns = 0, state = SafetyState.NORMAL
    )?.also(::saveSession)

    fun simulateConcern(): SafetySession? = currentSession()?.copy(
        missedCheckIns = 1,
        routeDeviation = true,
        batteryPercent = 16,
        state = SafetyState.CONCERN,
    )?.also(::saveSession)

    fun markSafe(): SafetySession? = currentSession()?.copy(
        resolved = true, state = SafetyState.RESOLVED
    )?.also(::saveSession)

    private fun saveSession(s: SafetySession) {
        prefs.edit()
            .putString("session_id", s.id)
            .putString("mode", s.mode.name)
            .putLong("started", s.startedAt)
            .putLong("expected", s.expectedEndAt)
            .putLong("checkin", s.lastCheckInAt)
            .putInt("missed", s.missedCheckIns)
            .putBoolean("route_deviation", s.routeDeviation)
            .putInt("battery", s.batteryPercent)
            .putString("state", s.state.name)
            .putBoolean("resolved", s.resolved)
            .apply()
    }

    fun guardians(): List<Guardian> = listOf(
        Guardian("Sankar", "Primary guardian", "Push + SMS", primary = true),
        Guardian("Priya", "Backup guardian", "Push"),
        Guardian("Family Circle", "Escalation group", "Push + call plan"),
    )
}
