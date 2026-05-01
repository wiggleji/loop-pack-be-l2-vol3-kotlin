package com.loopers.domain.ranking

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate

/**
 * 랭킹 조회 기간 단위.
 *
 * 각 단위마다 다른 storage 가 적재한다:
 *  - DAILY   : streamer 가 Redis ZSET 에 실시간 적재 (ranking:all:yyyyMMdd)
 *  - WEEKLY  : batch (Chunk/Tasklet) 가 mv_product_rank_weekly 에 일괄 적재
 *  - MONTHLY : batch (Chunk/Tasklet) 가 mv_product_rank_monthly 에 일괄 적재
 *
 * Facade 가 이 enum 값을 보고 적절한 storage 로 dispatch.
 */
enum class RankingPeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
    ;

    /** 입력 baseDate 를 storage key 로 변환. */
    fun keyOf(date: LocalDate): String = when (this) {
        DAILY -> date.toString() // 실제 키는 RankingKeyPolicy 가 만든다 — yyyyMMdd 문자열은 호환용
        WEEKLY -> PeriodPolicy.yearWeek(date)
        MONTHLY -> PeriodPolicy.yearMonth(date)
    }

    companion object {
        fun parse(raw: String?): RankingPeriod {
            if (raw.isNullOrBlank()) return DAILY
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: throw CoreException(
                    errorType = ErrorType.BAD_REQUEST,
                    customMessage = "[$raw] period 는 DAILY|WEEKLY|MONTHLY 중 하나여야 합니다.",
                )
        }
    }
}
