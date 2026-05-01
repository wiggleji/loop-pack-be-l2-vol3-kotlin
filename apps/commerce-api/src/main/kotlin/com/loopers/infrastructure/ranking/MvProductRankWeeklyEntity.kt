package com.loopers.infrastructure.ranking

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 주간 랭킹 Materialized View — **commerce-api 측 read-only mirror**.
 *
 * 적재는 `apps/commerce-batch` 의 동명 엔티티가 책임지고, 여기서는 조회만 한다.
 *
 * **Cross-module invariant:**
 * `apps/commerce-batch` 측의 [com.loopers.infrastructure.persistence.ranking.MvProductRankWeeklyEntity]
 * 와 컬럼/제약/인덱스가 정확히 동일해야 한다 (스키마는 batch 가 ddl-auto 또는 마이그레이션으로 만든다).
 *
 * Naming notes:
 *  - `rank` → 컬럼 `rank_no` (MySQL 8 윈도우 함수 키워드 회피)
 *  - 기간 컬럼은 `period_key` 로 통일 (`year_month` 는 MySQL 예약어)
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
) : BaseEntity()
