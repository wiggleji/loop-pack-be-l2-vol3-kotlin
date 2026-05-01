package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.coupon.CouponIssueFacade
import com.loopers.config.kafka.KafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class CouponIssueConsumer(
    private val couponIssueFacade: CouponIssueFacade,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["coupon-issue-requests"],
        groupId = "commerce-api-coupon-issue",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(
        messages: List<ConsumerRecord<String, ByteArray>>,
        acknowledgment: Acknowledgment,
    ) {
        log.info("[CouponIssueConsumer] ${messages.size}건 수신")

        for (record in messages) {
            try {
                val envelope = objectMapper.readTree(record.value())
                val eventType = envelope.get("eventType").asText()
                val payload = objectMapper.readTree(envelope.get("payload").asText())

                when (eventType) {
                    "COUPON_ISSUE_REQUESTED" -> {
                        val requestId = payload.get("requestId").asLong()
                        val userId = payload.get("userId").asLong()
                        val couponTemplateId = payload.get("couponTemplateId").asLong()

                        couponIssueFacade.processIssue(requestId, userId, couponTemplateId)
                    }
                    else -> log.warn("[CouponIssueConsumer] 알 수 없는 eventType: $eventType")
                }
            } catch (ex: Exception) {
                log.error("[CouponIssueConsumer] 처리 실패: offset=${record.offset()}, error=${ex.message}", ex)
            }
        }

        acknowledgment.acknowledge()
    }
}
