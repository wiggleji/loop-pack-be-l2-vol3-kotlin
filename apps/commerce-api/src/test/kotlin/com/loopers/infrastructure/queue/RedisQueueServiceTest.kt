package com.loopers.infrastructure.queue

import com.loopers.domain.queue.QueueStatus
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@ActiveProfiles("test")
class RedisQueueServiceTest @Autowired constructor(
    private val redisQueueService: RedisQueueService,
    private val redisCleanUp: RedisCleanUp,
) {

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @Test
    fun `enter() should assign sequential ranks based on entry order`() {
        // Arrange & Act
        val entry1 = redisQueueService.enter(1L)
        val entry2 = redisQueueService.enter(2L)
        val entry3 = redisQueueService.enter(3L)

        // Assert
        assertThat(entry1.status).isEqualTo(QueueStatus.WAITING)
        assertThat(entry1.rank).isEqualTo(0L)
        assertThat(entry2.rank).isEqualTo(1L)
        assertThat(entry3.rank).isEqualTo(2L)
        assertThat(entry3.totalWaiting).isEqualTo(3L)
    }

    @Test
    fun `enter() should prevent duplicate entry for same userId`() {
        // Arrange
        redisQueueService.enter(1L)

        // Act
        val duplicate = redisQueueService.enter(1L)

        // Assert — rank should remain 0, totalWaiting should still be 1
        assertThat(duplicate.rank).isEqualTo(0L)
        assertThat(duplicate.totalWaiting).isEqualTo(1L)
    }

    @Test
    fun `enter() - concurrent entry by 100 users should maintain fair ordering`() {
        // Arrange
        val threadCount = 100
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        // Act
        repeat(threadCount) { i ->
            executor.submit {
                try {
                    redisQueueService.enter((i + 1).toLong())
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Assert — all 100 users should be in the queue with unique ranks
        val positions = (1L..100L).map { redisQueueService.getPosition(it) }
        val ranks = positions.mapNotNull { it.rank }

        assertThat(ranks).hasSize(100)
        assertThat(ranks.toSet()).hasSize(100) // all unique
        assertThat(ranks).allMatch { it in 0L..99L }

        positions.forEach { entry ->
            assertThat(entry.status).isEqualTo(QueueStatus.WAITING)
            assertThat(entry.totalWaiting).isEqualTo(100L)
        }
    }

    @Test
    fun `getPosition() should return NOT_IN_QUEUE for user not in queue`() {
        // Act
        val entry = redisQueueService.getPosition(999L)

        // Assert
        assertThat(entry.status).isEqualTo(QueueStatus.NOT_IN_QUEUE)
        assertThat(entry.rank).isNull()
        assertThat(entry.estimatedWaitSeconds).isNull()
        assertThat(entry.token).isNull()
    }

    @Test
    fun `getPosition() should return ACTIVE with token after token is issued`() {
        // Arrange
        redisQueueService.enter(1L)
        redisQueueService.popNextBatch(1)
        redisQueueService.issueToken(1L)

        // Act
        val entry = redisQueueService.getPosition(1L)

        // Assert
        assertThat(entry.status).isEqualTo(QueueStatus.ACTIVE)
        assertThat(entry.rank).isEqualTo(0L)
        assertThat(entry.token).isNotNull()
    }

    @Test
    fun `popNextBatch() should pop users in FIFO order`() {
        // Arrange
        redisQueueService.enter(1L)
        Thread.sleep(10) // ensure different timestamps
        redisQueueService.enter(2L)
        Thread.sleep(10)
        redisQueueService.enter(3L)

        // Act — pop 2 users
        val popped = redisQueueService.popNextBatch(2)

        // Assert
        assertThat(popped).containsExactly(1L, 2L)

        // remaining queue should have only user 3
        val remaining = redisQueueService.getPosition(3L)
        assertThat(remaining.rank).isEqualTo(0L)
        assertThat(remaining.totalWaiting).isEqualTo(1L)
    }

    @Test
    fun `popNextBatch() should return empty list when queue is empty`() {
        // Act
        val popped = redisQueueService.popNextBatch(10)

        // Assert
        assertThat(popped).isEmpty()
    }

    @Test
    fun `issueToken() and validateToken() should work correctly`() {
        // Arrange
        val token = redisQueueService.issueToken(1L)

        // Act & Assert
        assertThat(redisQueueService.validateToken(1L, token)).isTrue()
        assertThat(redisQueueService.validateToken(1L, "invalid-token")).isFalse()
        assertThat(redisQueueService.validateToken(999L, token)).isFalse()
    }

    @Test
    fun `consumeToken() should invalidate the token`() {
        // Arrange
        val token = redisQueueService.issueToken(1L)
        assertThat(redisQueueService.validateToken(1L, token)).isTrue()

        // Act
        redisQueueService.consumeToken(1L)

        // Assert
        assertThat(redisQueueService.validateToken(1L, token)).isFalse()
    }

    @Test
    fun `estimatedWaitSeconds should be calculated based on rank and throughput`() {
        // Arrange — add 200 users to see non-zero wait time
        repeat(200) { i ->
            redisQueueService.enter((i + 1).toLong())
        }

        // Act
        val entry = redisQueueService.getPosition(200L)

        // Assert — rank 199, throughput 175/s → 199 / 175 = 1 (integer division)
        assertThat(entry.rank).isEqualTo(199L)
        assertThat(entry.estimatedWaitSeconds).isEqualTo(199L / 175L)
    }

    @Test
    fun `full flow - enter, wait, get token, validate, consume`() {
        // 1. Enter queue
        val enterResult = redisQueueService.enter(1L)
        assertThat(enterResult.status).isEqualTo(QueueStatus.WAITING)
        assertThat(enterResult.rank).isEqualTo(0L)

        // 2. Scheduler pops and issues token
        val popped = redisQueueService.popNextBatch(1)
        assertThat(popped).containsExactly(1L)
        val token = redisQueueService.issueToken(1L)

        // 3. User polls and gets ACTIVE status with token
        val position = redisQueueService.getPosition(1L)
        assertThat(position.status).isEqualTo(QueueStatus.ACTIVE)
        assertThat(position.token).isEqualTo(token)

        // 4. User places order — validate token
        assertThat(redisQueueService.validateToken(1L, token)).isTrue()

        // 5. Order success — consume token
        redisQueueService.consumeToken(1L)
        assertThat(redisQueueService.validateToken(1L, token)).isFalse()

        // 6. User is no longer in queue
        val after = redisQueueService.getPosition(1L)
        assertThat(after.status).isEqualTo(QueueStatus.NOT_IN_QUEUE)
    }
}
