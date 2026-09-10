package com.harshapriya.safecircle.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.harshapriya.safecircle.reliability.OfflineEventQueue
import com.harshapriya.safecircle.sync.LocalDemoGateway
import com.harshapriya.safecircle.sync.SyncCoordinator

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            SyncCoordinator(
                queue = OfflineEventQueue(applicationContext),
                gateway = LocalDemoGateway()
            ).flush()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
