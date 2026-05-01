package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingEntry
import com.loopers.domain.ranking.RankingQueryRepository
import com.loopers.domain.ranking.RankingRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Redis Sorted Set 기반 랭킹 저장소 (Adapter).
 *
 * - **쓰기**: master 템플릿 사용 (정합성 우선)
 * - **조회**: replica 우선 템플릿 사용 (트래픽 분산)
 *
 * Redis 명령어 매핑:
 *   incrementScore  → ZINCRBY
 *   batchIncrement  → executePipelined { ZINCRBY * N }
 *   expire          → EXPIRE
 *   findTopN        → ZREVRANGE WITHSCORES
 *   findScore       → ZSCORE
 *   findRank        → ZREVRANK (score 내림차순 기준)
 *   count           → ZCARD
 */
@Component
class RedisRankingRepository(
    private val redisTemplate: RedisTemplate<String, String>,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterRedisTemplate: RedisTemplate<String, String>,
) : RankingRepository, RankingQueryRepository {

    // ───── Write Path ─────

    override fun incrementScore(key: String, productId: Long, delta: Double) {
        masterRedisTemplate.opsForZSet().incrementScore(key, productId.toString(), delta)
    }

    override fun batchIncrement(key: String, deltas: Map<Long, Double>) {
        if (deltas.isEmpty()) return

        masterRedisTemplate.executePipelined(
            org.springframework.data.redis.core.RedisCallback { connection ->
                val serializer = masterRedisTemplate.stringSerializer
                val keyBytes = serializer.serialize(key)!!
                deltas.forEach { (productId, delta) ->
                    val memberBytes = serializer.serialize(productId.toString())!!
                    connection.zSetCommands().zIncrBy(keyBytes, delta, memberBytes)
                }
                null
            },
        )
    }

    override fun expire(key: String, ttl: Duration) {
        masterRedisTemplate.expire(key, ttl.seconds, TimeUnit.SECONDS)
    }

    // ───── Read Path ─────

    override fun findTopN(key: String, offset: Long, size: Long): List<RankingEntry> {
        if (size <= 0) return emptyList()
        val end = offset + size - 1
        val tuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, offset, end)
            ?: return emptyList()

        return tuples.mapNotNull { tuple ->
            val productId = tuple.value?.toLongOrNull() ?: return@mapNotNull null
            val score = tuple.score ?: return@mapNotNull null
            RankingEntry(productId = productId, score = score)
        }
    }

    override fun findScore(key: String, productId: Long): Double? =
        redisTemplate.opsForZSet().score(key, productId.toString())

    override fun findRank(key: String, productId: Long): Long? =
        redisTemplate.opsForZSet().reverseRank(key, productId.toString())

    override fun count(key: String): Long =
        redisTemplate.opsForZSet().zCard(key) ?: 0L
}
