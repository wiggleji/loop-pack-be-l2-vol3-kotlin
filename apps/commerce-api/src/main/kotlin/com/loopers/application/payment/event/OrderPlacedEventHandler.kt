package com.loopers.application.payment.event

import com.loopers.application.order.event.OrderPlacedEvent
import com.loopers.application.payment.PaymentFacade
import com.loopers.application.payment.RequestPaymentCommand
import com.loopers.infrastructure.catalog.product.ProductCacheService
import com.loopers.infrastructure.outbox.KafkaTopics
import com.loopers.infrastructure.outbox.OutboxEventService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * @deprecated Kafka 기반 이벤트 처리로 대체됨.
 * Outbox → OutboxRelay → Kafka → OrderEventConsumer (commerce-api) 흐름으로 처리.
 * @see com.loopers.interfaces.consumer.OrderEventConsumer
 */
@Deprecated("Replaced by Kafka consumer: OrderEventConsumer")
// @Component — 비활성화: Kafka consumer로 대체
class OrderPlacedEventHandler(
    private val paymentFacade: PaymentFacade,
    private val productCacheService: ProductCacheService,
    private val outboxEventService: OutboxEventService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    fun handleOrderPlaced(event: OrderPlacedEvent) {
        log.info("[Event] OrderPlaced: orderId=${event.orderId}, userId=${event.userId}")

        // 1. 결제 요청 (PG 호출)
        try {
            paymentFacade.requestPayment(
                RequestPaymentCommand(
                    orderId = event.orderId,
                    amount = event.totalPrice,
                    cardType = event.cardType,
                    cardNo = event.cardNo,
                ),
            )
        } catch (ex: Exception) {
            log.error("[Event] 결제 요청 실패: orderId=${event.orderId}, error=${ex.message}", ex)
        }

        // 2. 캐시 무효화
        try {
            event.items.forEach { productCacheService.evictProductDetail(it.productId) }
            productCacheService.evictAllProductLists()
        } catch (ex: Exception) {
            log.error("[Event] 캐시 무효화 실패: orderId=${event.orderId}, error=${ex.message}", ex)
        }

        // 3. Kafka Outbox 저장 (At Least Once 보장)
        try {
            outboxEventService.save(
                aggregateType = "ORDER",
                aggregateId = event.orderId.toString(),
                eventType = "ORDER_PLACED",
                payload = event,
                topic = KafkaTopics.ORDER_EVENTS,
                partitionKey = event.orderId.toString(),
            )
        } catch (ex: Exception) {
            log.error("[Event] Outbox 저장 실패: orderId=${event.orderId}, error=${ex.message}", ex)
        }
    }
}
