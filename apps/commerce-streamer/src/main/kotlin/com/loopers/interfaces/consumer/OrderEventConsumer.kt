package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.metrics.MetricsService
import com.loopers.application.metrics.OrderItemMetrics
import com.loopers.application.ranking.RankingUpdater
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.ranking.RankingEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class OrderEventConsumer(
    private val metricsService: MetricsService,
    private val rankingUpdater: RankingUpdater,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["order-events"],
        groupId = "commerce-streamer-order",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(
        messages: List<ConsumerRecord<String, ByteArray>>,
        acknowledgment: Acknowledgment,
    ) {
        log.info("[OrderConsumer] ${messages.size}건 수신")

        // Phase B: 모든 record 의 모든 item 을 단일 RankingEvent 리스트로 모은다 (멱등 skip 된 건 제외).
        val rankingBatch = ArrayList<RankingEvent>()

        for (record in messages) {
            try {
                val envelope = objectMapper.readTree(record.value())
                val eventId = envelope.get("eventId").asText()
                val eventType = envelope.get("eventType").asText()
                val payload = objectMapper.readTree(envelope.get("payload").asText())

                when (eventType) {
                    "ORDER_PLACED" -> {
                        val items = payload.get("items").map { item ->
                            OrderItemMetrics(
                                productId = item.get("productId").asLong(),
                                quantity = item.get("quantity").asInt(),
                                price = item.get("price").asInt(),
                            )
                        }
                        val applied = metricsService.handleOrderPlaced(eventId, items)

                        if (applied) {
                            items.forEach { item ->
                                rankingBatch.add(
                                    RankingEvent.Ordered(
                                        productId = item.productId,
                                        amount = item.price.toLong() * item.quantity.toLong(),
                                    ),
                                )
                            }
                        }
                    }
                    else -> log.warn("[OrderConsumer] 알 수 없는 eventType: $eventType")
                }
            } catch (ex: Exception) {
                log.error("[OrderConsumer] 처리 실패: offset=${record.offset()}, error=${ex.message}", ex)
            }
        }

        // Phase B flush — 같은 product 가 여러 주문에 걸쳐 있으면 합산되어 단일 ZINCRBY 로 반영
        if (rankingBatch.isNotEmpty()) {
            runCatching { rankingUpdater.applyBatch(rankingBatch) }
                .onFailure { ex ->
                    log.error("[OrderConsumer] 랭킹 batch flush 실패: size=${rankingBatch.size}, error=${ex.message}", ex)
                }
        }

        acknowledgment.acknowledge()
    }
}
