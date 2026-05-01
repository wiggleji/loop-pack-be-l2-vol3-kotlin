package com.loopers.domain.queue

data class QueueEntry(
    val userId: Long,
    val status: QueueStatus,
    val rank: Long?,
    val totalWaiting: Long,
    val estimatedWaitSeconds: Long?,
    val token: String?,
)

enum class QueueStatus {
    WAITING,
    ACTIVE,
    NOT_IN_QUEUE,
}
