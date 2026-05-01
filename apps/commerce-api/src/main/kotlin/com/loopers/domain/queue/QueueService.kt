package com.loopers.domain.queue

interface QueueService {

    fun enter(userId: Long): QueueEntry

    fun getPosition(userId: Long): QueueEntry

    fun popNextBatch(count: Long): List<Long>

    fun issueToken(userId: Long): String

    fun validateToken(userId: Long, token: String): Boolean

    fun consumeToken(userId: Long)
}
