package com.loopers.application.like

import com.loopers.application.like.event.ProductLikedEvent
import com.loopers.application.like.event.ProductUnlikedEvent
import com.loopers.domain.catalog.brand.BrandRepository
import com.loopers.domain.catalog.product.ProductRepository
import com.loopers.domain.catalog.product.ProductService
import com.loopers.domain.like.LikeService
import com.loopers.infrastructure.outbox.KafkaTopics
import com.loopers.infrastructure.outbox.OutboxEventService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LikeFacade(
    private val likeService: LikeService,
    private val productService: ProductService,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val outboxEventService: OutboxEventService,
) {

    @Transactional
    fun addLike(userId: Long, productId: Long) {
        productService.getById(productId) // 상품 존재 확인
        likeService.addLike(userId, productId)

        // Outbox 저장 (같은 TX — At Least Once 보장)
        outboxEventService.save(
            aggregateType = "PRODUCT",
            aggregateId = productId.toString(),
            eventType = "PRODUCT_LIKED",
            payload = ProductLikedEvent(userId = userId, productId = productId),
            topic = KafkaTopics.CATALOG_EVENTS,
            partitionKey = productId.toString(),
        )
    }

    @Transactional
    fun removeLike(userId: Long, productId: Long) {
        likeService.removeLike(userId, productId)

        // Outbox 저장 (같은 TX — At Least Once 보장)
        outboxEventService.save(
            aggregateType = "PRODUCT",
            aggregateId = productId.toString(),
            eventType = "PRODUCT_UNLIKED",
            payload = ProductUnlikedEvent(userId = userId, productId = productId),
            topic = KafkaTopics.CATALOG_EVENTS,
            partitionKey = productId.toString(),
        )
    }

    @Transactional(readOnly = true)
    fun getLikedProducts(userId: Long): List<LikedProductResult> {
        val likes = likeService.getLikedByUser(userId)
        val productIds = likes.map { it.productId }
        val productMap = productRepository.findAllByIds(productIds).associateBy { it.id }
        val brandIds = productMap.values.map { it.brandId }.distinct()
        val brandMap = brandRepository.findAllByIds(brandIds).associateBy { it.id }

        return likes.mapNotNull { like ->
            val product = productMap[like.productId] ?: return@mapNotNull null
            val brand = brandMap[product.brandId] ?: return@mapNotNull null
            LikedProductResult.from(product, brand)
        }
    }
}
