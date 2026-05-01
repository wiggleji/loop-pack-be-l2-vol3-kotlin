package com.loopers.infrastructure.outbox

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OutboxRelay(
    private val outboxEventService: OutboxEventService,
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 5_000)
    fun relay() {
        val pending = outboxEventService.findPending()
        if (pending.isEmpty()) return

        log.info("[OutboxRelay] ${pending.size}건의 이벤트 발행 시작")

        for (event in pending) {
            try {
                val envelope = mapOf(
                    "eventId" to event.id.toString(),
                    "eventType" to event.eventType,
                    "aggregateType" to event.aggregateType,
                    "aggregateId" to event.aggregateId,
                    "payload" to event.payload,
                    "createdAt" to event.createdAt.toString(),
                )

                kafkaTemplate.send(event.topic, event.partitionKey, envelope)
                    .whenComplete { _, ex ->
                        if (ex == null) {
                            outboxEventService.markPublished(event)
                            log.debug("[OutboxRelay] 발행 완료: id=${event.id}, topic=${event.topic}, type=${event.eventType}")
                        } else {
                            log.error("[OutboxRelay] 발행 실패: id=${event.id}, error=${ex.message}", ex)
                        }
                    }
            } catch (ex: Exception) {
                log.error("[OutboxRelay] 발행 실패: id=${event.id}, error=${ex.message}", ex)
            }
        }
    }
}
