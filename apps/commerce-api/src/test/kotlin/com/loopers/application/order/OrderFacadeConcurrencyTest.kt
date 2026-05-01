package com.loopers.application.order

import com.loopers.infrastructure.catalog.brand.BrandEntity
import com.loopers.infrastructure.catalog.brand.BrandJpaRepository
import com.loopers.infrastructure.catalog.product.ProductEntity
import com.loopers.infrastructure.catalog.product.ProductJpaRepository
import com.loopers.infrastructure.catalog.product.ProductStockEntity
import com.loopers.infrastructure.catalog.product.ProductStockJpaRepository
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import com.loopers.domain.catalog.product.ProductStatus

@SpringBootTest
@ActiveProfiles("test")
class OrderFacadeConcurrencyTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val productStockJpaRepository: ProductStockJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @Test
    fun `placeOrder() - 동시에 10명이 주문해도 재고 5개를 초과해서는 안된다`() {
        // Arrange
        val brand = brandJpaRepository.save(BrandEntity(name = "TestBrand", description = "desc"))
        val product = productJpaRepository.save(
            ProductEntity(
                brandId = brand.id,
                name = "TestProduct",
                description = "desc",
                price = 10000,
                status = ProductStatus.ACTIVE,
            )
        )
        productStockJpaRepository.save(
            ProductStockEntity(productId = product.id, quantity = 5)
        )

        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // Act
        repeat(threadCount) { i ->
            executor.submit {
                try {
                    orderFacade.placeOrder(
                        userId = (i + 1).toLong(),
                        cmd = PlaceOrderCommand(
                            items = listOf(OrderItemCommand(productId = product.id, quantity = 1)),
                            cardType = com.loopers.domain.payment.CardType.SAMSUNG,
                            cardNo = "1234-5678-9012-3456",
                        ),
                    )
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        // Assert
        val finalStock = productStockJpaRepository.findByProductId(product.id)!!
        assertThat(successCount.get()).isEqualTo(5)
        assertThat(failCount.get()).isEqualTo(5)
        assertThat(finalStock.quantity).isEqualTo(0)
    }
}
