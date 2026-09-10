package com.harshapriya.safecircle.background

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SafetyScheduler(private val context: Context) {
    fun schedule(sessionId: String, expectedEndAt: Long) {
        val wm = WorkManager.getInstance(context)
        listOf(0L, 5L, 10L, 15L).forEach { minutesAfter ->
            val runAt = expectedEndAt + minutesAfter * 60_000L
            val delay = (runAt - System.currentTimeMillis()).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<SafetyCheckWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(SafetyCheckWorker.KEY_SESSION_ID, sessionId)
                        .putLong(SafetyCheckWorker.KEY_STAGE_MINUTES, minutesAfter)
                        .build()
                )
                .addTag("safety_session_$sessionId")
                .build()
            wm.enqueue(request)
        }
    }

    fun cancel(sessionId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag("safety_session_$sessionId")
    }
}
