package com.harshapriya.safecircle.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.harshapriya.safecircle.reliability.OfflineEventQueue
import com.harshapriya.safecircle.sync.HttpSafeCircleGateway
import com.harshapriya.safecircle.sync.LocalDemoGateway
import com.harshapriya.safecircle.sync.NetworkConfig
import com.harshapriya.safecircle.sync.SafeCircleGateway
import com.harshapriya.safecircle.sync.SyncCoordinator

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val gateway: SafeCircleGateway =
                if (NetworkConfig.isConfigured) {
                    HttpSafeCircleGateway(NetworkConfig.baseUrl, NetworkConfig.demoToken)
                } else {
                    LocalDemoGateway()
                }

            SyncCoordinator(
                queue = OfflineEventQueue(applicationContext),
                gateway = gateway
            ).flush()

            Result.success()
        }.getOrElse {
            if (NetworkConfig.isConfigured) Result.retry() else Result.success()
        }
    }
}
