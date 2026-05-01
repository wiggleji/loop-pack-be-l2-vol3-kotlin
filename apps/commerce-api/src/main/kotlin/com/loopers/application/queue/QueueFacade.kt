package com.loopers.application.queue

import com.loopers.domain.queue.QueueEntry
import com.loopers.domain.queue.QueueService
import org.springframework.stereotype.Service

@Service
class QueueFacade(
    private val queueService: QueueService,
) {

    fun enter(userId: Long): QueueEntry {
        return queueService.enter(userId)
    }

    fun getPosition(userId: Long): QueueEntry {
        return queueService.getPosition(userId)
    }

    fun consumeToken(userId: Long) {
        queueService.consumeToken(userId)
    }
}
