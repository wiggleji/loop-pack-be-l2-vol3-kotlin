package com.loopers.application.metrics

import com.loopers.infrastructure.event.EventHandledEntity
import com.loopers.infrastructure.event.EventHandledJpaRepository
import com.loopers.infrastructure.metrics.ProductMetricsEntity
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MetricsService(
    private val productMetricsJpaRepository: ProductMetricsJpaRepository,
    private val eventHandledJpaRepository: EventHandledJpaRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * @return true 면 실제로 metric 이 갱신됨 (consumer 측 후속 처리 — 예: 랭킹 ZSET 갱신 — 진행해도 됨).
     *         false 면 멱등 처리로 skip 됨 (재처리된 메시지) — 후속 처리도 같이 skip 해야 함.
     */
    @Transactional
    fun handleProductViewed(eventId: String, productId: Long): Boolean {
        if (isAlreadyHandled(eventId)) return false

        val metrics = getOrCreate(productId)
        metrics.incrementView()
        productMetricsJpaRepository.save(metrics)
        markHandled(eventId)

        log.info("[Metrics] PRODUCT_VIEWED: productId=$productId, viewCount=${metrics.viewCount}")
        return true
    }

    @Transactional
    fun handleProductLiked(eventId: String, productId: Long): Boolean {
        if (isAlreadyHandled(eventId)) return false

        val metrics = getOrCreate(productId)
        metrics.incrementLike()
        productMetricsJpaRepository.save(metrics)
        markHandled(eventId)

        log.info("[Metrics] PRODUCT_LIKED: productId=$productId, likeCount=${metrics.likeCount}")
        return true
    }

    @Transactional
    fun handleProductUnliked(eventId: String, productId: Long): Boolean {
        if (isAlreadyHandled(eventId)) return false

        val metrics = getOrCreate(productId)
        metrics.decrementLike()
        productMetricsJpaRepository.save(metrics)
        markHandled(eventId)

        log.info("[Metrics] PRODUCT_UNLIKED: productId=$productId, likeCount=${metrics.likeCount}")
        return true
    }

    @Transactional
    fun handleOrderPlaced(eventId: String, items: List<OrderItemMetrics>): Boolean {
        if (isAlreadyHandled(eventId)) return false

        for (item in items) {
            val metrics = getOrCreate(item.productId)
            metrics.addSales(item.quantity, item.price * item.quantity)
            productMetricsJpaRepository.save(metrics)
        }
        markHandled(eventId)

        log.info("[Metrics] ORDER_PLACED: ${items.size} items processed")
        return true
    }

    private fun getOrCreate(productId: Long): ProductMetricsEntity =
        productMetricsJpaRepository.findByProductId(productId)
            ?: ProductMetricsEntity(productId = productId)

    private fun isAlreadyHandled(eventId: String): Boolean {
        val exists = eventHandledJpaRepository.existsById(eventId)
        if (exists) {
            log.info("[Metrics] 이미 처리된 이벤트 skip: eventId=$eventId")
        }
        return exists
    }

    private fun markHandled(eventId: String) {
        eventHandledJpaRepository.save(EventHandledEntity(eventId = eventId))
    }
}

data class OrderItemMetrics(
    val productId: Long,
    val quantity: Int,
    val price: Int,
)
