package com.harshapriya.safecircle.data

import android.content.Context
import com.harshapriya.safecircle.background.SafetyScheduler
import com.harshapriya.safecircle.history.SafetyHistoryRepository
import com.harshapriya.safecircle.model.Guardian
import com.harshapriya.safecircle.model.SafetySession
import com.harshapriya.safecircle.model.SafetyState
import com.harshapriya.safecircle.model.SessionMode
import com.harshapriya.safecircle.platform.BatteryMonitor
import com.harshapriya.safecircle.platform.LocationProvider
import com.harshapriya.safecircle.reliability.AuditEvent
import com.harshapriya.safecircle.reliability.AuditLog
import java.util.UUID

class SafetyRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("safecircle", Context.MODE_PRIVATE)
    private val battery = BatteryMonitor(context)
    private val location = LocationProvider(context)
    private val scheduler = SafetyScheduler(context)
    private val audit = AuditLog(context)
    private val history = SafetyHistoryRepository(context)

    fun startSession(mode: SessionMode): SafetySession = startSession(mode, mode.minutes, null)

    fun startSession(mode: SessionMode, durationMinutes: Int, destinationLabel: String?): SafetySession {
        val now = System.currentTimeMillis()
        val safeDuration = durationMinutes.coerceIn(5, 24 * 60)
        val session = SafetySession(
            id = UUID.randomUUID().toString(),
            mode = mode,
            startedAt = now,
            expectedEndAt = now + safeDuration * 60_000L,
            lastCheckInAt = now,
            batteryPercent = battery.currentPercent(),
            destinationLabel = destinationLabel?.trim()?.takeIf { it.isNotBlank() }
        )
        saveSession(session)
        scheduler.schedule(session.id, session.expectedEndAt)
        audit.append(AuditEvent(now, "SESSION_STARTED", session.id, "mode=${mode.name}"))
        history.recordStarted(session.id, mode.name, now)
        location.lastKnown()?.let {
            prefs.edit()
                .putString("last_location", "${it.latitude},${it.longitude}")
                .putFloat("last_accuracy", it.accuracyMeters)
                .apply()
        }
        return session
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
            batteryPercent = battery.currentPercent(),
            state = state,
            resolved = prefs.getBoolean("resolved", false),
            destinationLabel = prefs.getString("destination", null)
        )
    }

    fun updateEta(addMinutes: Int): SafetySession? {
        val current = currentSession() ?: return null
        scheduler.cancel(current.id)
        val updated = current.copy(expectedEndAt = current.expectedEndAt + addMinutes * 60_000L)
        saveSession(updated)
        scheduler.schedule(updated.id, updated.expectedEndAt)
        audit.append(AuditEvent(System.currentTimeMillis(), "ETA_UPDATED", updated.id, "deltaMinutes=$addMinutes"))
        return updated
    }

    fun checkIn(): SafetySession? = currentSession()?.copy(
        lastCheckInAt = System.currentTimeMillis(),
        missedCheckIns = 0,
        batteryPercent = battery.currentPercent(),
        state = SafetyState.NORMAL
    )?.also {
        saveSession(it)
        audit.append(AuditEvent(System.currentTimeMillis(), "CHECK_IN", it.id, "user confirmed"))
    }

    fun simulateConcern(): SafetySession? = currentSession()?.copy(
        missedCheckIns = 1,
        routeDeviation = true,
        batteryPercent = 16,
        state = SafetyState.CONCERN,
    )?.also(::saveSession)

    fun markSafe(): SafetySession? = currentSession()?.copy(
        resolved = true, state = SafetyState.RESOLVED
    )?.also {
        saveSession(it)
        scheduler.cancel(it.id)
        val endedAt = System.currentTimeMillis()
        audit.append(AuditEvent(endedAt, "SESSION_RESOLVED", it.id, "user marked safe"))
        history.recordResolved(it.id, endedAt, "SAFE")
    }

    fun lastLocationLabel(): String? = prefs.getString("last_location", null)

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
            .putString("destination", s.destinationLabel)
            .apply()
    }

    fun guardians(): List<Guardian> = listOf(
        Guardian("Primary Guardian", "Primary guardian", "Push + SMS", primary = true),
        Guardian("Backup Guardian", "Backup guardian", "Push"),
        Guardian("Family Circle", "Escalation group", "Push + call plan"),
    )
}
