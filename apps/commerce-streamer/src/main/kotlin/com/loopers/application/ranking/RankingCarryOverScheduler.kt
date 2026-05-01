package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingKeyPolicy
import com.loopers.domain.ranking.RankingQueryRepository
import com.loopers.domain.ranking.RankingRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

/**
 * 일간 랭킹 Carry-Over (이월) 스케줄러.
 *
 * ## 왜 필요한가
 * 매일 자정에 ZSET 키가 새로 생성되면 신규 진입 상품과 기존 인기 상품이 동등하게 0점에서 시작한다.
 * 그러면 아침 시간대 트래픽이 적을 때 "어제까지 1위였던 상품" 이 갑자기 사라지는 현상이 발생한다.
 * 이를 막기 위해 23:55 시점에 오늘 Top-N 의 점수를 낮은 가중치(=0.001)로 내일 키에 시드한다.
 *
 * ## 설계 결정
 * - **TopN + batchIncrement** 로 구현 (ZUNIONSTORE 를 별도 Redis primitive 로 도입하지 않음)
 *   → 기존 RankingRepository.batchIncrement (pipelined) 만으로 충분히 효율적이고, Port 가 깨끗하다.
 * - **23:55** — 자정 직전이므로 오늘의 거의 모든 활동이 반영된 시점이고, carry-over 가 끝나기 전에
 *   날짜가 바뀌어도 (00:00 의 첫 ZINCRBY 가 새 키를 생성한 뒤 carry-over 가 누적되도) 결과는 동일하다.
 * - **가중치 0.001** — 어제 1위가 오늘 약간의 부스팅을 받지만, 오늘 실제 활동 한 두 건이면
 *   금방 추월당할 정도로 작은 값. 너무 크면 "어제 인기" 가 영원히 살아남는다.
 * - **Top 100** — 페이지 5장 (size=20) 분량. 더 늘려도 효과 미미.
 * - **enabled flag** — 통합 테스트/로컬에서 의도치 않게 돌지 않도록 default false 로 두고
 *   운영 profile 에서만 true 로 켠다.
 */
@Component
@ConditionalOnProperty(prefix = "ranking.carry-over", name = ["enabled"], havingValue = "true")
class RankingCarryOverScheduler(
    private val rankingRepository: RankingRepository,
    private val rankingQueryRepository: RankingQueryRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 23:55 KST 에 매일 실행. 오늘 Top-N 을 내일 키에 0.001 가중치로 시드.
     */
    @Scheduled(cron = "0 55 23 * * *", zone = "Asia/Seoul")
    fun carryOverNow() {
        val today = LocalDate.now(clock)
        carryOver(today, today.plusDays(1))
    }

    /**
     * 테스트/수동 호출용 — 임의의 from→to 일자 조합에 대해 carry-over 를 실행한다.
     */
    fun carryOver(from: LocalDate, to: LocalDate) {
        val fromKey = RankingKeyPolicy.dailyKey(from)
        val toKey = RankingKeyPolicy.dailyKey(to)

        val topEntries = rankingQueryRepository.findTopN(fromKey, offset = 0, size = TOP_N)
        if (topEntries.isEmpty()) {
            log.info("[CarryOver] $fromKey 에 데이터 없음 — skip")
            return
        }

        val deltas = topEntries.associate { it.productId to it.score * CARRY_OVER_WEIGHT }
        rankingRepository.batchIncrement(toKey, deltas)
        rankingRepository.expire(toKey, RankingKeyPolicy.TTL)

        log.info(
            "[CarryOver] {} → {} : {}건 이월 (weight={})",
            fromKey,
            toKey,
            topEntries.size,
            CARRY_OVER_WEIGHT,
        )
    }

    companion object {
        const val TOP_N: Long = 100
        const val CARRY_OVER_WEIGHT: Double = 0.001
    }
}
