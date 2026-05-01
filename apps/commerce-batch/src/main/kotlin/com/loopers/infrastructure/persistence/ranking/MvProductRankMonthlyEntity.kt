package com.loopers.infrastructure.persistence.ranking

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 월간 랭킹 Materialized View.
 *
 * 구조는 [MvProductRankWeeklyEntity] 와 동일하나, `period_key` 값이 `yyyy-MM` 포맷이고
 * 의도적으로 별도 테이블로 분리한다. (조회 패턴/SLA/적재 주기 분리, 인덱스 격리)
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
) : BaseEntity() {

    init {
        require(rank >= 1) { "[$rank] rank 는 1 이상이어야 합니다." }
        require(periodKey.isNotBlank()) { "periodKey 는 비어있을 수 없습니다." }
    }
}
