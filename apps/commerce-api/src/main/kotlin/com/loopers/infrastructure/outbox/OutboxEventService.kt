package com.loopers.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OutboxEventService(
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
    private val objectMapper: ObjectMapper,
) {

    @Transactional
    fun save(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        payload: Any,
        topic: String,
        partitionKey: String,
    ) {
        outboxEventJpaRepository.save(
            OutboxEventEntity(
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                eventType = eventType,
                payload = objectMapper.writeValueAsString(payload),
                topic = topic,
                partitionKey = partitionKey,
            ),
        )
    }

    fun findPending(): List<OutboxEventEntity> =
        outboxEventJpaRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)

    @Transactional
    fun markPublished(entity: OutboxEventEntity) {
        entity.markPublished()
        outboxEventJpaRepository.save(entity)
    }
}
