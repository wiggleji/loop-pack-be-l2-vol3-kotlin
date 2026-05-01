package com.loopers.domain.ranking

/**
 * 랭킹 조회 Port. 쓰기와 분리해 CQRS 스타일로 운영한다.
 *
 * Streamer 는 주로 [RankingRepository] (쓰기) 를 사용하지만,
 * 카나리 검증/통합 테스트 등에서 ZSET 상태를 조회해야 할 때 이 인터페이스를 사용한다.
 * commerce-api 측에도 동일 형태의 Port 가 별도로 존재할 예정 (계층 분리).
 */
interface RankingQueryRepository {

    /** ZREVRANGE — score 내림차순 Top-N (offset 부터 size 개) */
    fun findTopN(key: String, offset: Long, size: Long): List<RankingEntry>

    /** ZSCORE — 특정 product 의 현재 점수 (없으면 null) */
    fun findScore(key: String, productId: Long): Double?

    /** ZREVRANK — score 내림차순 기준 0-based 순위 (없으면 null) */
    fun findRank(key: String, productId: Long): Long?

    /** ZCARD — 키에 들어있는 멤버 수 */
    fun count(key: String): Long
}

/** ZSET 의 (member, score) 쌍을 표현하는 도메인 결과 객체. */
data class RankingEntry(
    val productId: Long,
    val score: Double,
)
