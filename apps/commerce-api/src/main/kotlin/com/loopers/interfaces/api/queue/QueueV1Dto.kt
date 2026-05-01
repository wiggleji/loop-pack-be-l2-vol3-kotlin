package com.loopers.interfaces.api.queue

import com.loopers.domain.queue.QueueEntry
import com.loopers.domain.queue.QueueStatus

class QueueV1Dto {

    data class QueuePositionResponse(
        val status: QueueStatus,
        val rank: Long?,
        val totalWaiting: Long,
        val estimatedWaitSeconds: Long?,
        val token: String?,
    ) {
        companion object {
            fun from(entry: QueueEntry) = QueuePositionResponse(
                status = entry.status,
                rank = entry.rank,
                totalWaiting = entry.totalWaiting,
                estimatedWaitSeconds = entry.estimatedWaitSeconds,
                token = entry.token,
            )
        }
    }
}
