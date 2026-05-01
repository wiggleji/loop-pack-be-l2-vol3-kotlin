package com.loopers.infrastructure.catalog.product

import com.loopers.domain.catalog.product.ProductStock
import com.loopers.domain.catalog.product.ProductStockRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

@Repository
class ProductStockRepositoryImpl(
    private val productStockJpaRepository: ProductStockJpaRepository,
    @PersistenceContext private val entityManager: EntityManager,
) : ProductStockRepository {

    override fun save(stock: ProductStock): ProductStock {
        val entity = if (stock.id > 0L) {
            productStockJpaRepository.getReferenceById(stock.id).apply {
                update(stock.quantity)
            }
        } else {
            ProductStockEntity.from(stock)
        }
        return productStockJpaRepository.save(entity).toDomain()
    }

    override fun findByProductId(productId: Long): ProductStock? =
        productStockJpaRepository.findByProductId(productId)?.toDomain()

    override fun findAllByProductIds(productIds: List<Long>): List<ProductStock> =
        if (productIds.isEmpty()) emptyList()
        else productStockJpaRepository.findAllByProductIdIn(productIds).map { it.toDomain() }

    override fun findByProductIdForUpdate(productId: Long): ProductStock? {
        val entity = productStockJpaRepository.findByProductId(productId) ?: return null
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE)
        return entity.toDomain()
    }
}
