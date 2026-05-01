package com.loopers.infrastructure.ranking

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 월간 랭킹 Materialized View — **commerce-api 측 read-only mirror**.
 *
 * 구조는 [MvProductRankWeeklyEntity] 와 동일, periodKey 만 `yyyy-MM` 포맷.
 * batch 측 동명 엔티티와 스키마가 일치해야 한다.
 */
@Entity
@Table(
    name = "mv_product_rank_monthly",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_mv_monthly_period_product", columnNames = ["period_key", "product_id"]),
    ],
    indexes = [
        Index(name = "idx_mv_monthly_period_rank", columnList = "period_key, rank_no"),
    ],
)
class MvProductRankMonthlyEntity(
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

    @Column(name = "period_key", nullable = false, length = 7)
    val periodKey: String,
) : BaseEntity()
