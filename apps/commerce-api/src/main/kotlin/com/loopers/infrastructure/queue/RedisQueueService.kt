package com.loopers.infrastructure.queue

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.queue.QueueEntry
import com.loopers.domain.queue.QueueService
import com.loopers.domain.queue.QueueStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

@Component
class RedisQueueService(
    private val redisTemplate: RedisTemplate<String, String>,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterRedisTemplate: RedisTemplate<String, String>,
) : QueueService {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val WAITING_QUEUE_KEY = "order:waiting-queue"
        private const val TOKEN_KEY_PREFIX = "order:entry-token:"
        private val TOKEN_TTL: Duration = Duration.ofMinutes(5)
        private const val THROUGHPUT_PER_SECOND = 175L
    }

    override fun enter(userId: Long): QueueEntry {
        val score = System.currentTimeMillis().toDouble()
        val added = masterRedisTemplate.opsForZSet()
            .addIfAbsent(WAITING_QUEUE_KEY, userId.toString(), score) ?: false

        if (!added) {
            return getPosition(userId)
        }

        val rank = redisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, userId.toString())
        val totalWaiting = redisTemplate.opsForZSet().zCard(WAITING_QUEUE_KEY) ?: 0L

        return QueueEntry(
            userId = userId,
            status = QueueStatus.WAITING,
            rank = rank,
            totalWaiting = totalWaiting,
            estimatedWaitSeconds = rank?.let { it / THROUGHPUT_PER_SECOND },
            token = null,
        )
    }

    override fun getPosition(userId: Long): QueueEntry {
        // 1. 이미 토큰이 발급되었는지 확인
        val existingToken = redisTemplate.opsForValue().get(tokenKey(userId))
        if (existingToken != null) {
            return QueueEntry(
                userId = userId,
                status = QueueStatus.ACTIVE,
                rank = 0,
                totalWaiting = redisTemplate.opsForZSet().zCard(WAITING_QUEUE_KEY) ?: 0L,
                estimatedWaitSeconds = 0,
                token = existingToken,
            )
        }

        // 2. 대기열에서 순번 조회
        val rank = redisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, userId.toString())
            ?: return QueueEntry(
                userId = userId,
                status = QueueStatus.NOT_IN_QUEUE,
                rank = null,
                totalWaiting = redisTemplate.opsForZSet().zCard(WAITING_QUEUE_KEY) ?: 0L,
                estimatedWaitSeconds = null,
                token = null,
            )

        val totalWaiting = redisTemplate.opsForZSet().zCard(WAITING_QUEUE_KEY) ?: 0L

        return QueueEntry(
            userId = userId,
            status = QueueStatus.WAITING,
            rank = rank,
            totalWaiting = totalWaiting,
            estimatedWaitSeconds = rank / THROUGHPUT_PER_SECOND,
            token = null,
        )
    }

    override fun popNextBatch(count: Long): List<Long> {
        val members = masterRedisTemplate.opsForZSet()
            .popMin(WAITING_QUEUE_KEY, count)

        if (members.isNullOrEmpty()) return emptyList()

        return members.mapNotNull { typedTuple ->
            typedTuple.value?.toLongOrNull()
        }
    }

    override fun issueToken(userId: Long): String {
        val token = UUID.randomUUID().toString()
        masterRedisTemplate.opsForValue().set(tokenKey(userId), token, TOKEN_TTL)
        log.info("Entry token issued - userId={}, ttl={}s", userId, TOKEN_TTL.seconds)
        return token
    }

    override fun validateToken(userId: Long, token: String): Boolean {
        val stored = redisTemplate.opsForValue().get(tokenKey(userId))
        return stored != null && stored == token
    }

    override fun consumeToken(userId: Long) {
        masterRedisTemplate.delete(tokenKey(userId))
        log.info("Entry token consumed - userId={}", userId)
    }

    private fun tokenKey(userId: Long) = "$TOKEN_KEY_PREFIX$userId"
}
