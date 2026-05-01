package com.loopers.infrastructure.persistence.metrics

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version

/**
 * `product_metrics` 테이블의 batch 측 매핑.
 *
 * 원본 소유는 `commerce-streamer` 의 동일명 엔티티이고, 본 클래스는 batch 가
 *   1) Hibernate `ddl-auto=create` 로 테스트 스키마를 생성하기 위해
 *   2) Reader 가 native query 로 읽기 전 테스트 데이터를 seed 하기 위해
 * 사용하는 **mirror entity** 다.
 *
 * 컬럼/인덱스 정의가 streamer 측과 어긋나면 운영 환경에서 데이터 불일치가 발생하므로,
 * 한쪽이 변경되면 다른 쪽도 동기화해야 한다 (week-9 의 RankingKeyPolicy 와 동일한 cross-module duplication 패턴).
 */
@Entity
@Table(
    name = "product_metrics",
    indexes = [
        Index(name = "idx_product_metrics_product_id", columnList = "product_id", unique = true),
    ],
)
class ProductMetricsEntity(
    @Column(name = "product_id", nullable = false, unique = true)
    val productId: Long,

    @Column(nullable = false)
    var viewCount: Long = 0,

    @Column(nullable = false)
    var likeCount: Long = 0,

    @Column(nullable = false)
    var salesCount: Long = 0,

    @Column(nullable = false)
    var salesAmount: Long = 0,

    @Version
    val version: Long = 0,
) : BaseEntity()
