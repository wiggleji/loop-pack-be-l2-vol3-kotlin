package com.loopers.application.ranking

import com.loopers.domain.catalog.brand.BrandRepository
import com.loopers.domain.catalog.product.ProductRepository
import com.loopers.domain.ranking.PeriodPolicy
import com.loopers.domain.ranking.RankingEntry
import com.loopers.domain.ranking.RankingKeyPolicy
import com.loopers.domain.ranking.RankingMvQueryRepository
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingQueryRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 랭킹 조회 Application Facade.
 *
 * 책임:
 *  - 입력 검증 (date 포맷, page/size 범위, period 값)
 *  - period 별 storage 디스패치:
 *      · DAILY   → Redis ZSET ([RankingQueryRepository])
 *      · WEEKLY  → MV 테이블  ([RankingMvQueryRepository])
 *      · MONTHLY → MV 테이블  ([RankingMvQueryRepository])
 *  - productId 페이지 → product/brand 일괄 조회 (N+1 방지) — period 와 무관한 공통 로직
 *  - 단건 상품의 현재 순위 조회
 */
@Service
class RankingFacade(
    private val rankingQueryRepository: RankingQueryRepository,
    private val rankingMvQueryRepository: RankingMvQueryRepository,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
) {

    companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        private const val MAX_PAGE_SIZE = 100
    }

    /**
     * 랭킹 페이지 조회.
     *
     * @param period     DAILY|WEEKLY|MONTHLY (null/blank → DAILY)
     * @param dateString yyyyMMdd. null 이면 오늘.
     * @param page       1-based 페이지 번호 (최소 1)
     * @param size       페이지 크기 (1 ~ 100)
     */
    fun getRankingPage(period: RankingPeriod, dateString: String?, page: Int, size: Int): RankingPageResult {
        val date = parseDate(dateString)
        validatePageParams(page, size)

        val offset = ((page - 1).toLong()) * size.toLong()
        val (totalCount, entries, periodKey) = loadEntries(period, date, offset, size.toLong())

        if (entries.isEmpty()) {
            return RankingPageResult(
                period = period,
                periodKey = periodKey,
                date = date.format(DATE_FORMATTER),
                page = page,
                size = size,
                totalCount = totalCount,
                items = emptyList(),
            )
        }

        // period 무관 — productId 페이지 → product/brand IN 쿼리 1회씩
        val productIds = entries.map { it.productId }
        val productMap = productRepository.findAllByIds(productIds).associateBy { it.id }
        val brandIds = productMap.values.map { it.brandId }.distinct()
        val brandMap = brandRepository.findAllByIds(brandIds).associateBy { it.id }

        val items = entries.mapIndexedNotNull { index, entry ->
            val product = productMap[entry.productId] ?: return@mapIndexedNotNull null // 삭제된 상품 skip
            val brand = brandMap[product.brandId]
            RankingItemResult(
                rank = offset + index, // 0-based 전역 순위
                score = entry.score,
                productId = product.id,
                name = product.name,
                price = product.price,
                likeCount = product.likeCount,
                brandId = product.brandId,
                brandName = brand?.name ?: "",
            )
        }

        return RankingPageResult(
            period = period,
            periodKey = periodKey,
            date = date.format(DATE_FORMATTER),
            page = page,
            size = size,
            totalCount = totalCount,
            items = items,
        )
    }

    /**
     * 특정 상품의 오늘(DAILY) 랭킹 순위 (0-based, 없으면 null).
     *
     * 상품 상세 응답에 포함되어 매 호출 Redis 1 round-trip — 향후 short TTL 캐시 여지.
     */
    fun findTodayRank(productId: Long): Long? {
        val key = RankingKeyPolicy.dailyKey(LocalDate.now())
        return rankingQueryRepository.findRank(key, productId)
    }

    private data class EntriesLoad(
        val totalCount: Long,
        val entries: List<RankingEntry>,
        val periodKey: String,
    )

    private fun loadEntries(period: RankingPeriod, date: LocalDate, offset: Long, size: Long): EntriesLoad =
        when (period) {
            RankingPeriod.DAILY -> {
                val key = RankingKeyPolicy.dailyKey(date)
                EntriesLoad(
                    totalCount = rankingQueryRepository.count(key),
                    entries = rankingQueryRepository.findTopN(key, offset, size),
                    periodKey = date.format(DATE_FORMATTER),
                )
            }
            RankingPeriod.WEEKLY -> {
                val periodKey = PeriodPolicy.yearWeek(date)
                EntriesLoad(
                    totalCount = rankingMvQueryRepository.count(period, periodKey),
                    entries = rankingMvQueryRepository.findPage(period, periodKey, offset, size),
                    periodKey = periodKey,
                )
            }
            RankingPeriod.MONTHLY -> {
                val periodKey = PeriodPolicy.yearMonth(date)
                EntriesLoad(
                    totalCount = rankingMvQueryRepository.count(period, periodKey),
                    entries = rankingMvQueryRepository.findPage(period, periodKey, offset, size),
                    periodKey = periodKey,
                )
            }
        }

    private fun parseDate(dateString: String?): LocalDate {
        if (dateString.isNullOrBlank()) return LocalDate.now()
        return try {
            LocalDate.parse(dateString, DATE_FORMATTER)
        } catch (ex: DateTimeParseException) {
            throw CoreException(
                errorType = ErrorType.BAD_REQUEST,
                customMessage = "[$dateString] date 는 yyyyMMdd 형식이어야 합니다.",
            )
        }
    }

    private fun validatePageParams(page: Int, size: Int) {
        if (page < 1) {
            throw CoreException(
                errorType = ErrorType.BAD_REQUEST,
                customMessage = "[$page] page 는 1 이상이어야 합니다.",
            )
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw CoreException(
                errorType = ErrorType.BAD_REQUEST,
                customMessage = "[$size] size 는 1 이상 $MAX_PAGE_SIZE 이하여야 합니다.",
            )
        }
    }
}
