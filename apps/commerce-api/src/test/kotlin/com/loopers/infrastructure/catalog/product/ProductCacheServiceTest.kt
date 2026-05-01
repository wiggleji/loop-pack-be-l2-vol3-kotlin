package com.loopers.infrastructure.catalog.product

import com.loopers.application.catalog.brand.BrandResult
import com.loopers.application.catalog.product.ProductDetailResult
import com.loopers.application.catalog.product.ProductSummaryResult
import com.loopers.domain.catalog.product.ProductSearchCondition
import com.loopers.domain.catalog.product.ProductSort
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class ProductCacheServiceTest @Autowired constructor(
    private val productCacheService: ProductCacheService,
    private val redisCleanUp: RedisCleanUp,
) {

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    private fun sampleDetailResult(productId: Long = 1L) = ProductDetailResult(
        id = productId,
        name = "Test Product",
        description = "Test Description",
        price = 10000,
        stock = 50,
        likeCount = 3,
        brand = BrandResult(id = 1L, name = "Nike", description = "Brand desc"),
    )

    private fun sampleSummaryResults() = listOf(
        ProductSummaryResult(id = 1L, name = "Product A", price = 10000, likeCount = 5, brandId = 1L, brandName = "Nike"),
        ProductSummaryResult(id = 2L, name = "Product B", price = 20000, likeCount = 3, brandId = 1L, brandName = "Nike"),
    )

    @DisplayName("Product Detail Cache")
    @Nested
    inner class DetailCache {

        @DisplayName("putProductDetail() 후 getProductDetail() 하면 캐시된 값을 반환한다.")
        @Test
        fun `putProductDetail() should cache and getProductDetail() should return cached value`() {
            // arrange
            val result = sampleDetailResult(1L)

            // act
            productCacheService.putProductDetail(1L, result)
            val cached = productCacheService.getProductDetail(1L)

            // assert
            assertThat(cached).isEqualTo(result)
        }

        @DisplayName("캐시가 없으면 getProductDetail() 은 null 을 반환한다.")
        @Test
        fun `getProductDetail() should return null when cache miss`() {
            val cached = productCacheService.getProductDetail(999L)
            assertThat(cached).isNull()
        }

        @DisplayName("evictProductDetail() 후 getProductDetail() 은 null 을 반환한다.")
        @Test
        fun `evictProductDetail() should remove cached detail`() {
            // arrange
            productCacheService.putProductDetail(1L, sampleDetailResult(1L))

            // act
            productCacheService.evictProductDetail(1L)
            val cached = productCacheService.getProductDetail(1L)

            // assert
            assertThat(cached).isNull()
        }
    }

    @DisplayName("Product List Cache")
    @Nested
    inner class ListCache {

        @DisplayName("putProductList() 후 getProductList() 하면 캐시된 값을 반환한다.")
        @Test
        fun `putProductList() should cache and getProductList() should return cached value`() {
            // arrange
            val condition = ProductSearchCondition(brandId = 1L, sort = ProductSort.LATEST, page = 0, size = 20)
            val results = sampleSummaryResults()

            // act
            productCacheService.putProductList(condition, results)
            val cached = productCacheService.getProductList(condition)

            // assert
            assertThat(cached).isEqualTo(results)
        }

        @DisplayName("다른 조건으로 캐시하면 서로 다른 키에 저장된다.")
        @Test
        fun `different conditions should produce different cache keys`() {
            // arrange
            val condition1 = ProductSearchCondition(brandId = 1L, sort = ProductSort.LATEST, page = 0, size = 20)
            val condition2 = ProductSearchCondition(brandId = 2L, sort = ProductSort.PRICE_ASC, page = 0, size = 20)
            val results1 = sampleSummaryResults()
            val results2 = listOf(
                ProductSummaryResult(id = 3L, name = "Product C", price = 5000, likeCount = 1, brandId = 2L, brandName = "Adidas"),
            )

            // act
            productCacheService.putProductList(condition1, results1)
            productCacheService.putProductList(condition2, results2)

            // assert
            assertThat(productCacheService.getProductList(condition1)).isEqualTo(results1)
            assertThat(productCacheService.getProductList(condition2)).isEqualTo(results2)
        }

        @DisplayName("evictAllProductLists() 는 모든 리스트 캐시를 삭제한다.")
        @Test
        fun `evictAllProductLists() should clear all list caches`() {
            // arrange
            val condition1 = ProductSearchCondition(brandId = 1L, sort = ProductSort.LATEST, page = 0, size = 20)
            val condition2 = ProductSearchCondition(brandId = null, sort = ProductSort.LIKES_DESC, page = 1, size = 10)
            productCacheService.putProductList(condition1, sampleSummaryResults())
            productCacheService.putProductList(condition2, sampleSummaryResults())

            // act
            productCacheService.evictAllProductLists()

            // assert
            assertThat(productCacheService.getProductList(condition1)).isNull()
            assertThat(productCacheService.getProductList(condition2)).isNull()
        }
    }

    @DisplayName("Stampede Lock")
    @Nested
    inner class StampedeLock {

        @DisplayName("동시에 여러 스레드가 tryLock() 호출 시 하나만 성공한다.")
        @Test
        fun `tryLock() should allow only one thread to acquire lock`() {
            // arrange
            val threadCount = 10
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val lockAcquiredCount = AtomicInteger(0)

            // act
            repeat(threadCount) {
                executor.submit {
                    try {
                        if (productCacheService.tryLock(1L)) {
                            lockAcquiredCount.incrementAndGet()
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await()
            executor.shutdown()

            // assert
            assertThat(lockAcquiredCount.get()).isEqualTo(1)

            // cleanup
            productCacheService.unlock(1L)
        }

        @DisplayName("unlock() 후 다시 tryLock() 하면 성공한다.")
        @Test
        fun `unlock() should release lock allowing re-acquisition`() {
            // arrange
            assertThat(productCacheService.tryLock(1L)).isTrue()

            // act
            productCacheService.unlock(1L)
            val reacquired = productCacheService.tryLock(1L)

            // assert
            assertThat(reacquired).isTrue()
        }
    }
}
