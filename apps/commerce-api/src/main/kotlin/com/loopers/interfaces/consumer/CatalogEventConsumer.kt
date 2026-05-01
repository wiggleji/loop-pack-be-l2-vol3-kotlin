package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.catalog.product.ProductService
import com.loopers.infrastructure.catalog.product.ProductCacheService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class CatalogEventConsumer(
    private val productService: ProductService,
    private val productCacheService: ProductCacheService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["catalog-events"],
        groupId = "commerce-api-catalog",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(
        messages: List<ConsumerRecord<String, ByteArray>>,
        acknowledgment: Acknowledgment,
    ) {
        log.info("[CatalogEventConsumer] ${messages.size}건 수신")

        for (record in messages) {
            try {
                val envelope = objectMapper.readTree(record.value())
                val eventType = envelope.get("eventType").asText()
                val payload = objectMapper.readTree(envelope.get("payload").asText())

                when (eventType) {
                    "PRODUCT_LIKED" -> handleProductLiked(payload)
                    "PRODUCT_UNLIKED" -> handleProductUnliked(payload)
                    "PRODUCT_VIEWED" -> {} // 조회 이벤트는 commerce-streamer에서 처리
                    else -> log.warn("[CatalogEventConsumer] 알 수 없는 eventType: $eventType")
                }
            } catch (ex: Exception) {
                log.error("[CatalogEventConsumer] 처리 실패: offset=${record.offset()}, error=${ex.message}", ex)
            }
        }

        acknowledgment.acknowledge()
    }

    private fun handleProductLiked(payload: com.fasterxml.jackson.databind.JsonNode) {
        val productId = payload.get("productId").asLong()

        log.info("[CatalogEventConsumer] PRODUCT_LIKED: productId=$productId")

        try {
            productService.incrementLikeCount(productId)
        } catch (ex: Exception) {
            log.error("[CatalogEventConsumer] likeCount 증가 실패: productId=$productId, error=${ex.message}", ex)
        }

        try {
            productCacheService.evictProductDetail(productId)
            productCacheService.evictAllProductLists()
        } catch (ex: Exception) {
            log.error("[CatalogEventConsumer] 캐시 무효화 실패: productId=$productId, error=${ex.message}", ex)
        }
    }

    private fun handleProductUnliked(payload: com.fasterxml.jackson.databind.JsonNode) {
        val productId = payload.get("productId").asLong()

        log.info("[CatalogEventConsumer] PRODUCT_UNLIKED: productId=$productId")

        try {
            productService.decrementLikeCount(productId)
        } catch (ex: Exception) {
            log.error("[CatalogEventConsumer] likeCount 감소 실패: productId=$productId, error=${ex.message}", ex)
        }

        try {
            productCacheService.evictProductDetail(productId)
            productCacheService.evictAllProductLists()
        } catch (ex: Exception) {
            log.error("[CatalogEventConsumer] 캐시 무효화 실패: productId=$productId, error=${ex.message}", ex)
        }
    }
}
