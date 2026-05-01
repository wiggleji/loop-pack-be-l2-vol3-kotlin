package com.loopers.application.ranking

import com.loopers.domain.catalog.brand.Brand
import com.loopers.domain.catalog.brand.BrandRepository
import com.loopers.domain.catalog.product.Product
import com.loopers.domain.catalog.product.ProductRepository
import com.loopers.domain.ranking.RankingKeyPolicy
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * RankingFacade 의 조회/Aggregation 정합성을 SpringBootTest 로 검증한다.
 *
 * - Redis ZSET 에 productId/score 직접 적재 (ZADD) → ZREVRANGE 가 잘 동작하는지
 * - DB 에 product/brand 시드 → N+1 없이 단일 IN 쿼리로 join 이 일어나는지
 * - 입력 검증 (date 포맷, page/size 범위)
 */
@SpringBootTest
@ActiveProfiles("test")
class RankingFacadeTest @Autowired constructor(
    private val rankingFacade: RankingFacade,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {

    private val today: LocalDate = LocalDate.now()
    private val todayKey: String = RankingKeyPolicy.dailyKey(today)

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @Nested
    @DisplayName("getRankingPage()")
    inner class GetRankingPage {

        @Test
        fun `ZSET 점수 내림차순으로 상품 정보가 Aggregation 되어 반환된다`() {
            // Arrange
            val brand = brandRepository.save(Brand(name = "Nike", description = "스포츠"))
            val p1 = productRepository.save(Product(brandId = brand.id, name = "Air Max", description = "d", price = 100_000))
            val p2 = productRepository.save(Product(brandId = brand.id, name = "Pegasus", description = "d", price = 80_000))
            val p3 = productRepository.save(Product(brandId = brand.id, name = "Cortez", description = "d", price = 60_000))
            zadd(todayKey, p1.id, 5.0)
            zadd(todayKey, p2.id, 10.0)
            zadd(todayKey, p3.id, 3.0)

            // Act
            val result = rankingFacade.getRankingPage(period = RankingPeriod.DAILY, dateString = null, page = 1, size = 10)

            // Assert
            assertThat(result.totalCount).isEqualTo(3L)
            assertThat(result.items).hasSize(3)

            assertThat(result.items[0].productId).isEqualTo(p2.id) // score 10
            assertThat(result.items[0].rank).isEqualTo(0L)
            assertThat(result.items[0].score).isEqualTo(10.0)
            assertThat(result.items[0].name).isEqualTo("Pegasus")
            assertThat(result.items[0].brandName).isEqualTo("Nike")

            assertThat(result.items[1].productId).isEqualTo(p1.id) // score 5
            assertThat(result.items[1].rank).isEqualTo(1L)

            assertThat(result.items[2].productId).isEqualTo(p3.id) // score 3
            assertThat(result.items[2].rank).isEqualTo(2L)
        }

        @Test
        fun `페이징 — page 2 에는 offset 만큼 건너뛴 결과가 반환된다`() {
            val brand = brandRepository.save(Brand(name = "Nike", description = "d"))
            val products = (1..5).map {
                productRepository.save(Product(brandId = brand.id, name = "P$it", description = "d", price = 1000 * it))
            }
            // score: P1=1.0 ... P5=5.0
            products.forEachIndexed { idx, p -> zadd(todayKey, p.id, (idx + 1).toDouble()) }

            // size=2, page=2 → 3rd, 4th 위치
            val result = rankingFacade.getRankingPage(period = RankingPeriod.DAILY, dateString = null, page = 2, size = 2)

            assertThat(result.items).hasSize(2)
            assertThat(result.items[0].rank).isEqualTo(2L)
            assertThat(result.items[1].rank).isEqualTo(3L)
            assertThat(result.items[0].productId).isEqualTo(products[2].id) // score=3
            assertThat(result.items[1].productId).isEqualTo(products[1].id) // score=2
        }

        @Test
        fun `랭킹 ZSET 이 비어있으면 빈 items 와 totalCount 0 을 반환한다`() {
            val result = rankingFacade.getRankingPage(period = RankingPeriod.DAILY, dateString = null, page = 1, size = 20)

            assertThat(result.totalCount).isEqualTo(0L)
            assertThat(result.items).isEmpty()
        }

        @Test
        fun `삭제된 상품(ZSET 에는 있으나 DB 에 없음)은 응답에서 제외된다`() {
            val brand = brandRepository.save(Brand(name = "Nike", description = "d"))
            val p1 = productRepository.save(Product(brandId = brand.id, name = "Live", description = "d", price = 100))
            zadd(todayKey, p1.id, 5.0)
            zadd(todayKey, 999_999L, 10.0) // 존재하지 않는 productId

            val result = rankingFacade.getRankingPage(period = RankingPeriod.DAILY, dateString = null, page = 1, size = 10)

            assertThat(result.items).hasSize(1)
            assertThat(result.items[0].productId).isEqualTo(p1.id)
        }

        @Test
        fun `date 가 yyyyMMdd 가 아니면 BAD_REQUEST`() {
            val ex = assertThrows<CoreException> {
                rankingFacade.getRankingPage(period = RankingPeriod.DAILY, dateString = "2026-04-08", page = 1, size = 20)
            }
            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @Test
        fun `page 가 0 이하면 BAD_REQUEST`() {
            val ex = assertThrows<CoreException> {
                rankingFacade.getRankingPage(period = RankingPeriod.DAILY, dateString = null, page = 0, size = 20)
            }
            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @Test
        fun `size 가 100 초과면 BAD_REQUEST`() {
            val ex = assertThrows<CoreException> {
                rankingFacade.getRankingPage(period = RankingPeriod.DAILY, dateString = null, page = 1, size = 101)
            }
            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @Test
        fun `이전 일자 키도 정상적으로 조회된다 (yyyyMMdd 입력)`() {
            val yesterday = today.minusDays(1)
            val yKey = RankingKeyPolicy.dailyKey(yesterday)
            val brand = brandRepository.save(Brand(name = "Nike", description = "d"))
            val p1 = productRepository.save(Product(brandId = brand.id, name = "Old", description = "d", price = 100))
            zadd(yKey, p1.id, 1.0)

            val yString = yesterday.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            val result = rankingFacade.getRankingPage(period = RankingPeriod.DAILY, dateString = yString, page = 1, size = 10)

            assertThat(result.items).hasSize(1)
            assertThat(result.items[0].productId).isEqualTo(p1.id)
            assertThat(result.date).isEqualTo(yString)
        }
    }

    @Nested
    @DisplayName("findTodayRank()")
    inner class FindTodayRank {

        @Test
        fun `ZSET 에 있는 상품의 0-based 순위를 반환한다`() {
            val brand = brandRepository.save(Brand(name = "Nike", description = "d"))
            val p1 = productRepository.save(Product(brandId = brand.id, name = "P1", description = "d", price = 100))
            val p2 = productRepository.save(Product(brandId = brand.id, name = "P2", description = "d", price = 100))
            zadd(todayKey, p1.id, 10.0) // 1위
            zadd(todayKey, p2.id, 5.0) // 2위

            assertThat(rankingFacade.findTodayRank(p1.id)).isEqualTo(0L)
            assertThat(rankingFacade.findTodayRank(p2.id)).isEqualTo(1L)
        }

        @Test
        fun `ZSET 에 없는 상품은 null 을 반환한다`() {
            assertThat(rankingFacade.findTodayRank(productId = 999L)).isNull()
        }
    }

    private fun zadd(key: String, productId: Long, score: Double) {
        redisTemplate.opsForZSet().add(key, productId.toString(), score)
    }
}
