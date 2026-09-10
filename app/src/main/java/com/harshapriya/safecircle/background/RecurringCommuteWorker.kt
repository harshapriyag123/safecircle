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
        return Result.success()
    }
}
