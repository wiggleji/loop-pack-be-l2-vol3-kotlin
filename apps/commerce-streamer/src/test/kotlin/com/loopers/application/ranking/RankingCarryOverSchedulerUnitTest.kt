package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingEntry
import com.loopers.domain.ranking.RankingKeyPolicy
import com.loopers.domain.ranking.RankingQueryRepository
import com.loopers.domain.ranking.RankingRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@DisplayName("RankingCarryOverScheduler")
class RankingCarryOverSchedulerUnitTest {

    private val writeRepo: RankingRepository = mockk()
    private val readRepo: RankingQueryRepository = mockk()

    private val today = LocalDate.of(2026, 4, 8)
    private val tomorrow = today.plusDays(1)
    private val todayKey = RankingKeyPolicy.dailyKey(today)
    private val tomorrowKey = RankingKeyPolicy.dailyKey(tomorrow)

    private val fixedClock: Clock = Clock.fixed(
        today.atStartOfDay(ZoneId.systemDefault()).toInstant(),
        ZoneId.systemDefault(),
    )

    private lateinit var scheduler: RankingCarryOverScheduler

    @BeforeEach
    fun setUp() {
        every { writeRepo.batchIncrement(any(), any()) } just Runs
        every { writeRepo.expire(any(), any()) } just Runs
        scheduler = RankingCarryOverScheduler(writeRepo, readRepo, fixedClock)
    }

    @Test
    fun `오늘 TopN 을 내일 키에 0_001 가중치로 시드한다`() {
        every { readRepo.findTopN(todayKey, 0, 100) } returns listOf(
            RankingEntry(productId = 101L, score = 100.0),
            RankingEntry(productId = 102L, score = 50.0),
            RankingEntry(productId = 103L, score = 10.0),
        )

        scheduler.carryOverNow()

        verify(exactly = 1) {
            writeRepo.batchIncrement(
                tomorrowKey,
                mapOf(
                    101L to 0.1,
                    102L to 0.05,
                    103L to 0.01,
                ),
            )
        }
        verify(exactly = 1) { writeRepo.expire(tomorrowKey, RankingKeyPolicy.TTL) }
    }

    @Test
    fun `오늘 ZSET 이 비어있으면 batchIncrement 와 expire 를 호출하지 않는다`() {
        every { readRepo.findTopN(todayKey, 0, 100) } returns emptyList()

        scheduler.carryOverNow()

        verify(exactly = 0) { writeRepo.batchIncrement(any(), any()) }
        verify(exactly = 0) { writeRepo.expire(any(), any()) }
    }

    @Test
    fun `carry-over 가중치는 작아서 어제 1위가 오늘 한두 개 이벤트로 추월 가능하다`() {
        // 어제 1위 = score 1000.0 → carry-over score = 1.0 (≈ Liked 5건)
        every { readRepo.findTopN(todayKey, 0, 100) } returns listOf(
            RankingEntry(productId = 1L, score = 1000.0),
        )

        scheduler.carryOverNow()

        // 가중치 검증: 1000.0 × 0.001 = 1.0
        verify(exactly = 1) { writeRepo.batchIncrement(tomorrowKey, mapOf(1L to 1.0)) }
        // 즉, 내일 누군가 Liked 6건만 받으면 (0.2 × 6 = 1.2) 1위 자리가 바뀐다 — 가중치가 충분히 작다.
        assertThat(0.2 * 6).isGreaterThan(1.0)
    }

    @Test
    fun `임의의 from 일자로도 carry-over 가 가능하다 (수동 호출)`() {
        val customFrom = LocalDate.of(2026, 1, 1)
        val customTo = LocalDate.of(2026, 1, 2)
        every {
            readRepo.findTopN(RankingKeyPolicy.dailyKey(customFrom), 0, 100)
        } returns listOf(RankingEntry(productId = 999L, score = 10.0))

        scheduler.carryOver(customFrom, customTo)

        verify(exactly = 1) {
            writeRepo.batchIncrement(
                RankingKeyPolicy.dailyKey(customTo),
                mapOf(999L to 0.01),
            )
        }
    }
}
