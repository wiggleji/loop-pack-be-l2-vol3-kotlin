package com.loopers.domain.ranking

/**
 * Materialized View 기반 랭킹 조회 Port.
 *
 * Redis 기반 [RankingQueryRepository] (DAILY 전용) 와 분리되어 있다 — 저장소가 다르면
 * 인터페이스도 별개. 두 개를 강제로 한 인터페이스로 묶으면 storage-specific 디테일이
 * 양쪽으로 새어나가므로 의도적으로 분리.
 *
 * Facade 가 [RankingPeriod] 를 보고 어느 port 를 쓸지 결정한다.
 */
interface RankingMvQueryRepository {

    /**
     * `(period, periodKey)` 의 rank ASC 페이지 조회.
     *
     * @param period   WEEKLY | MONTHLY
     * @param periodKey 예: `2026-W16`, `2026-04`
     * @param offset   0-based offset
     * @param size     limit
     */
    fun findPage(period: RankingPeriod, periodKey: String, offset: Long, size: Long): List<RankingEntry>

    /** 해당 기간에 적재된 row 수 (TOP-N 이므로 max = TOP_N) */
    fun count(period: RankingPeriod, periodKey: String): Long

    /** 특정 product 의 rank (1-based, 없으면 null) */
    fun findRank(period: RankingPeriod, periodKey: String, productId: Long): Int?
}
