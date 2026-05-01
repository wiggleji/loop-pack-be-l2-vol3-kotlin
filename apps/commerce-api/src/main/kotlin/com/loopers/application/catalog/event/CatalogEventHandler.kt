package com.loopers.application.catalog.event

import com.loopers.infrastructure.outbox.KafkaTopics
import com.loopers.infrastructure.outbox.OutboxEventService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async

/**
 * @deprecated Kafka 기반 이벤트 처리로 대체됨.
 * ProductFacade에서 직접 Outbox 저장 → OutboxRelay → Kafka 흐름으로 처리.
 * @see com.loopers.application.catalog.product.ProductFacade
 */
@Deprecated("Replaced by direct Outbox save in ProductFacade")
// @Component — 비활성화: Outbox가 Facade TX에서 직접 저장됨
class CatalogEventHandler(
    private val outboxEventService: OutboxEventService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    @Async
    fun handleProductViewed(event: ProductViewedEvent) {
        log.info("[UserAction] PRODUCT_VIEWED: userId=${event.userId}, productId=${event.productId}")

        try {
            outboxEventService.save(
                aggregateType = "PRODUCT",
                aggregateId = event.productId.toString(),
                eventType = "PRODUCT_VIEWED",
                payload = event,
                topic = KafkaTopics.CATALOG_EVENTS,
                partitionKey = event.productId.toString(),
            )
        } catch (ex: Exception) {
            log.error("[Event] Outbox 저장 실패: productId=${event.productId}, error=${ex.message}", ex)
        }
    }
}
