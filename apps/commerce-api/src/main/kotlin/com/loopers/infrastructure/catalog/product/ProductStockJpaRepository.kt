package com.loopers.infrastructure.catalog.product

import org.springframework.data.jpa.repository.JpaRepository

interface ProductStockJpaRepository : JpaRepository<ProductStockEntity, Long> {
    fun findByProductId(productId: Long): ProductStockEntity?
    fun findAllByProductIdIn(productIds: List<Long>): List<ProductStockEntity>
}
