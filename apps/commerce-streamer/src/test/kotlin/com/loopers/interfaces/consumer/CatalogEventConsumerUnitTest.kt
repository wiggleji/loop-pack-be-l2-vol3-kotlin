package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.metrics.MetricsService
import com.loopers.application.ranking.RankingUpdater
import com.loopers.domain.ranking.RankingEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment

/**
 * S1 (멱등성) + Phase B (배치 flush) consumer 경계 계약.
 *
 * - 멱등 skip 된 record 는 ranking batch 에 들어가지 않는다 (중복 카운트 방지)
 * - 배치 안의 모든 적용 가능한 record 는 단 한 번의 [RankingUpdater.applyBatch] 호출로 flush
 * - Redis 예외는 ack 를 막지 않는다 (Eventual Consistency)
 */
@DisplayName("CatalogEventConsumer — Phase B batch + S1 idempotency")
class CatalogEventConsumerUnitTest {

    private val metricsService: MetricsService = mockk()
    private val rankingUpdater: RankingUpdater = mockk(relaxed = true)
    private val objectMapper = ObjectMapper()
    private val ack: Acknowledgment = mockk(relaxed = true)

    private val consumer = CatalogEventConsumer(metricsService, rankingUpdater, objectMapper)

    @Test
    fun `metrics 가 true 를 반환하면 ranking batch 에 포함되어 한 번에 flush 된다`() {
        every { metricsService.handleProductViewed("evt-1", 100L) } returns true
        val batchSlot = slot<List<RankingEvent>>()
        every { rankingUpdater.applyBatch(capture(batchSlot)) } returns Unit

        consumer.consume(listOf(record("evt-1", "PRODUCT_VIEWED", 100L)), ack)

        verify(exactly = 1) { rankingUpdater.applyBatch(any()) }
        assertThat(batchSlot.captured).containsExactly(RankingEvent.Viewed(100L))
        verify(exactly = 1) { ack.acknowledge() }
    }

    @Test
    fun `metrics 가 false 를 반환하면 batch 가 비어있어 applyBatch 가 호출되지 않는다`() {
        every { metricsService.handleProductViewed("evt-dup", 100L) } returns false

        consumer.consume(listOf(record("evt-dup", "PRODUCT_VIEWED", 100L)), ack)

        verify(exactly = 0) { rankingUpdater.applyBatch(any()) }
        verify(exactly = 1) { ack.acknowledge() }
    }

    @Test
    fun `같은 eventId 가 두 번 들어오면 첫 번째만 batch 에 포함된다`() {
        every { metricsService.handleProductLiked("evt-2", 200L) } returnsMany listOf(true, false)
        val batchSlot = slot<List<RankingEvent>>()
        every { rankingUpdater.applyBatch(capture(batchSlot)) } returns Unit

        consumer.consume(
            listOf(
                record("evt-2", "PRODUCT_LIKED", 200L),
                record("evt-2", "PRODUCT_LIKED", 200L),
            ),
            ack,
        )

        verify(exactly = 1) { rankingUpdater.applyBatch(any()) }
        assertThat(batchSlot.captured).containsExactly(RankingEvent.Liked(200L))
    }

    @Test
    fun `여러 이벤트 타입이 섞여 오면 모두 단일 batch 로 flush 된다`() {
        every { metricsService.handleProductViewed("v", 100L) } returns true
        every { metricsService.handleProductLiked("l", 100L) } returns true
        every { metricsService.handleProductUnliked("u", 100L) } returns true
        val batchSlot = slot<List<RankingEvent>>()
        every { rankingUpdater.applyBatch(capture(batchSlot)) } returns Unit

        consumer.consume(
            listOf(
                record("v", "PRODUCT_VIEWED", 100L),
                record("l", "PRODUCT_LIKED", 100L),
                record("u", "PRODUCT_UNLIKED", 100L),
            ),
            ack,
        )

        verify(exactly = 1) { rankingUpdater.applyBatch(any()) }
        assertThat(batchSlot.captured).containsExactlyInAnyOrder(
            RankingEvent.Viewed(100L),
            RankingEvent.Liked(100L),
            RankingEvent.Unliked(100L),
        )
    }

    @Test
    fun `같은 productId 에 여러 record 가 와도 batch 에 모두 포함되어 RankingUpdater 가 합산한다`() {
        // RankingUpdater.applyBatch 가 productId 별 합산을 책임지므로,
        // consumer 는 모든 적용 가능 record 를 그대로 batch 에 넣기만 한다.
        every { metricsService.handleProductViewed("v1", 100L) } returns true
        every { metricsService.handleProductViewed("v2", 100L) } returns true
        every { metricsService.handleProductViewed("v3", 100L) } returns true
        val batchSlot = slot<List<RankingEvent>>()
        every { rankingUpdater.applyBatch(capture(batchSlot)) } returns Unit

        consumer.consume(
            listOf(
                record("v1", "PRODUCT_VIEWED", 100L),
                record("v2", "PRODUCT_VIEWED", 100L),
                record("v3", "PRODUCT_VIEWED", 100L),
            ),
            ack,
        )

        verify(exactly = 1) { rankingUpdater.applyBatch(any()) }
        assertThat(batchSlot.captured).hasSize(3)
        assertThat(batchSlot.captured).allMatch { it == RankingEvent.Viewed(100L) }
    }

    @Test
    fun `Redis 실패가 발생해도 ack 는 호출되어 다음 메시지로 진행한다 (Eventual Consistency)`() {
        every { metricsService.handleProductViewed("evt-3", 100L) } returns true
        every { rankingUpdater.applyBatch(any()) } throws RuntimeException("redis down")

        consumer.consume(listOf(record("evt-3", "PRODUCT_VIEWED", 100L)), ack)

        verify(exactly = 1) { ack.acknowledge() }
    }

    @Test
    fun `알 수 없는 eventType 은 skip 되고 batch 가 비어 applyBatch 가 호출되지 않는다`() {
        consumer.consume(listOf(record("evt-x", "UNKNOWN_EVENT", 100L)), ack)

        verify(exactly = 0) { rankingUpdater.applyBatch(any()) }
        verify(exactly = 1) { ack.acknowledge() }
    }

    private fun record(eventId: String, eventType: String, productId: Long): ConsumerRecord<String, ByteArray> {
        val payload = """{"productId":$productId}"""
        val envelope = """
            {
              "eventId":"$eventId",
              "eventType":"$eventType",
              "payload":${objectMapper.writeValueAsString(payload)}
            }
        """.trimIndent()
        return ConsumerRecord("catalog-events", 0, 0L, eventId, envelope.toByteArray())
    }
}
