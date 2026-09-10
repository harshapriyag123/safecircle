package com.harshapriya.safecircle.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.harshapriya.safecircle.platform.NotificationService

class RecurringCommuteWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        NotificationService(applicationContext).show(
            8120,
            "Start your usual Safety Session?",
            "Your recurring commute window is coming up."
        )

        val hour = inputData.getInt(KEY_HOUR, -1)
        val minute = inputData.getInt(KEY_MINUTE, -1)
        if (hour in 0..23 && minute in 0..59) {
            RecurringCommuteScheduler(applicationContext).scheduleNext(hour, minute)
        }
        return Result.success()
    }

    companion object {
        const val KEY_HOUR = "hour"
        const val KEY_MINUTE = "minute"
    }
}
