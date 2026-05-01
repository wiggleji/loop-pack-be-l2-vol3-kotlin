package com.loopers.infrastructure.catalog.product

import com.loopers.domain.catalog.product.Product
import com.loopers.domain.catalog.product.ProductRepository
import com.loopers.domain.catalog.product.ProductSearchCondition
import com.loopers.domain.catalog.product.ProductSort
import com.loopers.domain.catalog.product.ProductStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

@Repository
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
) : ProductRepository {

    override fun save(product: Product): Product {
        val entity = if (product.id > 0L) {
            productJpaRepository.getReferenceById(product.id).apply {
                update(product.name, product.description, product.price, product.status)
                updateLikeCount(product.likeCount)
            }
        } else {
            ProductEntity.from(product)
        }
        return productJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): Product? =
        productJpaRepository.findById(id)
            .filter { it.deletedAt == null }
            .map { it.toDomain() }
            .orElse(null)

    override fun findAllByIds(ids: List<Long>): List<Product> =
        if (ids.isEmpty()) emptyList()
        else productJpaRepository.findAllByIdIn(ids).map { it.toDomain() }

    override fun findAll(condition: ProductSearchCondition): List<Product> {
        val pageable = PageRequest.of(condition.page, condition.size)
        return when (condition.sort) {
            ProductSort.LATEST -> productJpaRepository.findAllByStatusOrderByCreatedAtDesc(condition.brandId, ProductStatus.ACTIVE, pageable)
            ProductSort.PRICE_ASC -> productJpaRepository.findAllByStatusOrderByPriceAsc(condition.brandId, ProductStatus.ACTIVE, pageable)
            ProductSort.LIKES_DESC -> productJpaRepository.findAllByStatusOrderByLikeCountDesc(condition.brandId, ProductStatus.ACTIVE, pageable)
        }.map { it.toDomain() }
    }

    override fun findAllByBrandId(brandId: Long): List<Product> =
        productJpaRepository.findAllByBrandId(brandId).map { it.toDomain() }

    override fun deleteById(id: Long) {
        productJpaRepository.findById(id)
            .filter { it.deletedAt == null }
            .ifPresent {
                it.updateStatus(ProductStatus.DISCONTINUED)
                it.delete()
            }
    }

    override fun deleteAllByBrandId(brandId: Long) {
        productJpaRepository.findAllByBrandId(brandId).forEach {
            it.updateStatus(ProductStatus.DISCONTINUED)
            it.delete()
        }
    }

    override fun incrementLikeCountAtomic(id: Long): Boolean =
        productJpaRepository.incrementLikeCountAtomic(id) > 0

    override fun decrementLikeCountAtomic(id: Long): Boolean =
        productJpaRepository.decrementLikeCountAtomic(id) > 0
}
