package com.loopers.application.queue

import com.loopers.domain.queue.QueueEntry
import com.loopers.domain.queue.QueueService
import com.loopers.domain.queue.QueueStatus
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class QueueFacadeUnitTest {

    private val mockQueueService = mockk<QueueService>()
    private val facade = QueueFacade(mockQueueService)

    @Test
    fun `enter() should delegate to queueService and return entry`() {
        // Arrange
        val expected = QueueEntry(
            userId = 1L,
            status = QueueStatus.WAITING,
            rank = 0L,
            totalWaiting = 1L,
            estimatedWaitSeconds = 0L,
            token = null,
        )
        every { mockQueueService.enter(1L) } returns expected

        // Act
        val result = facade.enter(1L)

        // Assert
        assertThat(result).isEqualTo(expected)
        verify(exactly = 1) { mockQueueService.enter(1L) }
    }

    @Test
    fun `getPosition() should return ACTIVE with token when token is issued`() {
        // Arrange
        val expected = QueueEntry(
            userId = 1L,
            status = QueueStatus.ACTIVE,
            rank = 0L,
            totalWaiting = 0L,
            estimatedWaitSeconds = 0L,
            token = "abc-token",
        )
        every { mockQueueService.getPosition(1L) } returns expected

        // Act
        val result = facade.getPosition(1L)

        // Assert
        assertThat(result.status).isEqualTo(QueueStatus.ACTIVE)
        assertThat(result.token).isEqualTo("abc-token")
    }

    @Test
    fun `consumeToken() should delegate to queueService`() {
        // Arrange
        every { mockQueueService.consumeToken(1L) } just Runs

        // Act
        facade.consumeToken(1L)

        // Assert
        verify(exactly = 1) { mockQueueService.consumeToken(1L) }
    }
}
