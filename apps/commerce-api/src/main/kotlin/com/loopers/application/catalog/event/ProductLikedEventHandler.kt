package com.loopers.application.catalog.event

import com.loopers.application.like.event.ProductLikedEvent
import com.loopers.application.like.event.ProductUnlikedEvent
import com.loopers.domain.catalog.product.ProductService
import com.loopers.infrastructure.catalog.product.ProductCacheService
import com.loopers.infrastructure.outbox.KafkaTopics
import com.loopers.infrastructure.outbox.OutboxEventService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * @deprecated Kafka 기반 이벤트 처리로 대체됨.
 * Outbox → OutboxRelay → Kafka → CatalogEventConsumer (commerce-api) 흐름으로 처리.
 * @see com.loopers.interfaces.consumer.CatalogEventConsumer
 */
@Deprecated("Replaced by Kafka consumer: CatalogEventConsumer")
// @Component — 비활성화: Kafka consumer로 대체
class ProductLikedEventHandler(
    private val productService: ProductService,
    private val productCacheService: ProductCacheService,
    private val outboxEventService: OutboxEventService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun handleProductLiked(event: ProductLikedEvent) {
        log.info("[Event] ProductLiked: userId=${event.userId}, productId=${event.productId}")

        try {
            productService.incrementLikeCount(event.productId)
        } catch (ex: Exception) {
            log.error("[Event] likeCount 증가 실패: productId=${event.productId}, error=${ex.message}", ex)
        }

        try {
            productCacheService.evictProductDetail(event.productId)
            productCacheService.evictAllProductLists()
        } catch (ex: Exception) {
            log.error("[Event] 캐시 무효화 실패: productId=${event.productId}, error=${ex.message}", ex)
        }

        // Kafka Outbox 저장
        try {
            outboxEventService.save(
                aggregateType = "PRODUCT",
                aggregateId = event.productId.toString(),
                eventType = "PRODUCT_LIKED",
                payload = event,
                topic = KafkaTopics.CATALOG_EVENTS,
                partitionKey = event.productId.toString(),
            )
        } catch (ex: Exception) {
            log.error("[Event] Outbox 저장 실패: productId=${event.productId}, error=${ex.message}", ex)
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun handleProductUnliked(event: ProductUnlikedEvent) {
        log.info("[Event] ProductUnliked: userId=${event.userId}, productId=${event.productId}")

        try {
            productService.decrementLikeCount(event.productId)
        } catch (ex: Exception) {
            log.error("[Event] likeCount 감소 실패: productId=${event.productId}, error=${ex.message}", ex)
        }

        try {
            productCacheService.evictProductDetail(event.productId)
            productCacheService.evictAllProductLists()
        } catch (ex: Exception) {
            log.error("[Event] 캐시 무효화 실패: productId=${event.productId}, error=${ex.message}", ex)
        }

        // Kafka Outbox 저장
        try {
            outboxEventService.save(
                aggregateType = "PRODUCT",
                aggregateId = event.productId.toString(),
                eventType = "PRODUCT_UNLIKED",
                payload = event,
                topic = KafkaTopics.CATALOG_EVENTS,
                partitionKey = event.productId.toString(),
            )
        } catch (ex: Exception) {
            log.error("[Event] Outbox 저장 실패: productId=${event.productId}, error=${ex.message}", ex)
        }
    }
}
