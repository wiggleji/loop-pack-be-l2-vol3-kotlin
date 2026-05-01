package com.loopers.domain.ranking

/**
 * 랭킹 조회 Port (commerce-api 측 — 조회 전용).
 *
 * 쓰기 책임은 commerce-streamer 가 담당하므로 commerce-api 에는 ZINCRBY 등 쓰기 메서드가 없다.
 * Read replica 를 우선 사용해 트래픽을 분산한다 (구현체 책임).
 */
interface RankingQueryRepository {

    /**
     * score 내림차순 Top-N 조회 (offset 부터 size 개).
     *
     * @return (productId, score) 리스트. 빈 키면 빈 리스트.
     */
    fun findTopN(key: String, offset: Long, size: Long): List<RankingEntry>

    /**
     * score 내림차순 기준 0-based 순위. 멤버가 없으면 null.
     */
    fun findRank(key: String, productId: Long): Long?

    /** 키에 들어있는 멤버 수 (페이징 응답 totalCount 용) */
    fun count(key: String): Long
}

data class RankingEntry(
    val productId: Long,
    val score: Double,
)
