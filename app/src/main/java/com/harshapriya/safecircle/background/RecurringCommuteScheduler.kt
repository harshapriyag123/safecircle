package com.harshapriya.safecircle.background

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class RecurringCommuteScheduler(private val context: Context) {
    fun scheduleNext(hour: Int, minute: Int) {
        require(hour in 0..23)
        require(minute in 0..59)

        val now = ZonedDateTime.now()
        var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        val delay = Duration.between(now, target).toMillis()

        val request = OneTimeWorkRequestBuilder<RecurringCommuteWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putInt(RecurringCommuteWorker.KEY_HOUR, hour)
                    .putInt(RecurringCommuteWorker.KEY_MINUTE, minute)
                    .build()
            )
            .addTag("recurring_commute")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "recurring_commute_daily",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork("recurring_commute_daily")
    }
}
