package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingEntry
import com.loopers.domain.ranking.RankingMvQueryRepository
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

/**
 * MV 테이블 어댑터.
 *
 * WEEKLY/MONTHLY 두 테이블이 동일한 row shape (productId/rank/score/periodKey) 를 공유하므로
 * 메서드 시그니처는 한 벌이지만, 내부에서 [RankingPeriod] 로 분기해 해당 JPA 리포지토리를 호출.
 *
 * Why DAILY 가 빠져있는가?
 *  - DAILY 는 Redis ZSET (streamer 적재) 가 source of truth.
 *  - MV port 에 DAILY 를 끼워넣으면 "MV가 아닌 storage 를 MV port 가 다룬다" 는 모순.
 */
@Repository
class MvRankingQueryRepositoryImpl(
    private val weeklyRepository: MvProductRankWeeklyJpaRepository,
    private val monthlyRepository: MvProductRankMonthlyJpaRepository,
) : RankingMvQueryRepository {

    override fun findPage(period: RankingPeriod, periodKey: String, offset: Long, size: Long): List<RankingEntry> {
        require(size > 0) { "size 는 1 이상이어야 합니다." }
        // Spring Data Pageable 은 page 단위라 offset 을 직접 받지 못함 → 정수 나누어 떨어지는 page 로 변환.
        // facade 가 (page-1)*size 로 만든 offset 을 그대로 다시 page 로 환산.
        val pageNumber = (offset / size).toInt()
        val pageRequest = PageRequest.of(pageNumber, size.toInt())

        return when (period) {
            RankingPeriod.WEEKLY ->
                weeklyRepository.findByPeriodKeyOrderByRankAsc(periodKey, pageRequest)
                    .map { RankingEntry(productId = it.productId, score = it.score) }
            RankingPeriod.MONTHLY ->
                monthlyRepository.findByPeriodKeyOrderByRankAsc(periodKey, pageRequest)
                    .map { RankingEntry(productId = it.productId, score = it.score) }
            RankingPeriod.DAILY -> throw unsupported(period)
        }
    }

    override fun count(period: RankingPeriod, periodKey: String): Long = when (period) {
        RankingPeriod.WEEKLY -> weeklyRepository.countByPeriodKey(periodKey)
        RankingPeriod.MONTHLY -> monthlyRepository.countByPeriodKey(periodKey)
        RankingPeriod.DAILY -> throw unsupported(period)
    }

    override fun findRank(period: RankingPeriod, periodKey: String, productId: Long): Int? = when (period) {
        RankingPeriod.WEEKLY ->
            weeklyRepository.findByPeriodKeyAndProductId(periodKey, productId)?.rank
        RankingPeriod.MONTHLY ->
            monthlyRepository.findByPeriodKeyAndProductId(periodKey, productId)?.rank
        RankingPeriod.DAILY -> throw unsupported(period)
    }

    private fun unsupported(period: RankingPeriod) = CoreException(
        errorType = ErrorType.BAD_REQUEST,
        customMessage = "[$period] period 는 MV 어댑터에서 지원되지 않습니다 (DAILY 는 Redis 사용).",
    )
}
