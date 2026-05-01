package com.loopers.application.queue

import com.loopers.domain.queue.QueueService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class QueueSchedulerUnitTest {

    private val mockQueueService = mockk<QueueService>(relaxed = true)
    private val scheduler = QueueScheduler(mockQueueService)

    @Test
    fun `processQueue() should pop batch and issue tokens for each user`() {
        // Arrange
        every { mockQueueService.popNextBatch(QueueScheduler.BATCH_SIZE) } returns listOf(1L, 2L, 3L)
        every { mockQueueService.issueToken(any()) } returns "token"

        // Act
        scheduler.processQueue()

        // Assert
        verify(exactly = 1) { mockQueueService.popNextBatch(QueueScheduler.BATCH_SIZE) }
        verify(exactly = 1) { mockQueueService.issueToken(1L) }
        verify(exactly = 1) { mockQueueService.issueToken(2L) }
        verify(exactly = 1) { mockQueueService.issueToken(3L) }
    }

    @Test
    fun `processQueue() should do nothing when queue is empty`() {
        // Arrange
        every { mockQueueService.popNextBatch(QueueScheduler.BATCH_SIZE) } returns emptyList()

        // Act
        scheduler.processQueue()

        // Assert
        verify(exactly = 1) { mockQueueService.popNextBatch(QueueScheduler.BATCH_SIZE) }
        verify(exactly = 0) { mockQueueService.issueToken(any()) }
    }

    @Test
    fun `processQueue() should continue issuing tokens even if one fails`() {
        // Arrange
        every { mockQueueService.popNextBatch(QueueScheduler.BATCH_SIZE) } returns listOf(1L, 2L, 3L)
        every { mockQueueService.issueToken(1L) } returns "token-1"
        every { mockQueueService.issueToken(2L) } throws RuntimeException("Redis error")
        every { mockQueueService.issueToken(3L) } returns "token-3"

        // Act
        scheduler.processQueue()

        // Assert — all 3 should be attempted
        verify(exactly = 1) { mockQueueService.issueToken(1L) }
        verify(exactly = 1) { mockQueueService.issueToken(2L) }
        verify(exactly = 1) { mockQueueService.issueToken(3L) }
    }
}
