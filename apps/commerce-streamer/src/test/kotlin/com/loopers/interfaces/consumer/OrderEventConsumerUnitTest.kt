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
 * S1 (멱등성) + Phase B (배치 flush) — Order consumer 경계 계약.
 *
 * - 각 ORDER_PLACED record 의 모든 item 이 단일 batch 로 모임 (Ordered amount = price × qty)
 * - metrics false → 해당 record 의 모든 item 이 batch 에서 제외됨
 * - 같은 productId 가 여러 주문에 걸쳐 있어도 모두 batch 에 들어가고 RankingUpdater 가 합산
 */
@DisplayName("OrderEventConsumer — Phase B batch + S1 idempotency")
class OrderEventConsumerUnitTest {

    private val metricsService: MetricsService = mockk()
    private val rankingUpdater: RankingUpdater = mockk(relaxed = true)
    private val objectMapper = ObjectMapper()
    private val ack: Acknowledgment = mockk(relaxed = true)

    private val consumer = OrderEventConsumer(metricsService, rankingUpdater, objectMapper)

    @Test
    fun `metrics 가 true 면 모든 item 이 단일 batch 로 flush 된다 (amount = price × qty)`() {
        every { metricsService.handleOrderPlaced(eq("evt-1"), any()) } returns true
        val batchSlot = slot<List<RankingEvent>>()
        every { rankingUpdater.applyBatch(capture(batchSlot)) } returns Unit

        consumer.consume(
            listOf(
                record(
                    eventId = "evt-1",
                    items = listOf(Triple(100L, 2, 5_000), Triple(200L, 1, 30_000)),
                ),
            ),
            ack,
        )

        verify(exactly = 1) { rankingUpdater.applyBatch(any()) }
        assertThat(batchSlot.captured).containsExactlyInAnyOrder(
            RankingEvent.Ordered(100L, amount = 10_000L),
            RankingEvent.Ordered(200L, amount = 30_000L),
        )
        verify(exactly = 1) { ack.acknowledge() }
    }

    @Test
    fun `metrics 가 false 면 batch 가 비어 applyBatch 가 호출되지 않는다`() {
        every { metricsService.handleOrderPlaced(eq("evt-dup"), any()) } returns false

        consumer.consume(
            listOf(
                record(
                    eventId = "evt-dup",
                    items = listOf(Triple(100L, 2, 5_000), Triple(200L, 1, 30_000)),
                ),
            ),
            ack,
        )

        verify(exactly = 0) { rankingUpdater.applyBatch(any()) }
        verify(exactly = 1) { ack.acknowledge() }
    }

    @Test
    fun `여러 주문 record 의 같은 productId 도 모두 batch 에 들어간다 (RankingUpdater 가 합산)`() {
        every { metricsService.handleOrderPlaced(eq("e1"), any()) } returns true
        every { metricsService.handleOrderPlaced(eq("e2"), any()) } returns true
        val batchSlot = slot<List<RankingEvent>>()
        every { rankingUpdater.applyBatch(capture(batchSlot)) } returns Unit

        consumer.consume(
            listOf(
                record("e1", listOf(Triple(100L, 1, 10_000))),
                record("e2", listOf(Triple(100L, 2, 10_000))),
            ),
            ack,
        )

        verify(exactly = 1) { rankingUpdater.applyBatch(any()) }
        assertThat(batchSlot.captured).containsExactly(
            RankingEvent.Ordered(100L, amount = 10_000L),
            RankingEvent.Ordered(100L, amount = 20_000L),
        )
    }

    @Test
    fun `Redis 갱신 중 예외가 나도 ack 는 정상 호출된다`() {
        every { metricsService.handleOrderPlaced(any(), any()) } returns true
        every { rankingUpdater.applyBatch(any()) } throws RuntimeException("redis down")

        consumer.consume(
            listOf(record("evt-2", listOf(Triple(100L, 1, 10_000)))),
            ack,
        )

        verify(exactly = 1) { ack.acknowledge() }
    }

    private fun record(
        eventId: String,
        items: List<Triple<Long, Int, Int>>,
    ): ConsumerRecord<String, ByteArray> {
        val itemsJson = items.joinToString(",", "[", "]") { (productId, qty, price) ->
            """{"productId":$productId,"quantity":$qty,"price":$price}"""
        }
        val payload = """{"items":$itemsJson}"""
        val envelope = """
            {
              "eventId":"$eventId",
              "eventType":"ORDER_PLACED",
              "payload":${objectMapper.writeValueAsString(payload)}
            }
        """.trimIndent()
        return ConsumerRecord("order-events", 0, 0L, eventId, envelope.toByteArray())
    }
}
