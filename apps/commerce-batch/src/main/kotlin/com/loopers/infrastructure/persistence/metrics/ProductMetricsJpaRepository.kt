package com.loopers.infrastructure.persistence.metrics

import org.springframework.data.jpa.repository.JpaRepository

/**
 * 테스트/벤치마크에서 product_metrics 데이터를 seed 하기 위한 보조 repository.
 * 운영 batch path 는 native SQL 을 직접 사용한다 (점수 계산을 DB 에서 수행).
 */
interface ProductMetricsJpaRepository : JpaRepository<ProductMetricsEntity, Long>
