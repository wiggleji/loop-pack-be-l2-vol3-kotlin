package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.payment.PaymentFacade
import com.loopers.application.payment.RequestPaymentCommand
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.CardType
import com.loopers.infrastructure.catalog.product.ProductCacheService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class OrderEventConsumer(
    private val paymentFacade: PaymentFacade,
    private val orderService: OrderService,
    private val productCacheService: ProductCacheService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["order-events"],
        groupId = "commerce-api-order",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(
        messages: List<ConsumerRecord<String, ByteArray>>,
        acknowledgment: Acknowledgment,
    ) {
        log.info("[OrderEventConsumer] ${messages.size}건 수신")

        for (record in messages) {
            try {
                val envelope = objectMapper.readTree(record.value())
                val eventType = envelope.get("eventType").asText()
                val payload = objectMapper.readTree(envelope.get("payload").asText())

                when (eventType) {
                    "ORDER_PLACED" -> handleOrderPlaced(payload)
                    "PAYMENT_CONFIRMED" -> handlePaymentConfirmed(payload)
                    "PAYMENT_FAILED" -> handlePaymentFailed(payload)
                    else -> log.warn("[OrderEventConsumer] 알 수 없는 eventType: $eventType")
                }
            } catch (ex: Exception) {
                log.error("[OrderEventConsumer] 처리 실패: offset=${record.offset()}, error=${ex.message}", ex)
            }
        }

        acknowledgment.acknowledge()
    }

    private fun handleOrderPlaced(payload: com.fasterxml.jackson.databind.JsonNode) {
        val orderId = payload.get("orderId").asLong()
        val totalPrice = payload.get("totalPrice").asInt()
        val cardType = CardType.valueOf(payload.get("cardType").asText())
        val cardNo = payload.get("cardNo").asText()

        log.info("[OrderEventConsumer] ORDER_PLACED: orderId=$orderId")

        // 1. 결제 요청 (PG 호출)
        try {
            paymentFacade.requestPayment(
                RequestPaymentCommand(
                    orderId = orderId,
                    amount = totalPrice,
                    cardType = cardType,
                    cardNo = cardNo,
                ),
            )
        } catch (ex: Exception) {
            log.error("[OrderEventConsumer] 결제 요청 실패: orderId=$orderId, error=${ex.message}", ex)
        }

        // 2. 캐시 무효화
        try {
            val items = payload.get("items")
            items?.forEach { item ->
                productCacheService.evictProductDetail(item.get("productId").asLong())
            }
            productCacheService.evictAllProductLists()
        } catch (ex: Exception) {
            log.error("[OrderEventConsumer] 캐시 무효화 실패: orderId=$orderId, error=${ex.message}", ex)
        }
    }

    private fun handlePaymentConfirmed(payload: com.fasterxml.jackson.databind.JsonNode) {
        val orderId = payload.get("orderId").asLong()
        val paidAt = ZonedDateTime.parse(payload.get("paidAt").asText())

        log.info("[OrderEventConsumer] PAYMENT_CONFIRMED: orderId=$orderId")

        try {
            orderService.updateStatus(orderId) { order ->
                order.pay(paidAt)
            }
        } catch (ex: Exception) {
            log.error("[OrderEventConsumer] 주문 상태 변경 실패: orderId=$orderId, error=${ex.message}", ex)
        }
    }

    private fun handlePaymentFailed(payload: com.fasterxml.jackson.databind.JsonNode) {
        val orderId = payload.get("orderId").asLong()
        val reason = payload.get("reason")?.asText()
        log.info("[OrderEventConsumer] PAYMENT_FAILED: orderId=$orderId, reason=$reason")
    }
}
