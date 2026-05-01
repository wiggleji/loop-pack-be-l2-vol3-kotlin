package com.loopers.infrastructure.persistence.ranking

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 주간 랭킹 Materialized View.
 *
 * - 한 주차에 대해 TOP 100 상품을 적재한다 (`period_key` 기준, 예: `2026-W16`).
 * - `(period_key, product_id)` 유니크 — 동일 주차 재실행 시 idempotent 보장.
 * - `(period_key, rank_no)` 인덱스 — API 의 페이지 조회(`ORDER BY rank_no ASC LIMIT/OFFSET`)에 사용.
 *
 * Naming notes:
 *  - `rank` 는 MySQL 8 윈도우 함수 키워드 → 컬럼명 `rank_no` (Kotlin property 는 `rank` 로 자연스럽게 노출).
 *  - `year_month` 는 MySQL 예약어(`INTERVAL ... YEAR_MONTH`) → period 컬럼은 안전하게 `period_key` 로 통일.
 *  - 주간/월간이 같은 컬럼명을 쓰지만 테이블명이 의미를 명확히 한다.
 */
@Entity
@Table(
    name = "mv_product_rank_weekly",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_mv_weekly_period_product", columnNames = ["period_key", "product_id"]),
    ],
    indexes = [
        Index(name = "idx_mv_weekly_period_rank", columnList = "period_key, rank_no"),
    ],
)
class MvProductRankWeeklyEntity(
    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "rank_no", nullable = false)
    val rank: Int,

    @Column(name = "score", nullable = false)
    val score: Double,

    @Column(name = "view_count", nullable = false)
    val viewCount: Long,

    @Column(name = "like_count", nullable = false)
    val likeCount: Long,

    @Column(name = "sales_count", nullable = false)
    val salesCount: Long,

    @Column(name = "sales_amount", nullable = false)
    val salesAmount: Long,

    @Column(name = "period_key", nullable = false, length = 10)
    val periodKey: String,
) : BaseEntity() {

    init {
        require(rank >= 1) { "[$rank] rank 는 1 이상이어야 합니다." }
        require(periodKey.isNotBlank()) { "periodKey 는 비어있을 수 없습니다." }
    }
}
