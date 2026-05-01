package com.loopers.domain.ranking

import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 랭킹 ZSET Key 생성 정책 + TTL 정책.
 *
 * - **시간 양자화**: `ranking:all:{yyyyMMdd}` 일별 키로 분리해 윈도우별 랭킹을 운영한다.
 *   누적만 할 경우 오래된 인기 상품이 상위를 독식해 신상품 노출 기회가 사라지는 문제(Long Tail)를 방지.
 * - **TTL 2일**: 윈도우(1일)의 2배. 이전 일자 키 조회 가능 + 메모리 자동 회수.
 *
 * Why object (singleton)?
 *  - 순수 함수만 노출. 상태가 없으므로 의존성 주입 없이 호출하기 편리.
 */
object RankingKeyPolicy {

    private const val PREFIX = "ranking:all:"
    private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** 윈도우(1일)의 2배. 멘토 노트 권장: "시간 윈도우의 1.5~2배" */
    val TTL: Duration = Duration.ofDays(2)

    /** 주어진 일자에 해당하는 일간 랭킹 키를 생성한다. */
    fun dailyKey(date: LocalDate): String = "$PREFIX${date.format(FORMATTER)}"
}
