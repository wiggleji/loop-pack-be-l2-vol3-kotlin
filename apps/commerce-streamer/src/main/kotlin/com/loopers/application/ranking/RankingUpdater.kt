package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingEvent
import com.loopers.domain.ranking.RankingKeyPolicy
import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.ScoreCalculator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Consumer 가 호출하는 랭킹 갱신 진입점.
 *
 * 책임:
 *  1. 이벤트 → 점수 델타 변환 (ScoreCalculator 위임)
 *  2. 오늘 일자 키 결정 (RankingKeyPolicy 위임)
 *  3. ZINCRBY 호출 (RankingRepository 위임)
 *  4. 새 키에 대한 TTL 설정 (멱등, JVM 내 캐싱으로 EXPIRE 호출 최소화)
 *
 * 두 가지 호출 모드를 제공한다:
 *  - [applyEvent]: 단건 ZINCRBY (Phase A) — 단순함, 테스트/edge-case 에 유리
 *  - [applyBatch]: productId 별로 메모리 합산 후 1회 batchIncrement (Phase B) — Hot-key skew 압축
 *
 * 실패 정책 (멘토 노트):
 *  - Redis 호출은 DB 트랜잭션 밖에서 일어남 (consumer 가 metrics → ranking 순서로 호출)
 *  - 본 컴포넌트에서 던진 예외는 호출자(Consumer) 의 try/catch 로 흡수되며, 메인 트랜잭션에 영향 없음
 */
@Component
class RankingUpdater(
    private val rankingRepository: RankingRepository,
    private val scoreCalculator: ScoreCalculator,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 이미 TTL 을 적용한 키를 캐싱해 매 호출마다 EXPIRE 가 발생하는 것을 막는다.
     * - 일자가 바뀌면 새 키가 들어와 자연스레 EXPIRE 가 1회 실행됨
     * - JVM 재시작 시 캐시는 비워지지만 EXPIRE 는 멱등이므로 무해
     * - 메모리 사용량은 상수에 가까움 (1 entry / day)
     */
    private val ttlAppliedKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * 이벤트 1건을 ZSET 에 반영한다 (Phase A).
     *
     * 호출 시점에 [LocalDate.now] 를 기준으로 키를 결정한다. 즉, 이벤트가 발생한 시각이
     * 아니라 처리되는 시각을 기준으로 분류된다 (윈도우 경계 처리 단순화).
     */
    fun applyEvent(event: RankingEvent) {
        val key = currentKey()
        val delta = scoreCalculator.scoreFor(event)

        if (delta == 0.0) {
            log.debug("[RankingUpdater] zero delta skipped: event={}", event)
            return
        }

        rankingRepository.incrementScore(key, event.productId, delta)
        ensureTtl(key)
    }

    /**
     * 이벤트 N건을 productId 단위로 합산해 1회 batchIncrement 로 반영한다 (Phase B).
     *
     * Hot-key skew 시나리오 (상위 20개 상품에 80% 트래픽 집중) 에서:
     *   N events → uniq M productIds 만큼의 ZINCRBY 호출 (M ≪ N)
     *
     * 동일 productId 에 view, like 등 여러 이벤트가 섞여 와도 점수가 합산되어 ZSET 일관성은 유지된다.
     * EXPIRE 는 [ensureTtl] 로 키별 1회만 호출.
     *
     * 빈 리스트 / 모든 델타가 0 / 모두 멱등 skip 된 경우 → no-op.
     */
    fun applyBatch(events: List<RankingEvent>) {
        if (events.isEmpty()) return

        val deltas = HashMap<Long, Double>(events.size)
        for (event in events) {
            val delta = scoreCalculator.scoreFor(event)
            if (delta == 0.0) continue
            deltas.merge(event.productId, delta, Double::plus)
        }

        if (deltas.isEmpty()) {
            log.debug("[RankingUpdater] applyBatch: 모든 델타가 0 — skip")
            return
        }

        val key = currentKey()
        rankingRepository.batchIncrement(key, deltas)
        ensureTtl(key)
        log.info("[RankingUpdater] applyBatch: {} events → {} ZINCRBY (압축률 {}%)",
            events.size, deltas.size, compressionPct(events.size, deltas.size))
    }

    private fun compressionPct(input: Int, output: Int): Int =
        if (input == 0) 0 else ((1.0 - output.toDouble() / input.toDouble()) * 100).toInt()

    private fun currentKey(): String = RankingKeyPolicy.dailyKey(LocalDate.now(clock))

    private fun ensureTtl(key: String) {
        if (ttlAppliedKeys.add(key)) {
            rankingRepository.expire(key, RankingKeyPolicy.TTL)
            log.info("[RankingUpdater] TTL applied to new key: {}", key)
        }
    }
}
