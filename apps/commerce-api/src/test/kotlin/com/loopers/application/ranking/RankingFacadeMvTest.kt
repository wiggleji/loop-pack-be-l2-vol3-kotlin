package com.loopers.application.ranking

import com.loopers.domain.catalog.brand.Brand
import com.loopers.domain.catalog.brand.BrandRepository
import com.loopers.domain.catalog.product.Product
import com.loopers.domain.catalog.product.ProductRepository
import com.loopers.domain.ranking.PeriodPolicy
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.infrastructure.ranking.MvProductRankMonthlyEntity
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository
import com.loopers.infrastructure.ranking.MvProductRankWeeklyEntity
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * RankingFacade 의 WEEKLY/MONTHLY 경로 검증.
 *
 * Daily(=Redis) 경로는 [RankingFacadeTest] 가 담당하고, 여기서는 MV 테이블 (배치 적재 산출물)
 * 을 직접 시드해 facade 가 올바르게 디스패치 하는지 + 응답 페이지의 periodKey/rank 정합성을 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
class RankingFacadeMvTest @Autowired constructor(
    private val rankingFacade: RankingFacade,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val weeklyRepository: MvProductRankWeeklyJpaRepository,
    private val monthlyRepository: MvProductRankMonthlyJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    private val baseDate: LocalDate = LocalDate.of(2026, 4, 14)
    private val baseDateString: String = baseDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("getRankingPage(period = WEEKLY)")
    inner class WeeklyRanking {

        @Test
        fun `MV 적재된 주간 TOP-N 을 rank ASC 페이지 단위로 반환한다`() {
            val brand = brandRepository.save(Brand(name = "Nike", description = "스포츠"))
            val products = (1..3).map {
                productRepository.save(Product(brandId = brand.id, name = "P$it", description = "d", price = 1000 * it))
            }
            val periodKey = PeriodPolicy.yearWeek(baseDate)
            // rank 1 = score 100, rank 2 = score 80, rank 3 = score 60
            weeklyRepository.saveAll(
                listOf(
                    weekly(productId = products[0].id, rank = 1, score = 100.0, periodKey = periodKey),
                    weekly(productId = products[1].id, rank = 2, score = 80.0, periodKey = periodKey),
                    weekly(productId = products[2].id, rank = 3, score = 60.0, periodKey = periodKey),
                ),
            )

            val result = rankingFacade.getRankingPage(
                period = RankingPeriod.WEEKLY,
                dateString = baseDateString,
                page = 1,
                size = 10,
            )

            assertAll(
                { assertThat(result.period).isEqualTo(RankingPeriod.WEEKLY) },
                { assertThat(result.periodKey).isEqualTo(periodKey) },
                { assertThat(result.totalCount).isEqualTo(3L) },
                { assertThat(result.items.map { it.productId }).containsExactly(products[0].id, products[1].id, products[2].id) },
                { assertThat(result.items.map { it.rank }).containsExactly(0L, 1L, 2L) }, // 0-based 전역 순위
                { assertThat(result.items.map { it.score }).containsExactly(100.0, 80.0, 60.0) },
                { assertThat(result.items[0].brandName).isEqualTo("Nike") },
            )
        }

        @Test
        fun `해당 주차에 데이터가 없으면 totalCount 0 + 빈 items 반환`() {
            val result = rankingFacade.getRankingPage(
                period = RankingPeriod.WEEKLY,
                dateString = baseDateString,
                page = 1,
                size = 10,
            )

            assertAll(
                { assertThat(result.periodKey).isEqualTo(PeriodPolicy.yearWeek(baseDate)) },
                { assertThat(result.totalCount).isEqualTo(0L) },
                { assertThat(result.items).isEmpty() },
            )
        }

        @Test
        fun `MV 에는 있으나 product 가 삭제된 경우 응답에서 제외된다`() {
            val brand = brandRepository.save(Brand(name = "Nike", description = "d"))
            val live = productRepository.save(Product(brandId = brand.id, name = "Live", description = "d", price = 100))
            val periodKey = PeriodPolicy.yearWeek(baseDate)
            weeklyRepository.saveAll(
                listOf(
                    weekly(productId = live.id, rank = 1, score = 50.0, periodKey = periodKey),
                    weekly(productId = 999_999L, rank = 2, score = 40.0, periodKey = periodKey), // 존재하지 않는 productId
                ),
            )

            val result = rankingFacade.getRankingPage(
                period = RankingPeriod.WEEKLY,
                dateString = baseDateString,
                page = 1,
                size = 10,
            )

            assertThat(result.items).hasSize(1)
            assertThat(result.items[0].productId).isEqualTo(live.id)
        }

        @Test
        fun `페이지 2 — offset 만큼 건너뛴 결과를 반환한다`() {
            val brand = brandRepository.save(Brand(name = "Nike", description = "d"))
            val products = (1..5).map {
                productRepository.save(Product(brandId = brand.id, name = "P$it", description = "d", price = 1000))
            }
            val periodKey = PeriodPolicy.yearWeek(baseDate)
            products.forEachIndexed { idx, p ->
                weeklyRepository.save(weekly(productId = p.id, rank = idx + 1, score = (5 - idx).toDouble(), periodKey = periodKey))
            }

            // size=2, page=2 → rank 3, 4 (0-based 2, 3)
            val result = rankingFacade.getRankingPage(
                period = RankingPeriod.WEEKLY,
                dateString = baseDateString,
                page = 2,
                size = 2,
            )

            assertAll(
                { assertThat(result.items).hasSize(2) },
                { assertThat(result.items.map { it.rank }).containsExactly(2L, 3L) },
                { assertThat(result.items.map { it.productId }).containsExactly(products[2].id, products[3].id) },
            )
        }
    }

    @Nested
    @DisplayName("getRankingPage(period = MONTHLY)")
    inner class MonthlyRanking {

        @Test
        fun `월간 TOP-N 을 yyyy-MM periodKey 로 반환한다`() {
            val brand = brandRepository.save(Brand(name = "Nike", description = "d"))
            val product = productRepository.save(Product(brandId = brand.id, name = "P1", description = "d", price = 1000))
            val periodKey = PeriodPolicy.yearMonth(baseDate)
            monthlyRepository.save(monthly(productId = product.id, rank = 1, score = 200.0, periodKey = periodKey))

            val result = rankingFacade.getRankingPage(
                period = RankingPeriod.MONTHLY,
                dateString = baseDateString,
                page = 1,
                size = 10,
            )

            assertAll(
                { assertThat(result.period).isEqualTo(RankingPeriod.MONTHLY) },
                { assertThat(result.periodKey).isEqualTo("2026-04") },
                { assertThat(result.items).hasSize(1) },
                { assertThat(result.items[0].productId).isEqualTo(product.id) },
                { assertThat(result.items[0].score).isEqualTo(200.0) },
            )
        }
    }

    @Nested
    @DisplayName("RankingPeriod.parse()")
    inner class PeriodParsing {

        @Test
        fun `null 또는 blank 는 DAILY 로 해석된다`() {
            assertThat(RankingPeriod.parse(null)).isEqualTo(RankingPeriod.DAILY)
            assertThat(RankingPeriod.parse("")).isEqualTo(RankingPeriod.DAILY)
            assertThat(RankingPeriod.parse("  ")).isEqualTo(RankingPeriod.DAILY)
        }

        @Test
        fun `대소문자 무관하게 인식된다`() {
            assertThat(RankingPeriod.parse("weekly")).isEqualTo(RankingPeriod.WEEKLY)
            assertThat(RankingPeriod.parse("MONTHLY")).isEqualTo(RankingPeriod.MONTHLY)
            assertThat(RankingPeriod.parse("Daily")).isEqualTo(RankingPeriod.DAILY)
        }

        @Test
        fun `정의되지 않은 값은 BAD_REQUEST 로 거절`() {
            val ex = assertThrows<CoreException> { RankingPeriod.parse("yearly") }
            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    private fun weekly(productId: Long, rank: Int, score: Double, periodKey: String) =
        MvProductRankWeeklyEntity(
            productId = productId,
            rank = rank,
            score = score,
            viewCount = 0,
            likeCount = 0,
            salesCount = 0,
            salesAmount = 0,
            periodKey = periodKey,
        )

    private fun monthly(productId: Long, rank: Int, score: Double, periodKey: String) =
        MvProductRankMonthlyEntity(
            productId = productId,
            rank = rank,
            score = score,
            viewCount = 0,
            likeCount = 0,
            salesCount = 0,
            salesAmount = 0,
            periodKey = periodKey,
        )
}
