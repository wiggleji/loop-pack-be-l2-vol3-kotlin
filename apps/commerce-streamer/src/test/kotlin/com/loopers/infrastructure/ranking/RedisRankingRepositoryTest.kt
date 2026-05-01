package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingKeyPolicy
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.time.LocalDate

@SpringBootTest
@ActiveProfiles("test")
class RedisRankingRepositoryTest @Autowired constructor(
    private val repository: RedisRankingRepository,
    private val redisCleanUp: RedisCleanUp,
) {

    private val testDate: LocalDate = LocalDate.of(2026, 4, 8)
    private val key: String = RankingKeyPolicy.dailyKey(testDate)

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @Nested
    @DisplayName("incrementScore() — 단건 ZINCRBY")
    inner class IncrementScore {

        @Test
        fun `최초 호출 시 새 멤버가 score 와 함께 생성된다`() {
            repository.incrementScore(key, productId = 100L, delta = 1.5)

            assertThat(repository.findScore(key, 100L)).isEqualTo(1.5)
            assertThat(repository.count(key)).isEqualTo(1L)
        }

        @Test
        fun `같은 productId 로 여러 번 호출하면 score 가 누적된다`() {
            repository.incrementScore(key, productId = 100L, delta = 0.1)
            repository.incrementScore(key, productId = 100L, delta = 0.2)
            repository.incrementScore(key, productId = 100L, delta = 0.7)

            assertThat(repository.findScore(key, 100L)!!).isEqualTo(1.0, Offset.offset(1e-9))
        }

        @Test
        fun `음수 델타는 score 를 차감시킨다 (좋아요 취소 시나리오)`() {
            repository.incrementScore(key, productId = 100L, delta = 1.0)
            repository.incrementScore(key, productId = 100L, delta = -0.4)

            assertThat(repository.findScore(key, 100L)!!).isEqualTo(0.6, Offset.offset(1e-9))
        }
    }

    @Nested
    @DisplayName("batchIncrement() — Phase B 파이프라인")
    inner class BatchIncrement {

        @Test
        fun `여러 productId 의 델타를 한 번에 반영한다`() {
            val deltas = mapOf(
                101L to 0.1,
                102L to 0.5,
                103L to 1.2,
            )

            repository.batchIncrement(key, deltas)

            assertThat(repository.findScore(key, 101L)!!).isEqualTo(0.1, Offset.offset(1e-9))
            assertThat(repository.findScore(key, 102L)!!).isEqualTo(0.5, Offset.offset(1e-9))
            assertThat(repository.findScore(key, 103L)!!).isEqualTo(1.2, Offset.offset(1e-9))
            assertThat(repository.count(key)).isEqualTo(3L)
        }

        @Test
        fun `같은 키에 두 번 호출하면 score 가 누적된다`() {
            repository.batchIncrement(key, mapOf(101L to 0.5))
            repository.batchIncrement(key, mapOf(101L to 0.5))

            assertThat(repository.findScore(key, 101L)!!).isEqualTo(1.0, Offset.offset(1e-9))
        }

        @Test
        fun `빈 맵이 들어오면 no-op (예외 없음)`() {
            repository.batchIncrement(key, emptyMap())
            assertThat(repository.count(key)).isEqualTo(0L)
        }

        @Test
        fun `Phase A 단건 호출과 Phase B 배치 호출의 최종 결과가 동일하다`() {
            // 같은 입력을 두 가지 모드로 적용했을 때 ZSET 스냅샷이 동일해야 한다.
            val keyA = "ranking:test:phase-a"
            val keyB = "ranking:test:phase-b"
            val events = listOf(
                101L to 0.1,
                102L to 0.2,
                101L to 0.3, // 같은 product 누적
                103L to 1.0,
                102L to 0.1,
            )

            // Phase A
            events.forEach { (id, delta) -> repository.incrementScore(keyA, id, delta) }

            // Phase B (메모리에서 productId 별 합산 후 반영)
            val aggregated = events.groupingBy { it.first }
                .fold(0.0) { acc, (_, delta) -> acc + delta }
            repository.batchIncrement(keyB, aggregated)

            // 비교
            listOf(101L, 102L, 103L).forEach { productId ->
                val a = repository.findScore(keyA, productId)!!
                val b = repository.findScore(keyB, productId)!!
                assertThat(a).isEqualTo(b, Offset.offset(1e-9))
            }
        }
    }

    @Nested
    @DisplayName("findTopN() — ZREVRANGE")
    inner class FindTopN {

        @Test
        fun `score 내림차순으로 상위 N 개를 반환한다`() {
            repository.batchIncrement(
                key,
                mapOf(101L to 1.0, 102L to 5.0, 103L to 3.0, 104L to 2.0, 105L to 4.0),
            )

            val top3 = repository.findTopN(key, offset = 0, size = 3)

            assertThat(top3.map { it.productId }).containsExactly(102L, 105L, 103L)
            assertThat(top3[0].score).isEqualTo(5.0)
        }

        @Test
        fun `offset 으로 페이징이 가능하다`() {
            repository.batchIncrement(
                key,
                mapOf(101L to 1.0, 102L to 5.0, 103L to 3.0, 104L to 2.0, 105L to 4.0),
            )

            val page2 = repository.findTopN(key, offset = 2, size = 2)

            assertThat(page2.map { it.productId }).containsExactly(103L, 104L)
        }

        @Test
        fun `존재하지 않는 키 조회 시 빈 리스트를 반환한다`() {
            val result = repository.findTopN("ranking:non-existent", offset = 0, size = 10)
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("findRank() — ZREVRANK")
    inner class FindRank {

        @Test
        fun `score 가 높을수록 낮은 rank 를 반환한다 (0-based)`() {
            repository.batchIncrement(key, mapOf(101L to 1.0, 102L to 5.0, 103L to 3.0))

            assertThat(repository.findRank(key, 102L)).isEqualTo(0L)
            assertThat(repository.findRank(key, 103L)).isEqualTo(1L)
            assertThat(repository.findRank(key, 101L)).isEqualTo(2L)
        }

        @Test
        fun `존재하지 않는 멤버는 null 을 반환한다`() {
            repository.incrementScore(key, productId = 100L, delta = 1.0)
            assertThat(repository.findRank(key, productId = 999L)).isNull()
        }
    }

    @Nested
    @DisplayName("expire() — TTL 정책")
    inner class Expire {

        @Test
        fun `expire 호출 후 짧은 TTL 이 만료되면 키가 사라진다`() {
            repository.incrementScore(key, productId = 100L, delta = 1.0)
            repository.expire(key, Duration.ofSeconds(1))

            assertThat(repository.count(key)).isEqualTo(1L)

            // TTL 이 만료될 때까지 대기
            Thread.sleep(1500)

            assertThat(repository.count(key)).isEqualTo(0L)
            assertThat(repository.findScore(key, 100L)).isNull()
        }

        @Test
        fun `expire 는 멱등 호출 가능 (여러 번 호출해도 안전)`() {
            repository.incrementScore(key, productId = 100L, delta = 1.0)
            repository.expire(key, RankingKeyPolicy.TTL)
            repository.expire(key, RankingKeyPolicy.TTL)
            repository.expire(key, RankingKeyPolicy.TTL)

            assertThat(repository.count(key)).isEqualTo(1L)
        }
    }

    @Nested
    @DisplayName("RankingKeyPolicy")
    inner class KeyPolicy {

        @Test
        fun `dailyKey 는 ranking_all_yyyyMMdd 형식이다`() {
            val key = RankingKeyPolicy.dailyKey(LocalDate.of(2026, 4, 8))
            assertThat(key).isEqualTo("ranking:all:20260408")
        }

        @Test
        fun `TTL 은 2일이다 (윈도우의 2배)`() {
            assertThat(RankingKeyPolicy.TTL).isEqualTo(Duration.ofDays(2))
        }
    }
}
