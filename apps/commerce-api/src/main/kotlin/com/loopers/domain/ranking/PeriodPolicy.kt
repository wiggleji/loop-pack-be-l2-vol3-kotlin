package com.loopers.domain.ranking

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

/**
 * 기간 식별자(yearWeek / yearMonth) 생성 정책 — commerce-api 측.
 *
 * **Cross-module invariant (NB):**
 * `apps/commerce-batch` 의 [com.loopers.domain.ranking.PeriodPolicy] 와 포맷이 정확히 일치해야 한다.
 * 한쪽 변경 시 양쪽 테스트 모두 깨지도록 검증 유지.
 *
 * 포맷:
 *  - yearWeek  : ISO-8601 — `YYYY-Www` (예: `2026-W16`)
 *  - yearMonth : `yyyy-MM` (예: `2026-04`)
 */
object PeriodPolicy {

    private val MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    fun yearWeek(date: LocalDate): String {
        val weekFields = WeekFields.ISO
        val week = date.get(weekFields.weekOfWeekBasedYear())
        val weekBasedYear = date.get(weekFields.weekBasedYear())
        return "%04d-W%02d".format(weekBasedYear, week)
    }

    fun yearMonth(date: LocalDate): String = date.format(MONTH_FORMATTER)
}
