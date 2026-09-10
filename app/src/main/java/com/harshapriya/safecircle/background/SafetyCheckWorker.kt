package com.harshapriya.safecircle.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.harshapriya.safecircle.data.SafetyRepository
import com.harshapriya.safecircle.domain.SafetyEngine
import com.harshapriya.safecircle.platform.NotificationService
import com.harshapriya.safecircle.reliability.AuditEvent
import com.harshapriya.safecircle.reliability.AuditLog
import com.harshapriya.safecircle.reliability.OfflineEventQueue
import com.harshapriya.safecircle.reliability.RateLimiter

class SafetyCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        val stage = inputData.getLong(KEY_STAGE_MINUTES, 0L)
        val repo = SafetyRepository(applicationContext)
        val session = repo.currentSession() ?: return Result.success()
        if (session.id != sessionId || session.resolved) return Result.success()

        val snapshot = SafetyEngine.evaluate(session)
        val limiter = RateLimiter(applicationContext)
        if (!limiter.allow("stage_${session.id}_$stage", 60_000L)) return Result.success()

        val message = when (stage) {
            0L -> "Your Safety Session reached its expected-safe time. Please check in."
            5L -> "Safety Session unresolved. Primary Guardian escalation is ready."
            10L -> "Safety Session still unresolved. Backup Guardian escalation is ready."
            else -> "Safety Capsule release threshold reached for this session."
        }

        val notifier = NotificationService(applicationContext)
        val notificationId = (session.id.hashCode() + stage).toInt()
        if (stage == 0L) {
            notifier.showCheckIn(
                notificationId,
                "SafeCircle · ${snapshot.state.name}",
                message,
                session.id
            )
        } else {
            notifier.show(
                notificationId,
                "SafeCircle · ${snapshot.state.name}",
                message
            )
        }
        AuditLog(applicationContext).append(
            AuditEvent(System.currentTimeMillis(), "ESCALATION_STAGE", session.id, "stage=$stage state=${snapshot.state}")
        )
        OfflineEventQueue(applicationContext).enqueue(
            "ESCALATION_STAGE",
            """{"sessionId":"${session.id}","stage":$stage,"state":"${snapshot.state}"}"""
        )
        return Result.success()
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
        const val KEY_STAGE_MINUTES = "stage_minutes"
    }
}
