package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.metrics.MetricsService
import com.loopers.application.ranking.RankingUpdater
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.ranking.RankingEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class CatalogEventConsumer(
    private val metricsService: MetricsService,
    private val rankingUpdater: RankingUpdater,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["catalog-events"],
        groupId = "commerce-streamer-catalog",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(
        messages: List<ConsumerRecord<String, ByteArray>>,
        acknowledgment: Acknowledgment,
    ) {
        log.info("[CatalogConsumer] ${messages.size}건 수신")

        // Phase B: 배치 안에서 적용 가능한 RankingEvent 만 모았다가 한 번에 flush.
        // 멱등 skip 된 record 는 buffer 에 들어가지 않으므로 중복 카운트 방지 유지.
        val rankingBatch = ArrayList<RankingEvent>(messages.size)

        for (record in messages) {
            try {
                val envelope = objectMapper.readTree(record.value())
                val eventId = envelope.get("eventId").asText()
                val eventType = envelope.get("eventType").asText()
                val payload = objectMapper.readTree(envelope.get("payload").asText())
                val productId = payload.get("productId").asLong()

                val applied = when (eventType) {
                    "PRODUCT_VIEWED" -> metricsService.handleProductViewed(eventId, productId)
                    "PRODUCT_LIKED" -> metricsService.handleProductLiked(eventId, productId)
                    "PRODUCT_UNLIKED" -> metricsService.handleProductUnliked(eventId, productId)
                    else -> {
                        log.warn("[CatalogConsumer] 알 수 없는 eventType: $eventType")
                        false
                    }
                }

                if (applied) {
                    toRankingEvent(eventType, productId)?.let { rankingBatch.add(it) }
                }
            } catch (ex: Exception) {
                log.error("[CatalogConsumer] 처리 실패: offset=${record.offset()}, error=${ex.message}", ex)
            }
        }

        // Phase B flush — productId 별로 합산되어 단일 pipelined batchIncrement 로 반영됨
        if (rankingBatch.isNotEmpty()) {
            runCatching { rankingUpdater.applyBatch(rankingBatch) }
                .onFailure { ex ->
                    // Redis 실패는 main 흐름을 막지 않음 (Eventual Consistency)
                    log.error("[CatalogConsumer] 랭킹 batch flush 실패: size=${rankingBatch.size}, error=${ex.message}", ex)
                }
        }

        acknowledgment.acknowledge()
    }

    private fun toRankingEvent(eventType: String, productId: Long): RankingEvent? = when (eventType) {
        "PRODUCT_VIEWED" -> RankingEvent.Viewed(productId)
        "PRODUCT_LIKED" -> RankingEvent.Liked(productId)
        "PRODUCT_UNLIKED" -> RankingEvent.Unliked(productId)
        else -> null
    }
}
