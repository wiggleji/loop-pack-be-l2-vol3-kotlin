package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingEntry
import com.loopers.domain.ranking.RankingQueryRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

/**
 * Redis Sorted Set 기반 랭킹 조회 어댑터 (commerce-api 측).
 *
 * 기본 [RedisTemplate] (replica preferred) 만 사용 — 조회만 담당하므로 master 접근이 필요 없다.
 *
 * Redis 명령 매핑:
 *   findTopN  → ZREVRANGE WITHSCORES
 *   findRank  → ZREVRANK
 *   count     → ZCARD
 */
@Component
class RedisRankingQueryRepository(
    private val redisTemplate: RedisTemplate<String, String>,
) : RankingQueryRepository {

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

    override fun findRank(key: String, productId: Long): Long? =
        redisTemplate.opsForZSet().reverseRank(key, productId.toString())

    override fun count(key: String): Long =
        redisTemplate.opsForZSet().zCard(key) ?: 0L
}
