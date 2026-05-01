package com.loopers.infrastructure.metrics

import org.springframework.data.jpa.repository.JpaRepository

interface ProductMetricsJpaRepository : JpaRepository<ProductMetricsEntity, Long> {
    fun findByProductId(productId: Long): ProductMetricsEntity?
}
