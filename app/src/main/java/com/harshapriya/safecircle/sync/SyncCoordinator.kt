package com.harshapriya.safecircle.sync

import com.harshapriya.safecircle.reliability.OfflineEventQueue

class SyncCoordinator(
    private val queue: OfflineEventQueue,
    private val gateway: SafeCircleGateway
) {
    suspend fun flush(): Int {
        // Match the API batch limit; remaining events stay queued for the next flush.
        val pending = queue.all().take(500)
        if (pending.isEmpty()) return 0
        val acknowledged = gateway.upload(pending).toSet()
        pending.filter { it.id in acknowledged }.forEach { queue.acknowledge(it.id) }
        return acknowledged.size
    }
}
