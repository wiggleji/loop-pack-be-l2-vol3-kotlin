package com.loopers.domain.catalog.product

interface ProductStockRepository {
    fun save(stock: ProductStock): ProductStock
    fun findByProductId(productId: Long): ProductStock?
    fun findAllByProductIds(productIds: List<Long>): List<ProductStock>
    fun findByProductIdForUpdate(productId: Long): ProductStock?
}
