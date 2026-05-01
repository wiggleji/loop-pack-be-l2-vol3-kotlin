package com.loopers.domain.ranking

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * 기간 식별자(yearWeek / yearMonth) 생성 정책.
 *
 * MV 테이블의 partition key 로 사용되며, batch / api 양쪽이 동일한 포맷을 공유해야
 * 같은 주(week)/달(month) 데이터를 가리킨다. 따라서 단일 책임의 객체로 분리.
 *
 * 포맷:
 *  - yearWeek  : ISO-8601 주차 표현 — `YYYY-Www` (예: `2026-W16`)
 *    · 한국 캘린더 관행과 다소 다르지만 (월요일 시작) 표준 준수가 운영적으로 안전함.
 *  - yearMonth : `yyyy-MM` (예: `2026-04`)
 */
object PeriodPolicy {

    private val MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    /** ISO-8601 기준 주차. 일요일은 같은 주에 포함되도록 ISO 표준 사용. */
    fun yearWeek(date: LocalDate): String {
        val weekFields = WeekFields.ISO
        val week = date.get(weekFields.weekOfWeekBasedYear())
        val weekBasedYear = date.get(weekFields.weekBasedYear())
        return "%04d-W%02d".format(weekBasedYear, week)
    }

    fun yearMonth(date: LocalDate): String = date.format(MONTH_FORMATTER)

    /**
     * 입력 yearWeek 의 시작 날짜 (월요일).
     * `2026-W16` → `2026-04-13`
     */
    fun firstDayOfWeek(yearWeek: String): LocalDate {
        val (year, week) = parseYearWeek(yearWeek)
        return LocalDate.now()
            .withYear(year)
            .with(WeekFields.ISO.weekBasedYear(), year.toLong())
            .with(WeekFields.ISO.weekOfWeekBasedYear(), week.toLong())
            .with(WeekFields.ISO.dayOfWeek(), 1)
    }

    /** yearMonth 의 시작 날짜 (1일). */
    fun firstDayOfMonth(yearMonth: String): LocalDate {
        val parts = yearMonth.split("-")
        require(parts.size == 2) { "[$yearMonth] yyyy-MM 형식이어야 합니다." }
        return LocalDate.of(parts[0].toInt(), parts[1].toInt(), 1)
    }

    private fun parseYearWeek(yearWeek: String): Pair<Int, Int> {
        val match = WEEK_REGEX.matchEntire(yearWeek)
            ?: throw IllegalArgumentException("[$yearWeek] yyyy-Www 형식이어야 합니다.")
        return match.groupValues[1].toInt() to match.groupValues[2].toInt()
    }

    private val WEEK_REGEX = Regex("(\\d{4})-W(\\d{2})")

    @Suppress("unused") // 향후 다국어 ISO 처리 확장 여지
    private val isoLocale: Locale = Locale.ROOT
}
