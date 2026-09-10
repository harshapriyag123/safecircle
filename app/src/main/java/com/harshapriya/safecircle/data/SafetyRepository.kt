package com.harshapriya.safecircle.data

import android.content.Context
import com.harshapriya.safecircle.background.SafetyScheduler
import com.harshapriya.safecircle.background.SyncScheduler
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

    fun startSession(mode: SessionMode): SafetySession = startSession(mode, mode.minutes, null, null)

    fun startSession(
        mode: SessionMode,
        durationMinutes: Int,
        destinationLabel: String?,
        originLabel: String? = null
    ): SafetySession {
        val now = System.currentTimeMillis()
        val safeDuration = durationMinutes.coerceIn(5, 24 * 60)
        currentSession()?.takeIf { !it.resolved }?.let { active ->
            scheduler.cancel(active.id)
            audit.append(AuditEvent(now, "SESSION_REPLACED", active.id, "new session started"))
        }
        val session = SafetySession(
            id = UUID.randomUUID().toString(),
            mode = mode,
            startedAt = now,
            expectedEndAt = now + safeDuration * 60_000L,
            lastCheckInAt = now,
            batteryPercent = battery.currentPercent(),
            destinationLabel = destinationLabel?.trim()?.takeIf { it.isNotBlank() },
            originLabel = originLabel?.trim()?.takeIf { it.isNotBlank() },
            state = SafetyState.NORMAL,
            resolved = false,
            resolvedAt = null
        )
        saveSession(session)
        scheduler.schedule(session.id, session.expectedEndAt)
        audit.append(AuditEvent(now, "SESSION_STARTED", session.id, "mode=${mode.name};duration=$safeDuration"))
        history.recordStarted(session.id, mode.name, now)
        SyncScheduler.syncNow(context)
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
            destinationLabel = prefs.getString("destination", null),
            originLabel = prefs.getString("origin", null),
            resolvedAt = prefs.getLong("resolved_at", 0L).takeIf { it > 0L }
        )
    }

    fun updateEta(addMinutes: Int): SafetySession? {
        val current = currentSession() ?: return null
        if (current.resolved || addMinutes <= 0) {
            audit.append(AuditEvent(System.currentTimeMillis(), "ETA_UPDATE_REJECTED", current.id, "resolved=${current.resolved};delta=$addMinutes"))
            return current
        }
        scheduler.cancel(current.id)
        val updated = current.copy(expectedEndAt = current.expectedEndAt + addMinutes * 60_000L)
        saveSession(updated)
        scheduler.schedule(updated.id, updated.expectedEndAt)
        audit.append(AuditEvent(System.currentTimeMillis(), "ETA_UPDATED", updated.id, "deltaMinutes=$addMinutes;newExpected=${updated.expectedEndAt}"))
        SyncScheduler.syncNow(context)
        return updated
    }

    fun checkIn(): SafetySession? {
        val current = currentSession() ?: return null
        if (current.resolved) {
            audit.append(AuditEvent(System.currentTimeMillis(), "CHECK_IN_REJECTED", current.id, "session already resolved"))
            return current
        }
        val updated = current.copy(
            lastCheckInAt = System.currentTimeMillis(),
            missedCheckIns = 0,
            routeDeviation = false,
            batteryPercent = battery.currentPercent(),
            state = SafetyState.NORMAL
        )
        saveSession(updated)
        audit.append(AuditEvent(updated.lastCheckInAt, "CHECK_IN", updated.id, "user confirmed"))
        SyncScheduler.syncNow(context)
        return updated
    }

    /** Demo-only state injection. Production UI must route this through Judge Mode. */
    fun simulateConcern(): SafetySession? {
        val current = currentSession() ?: return null
        if (current.resolved) {
            audit.append(AuditEvent(System.currentTimeMillis(), "DEMO_CONCERN_REJECTED", current.id, "session already resolved"))
            return current
        }
        val updated = current.copy(
            missedCheckIns = 1,
            routeDeviation = true,
            state = SafetyState.CONCERN
        )
        saveSession(updated)
        audit.append(AuditEvent(System.currentTimeMillis(), "DEMO_CONCERN_SIMULATED", updated.id, "judge-mode signal injection"))
        return updated
    }

    fun markSafe(): SafetySession? {
        val current = currentSession() ?: return null
        if (current.resolved) return current
        val endedAt = System.currentTimeMillis()
        val updated = current.copy(
            resolved = true,
            state = SafetyState.RESOLVED,
            resolvedAt = endedAt
        )
        saveSession(updated)
        scheduler.cancel(updated.id)
        audit.append(AuditEvent(endedAt, "SESSION_RESOLVED", updated.id, "user marked safe"))
        history.recordResolved(updated.id, endedAt, "SAFE")
        SyncScheduler.syncNow(context)
        return updated
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
            .putString("origin", s.originLabel)
            .putLong("resolved_at", s.resolvedAt ?: 0L)
            .apply()
    }

    fun guardians(): List<Guardian> = listOf(
        Guardian("Primary Guardian", "Primary guardian", "Push + SMS", primary = true),
        Guardian("Backup Guardian", "Backup guardian", "Push"),
        Guardian("Family Circle", "Escalation group", "Push + call plan"),
    )
}
