package com.harshapriya.safecircle.background

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class RecurringCommuteScheduler(private val context: Context) {
    fun scheduleNext(hour: Int, minute: Int) {
        val now = ZonedDateTime.now()
        var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        val delay = Duration.between(now, target).toMillis()

        val request = OneTimeWorkRequestBuilder<RecurringCommuteWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag("recurring_commute")
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelAllWorkByTag("recurring_commute")
    }
}
