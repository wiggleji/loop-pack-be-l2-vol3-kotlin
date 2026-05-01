package com.loopers.domain.ranking

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 랭킹 ZSET Key 명명 규칙.
 *
 * **WARNING — Cross-module invariant:**
 * `apps/commerce-streamer` 의 [com.loopers.domain.ranking.RankingKeyPolicy] 와
 * 키 포맷이 정확히 일치해야 한다 (`ranking:all:{yyyyMMdd}`).
 * 한쪽을 변경할 경우 반드시 양쪽 테스트가 함께 실패하도록 검증을 유지한다.
 *
 * 두 앱이 같은 redis 모듈을 공유하지만, 도메인 명명 규칙을 infra 모듈에 두는 것은
 * 책임 위반이라 판단해 양쪽 도메인에 동일 정의를 두는 방식을 택했다.
 *
 * commerce-api 측은 조회 전용이므로 TTL 정책은 정의하지 않는다 (쓰기 책임은 streamer).
 */
object RankingKeyPolicy {

    private const val PREFIX = "ranking:all:"
    private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun dailyKey(date: LocalDate): String = "$PREFIX${date.format(FORMATTER)}"
}
