package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.infrastructure.catalog.brand.BrandEntity
import com.loopers.infrastructure.catalog.brand.BrandJpaRepository
import com.loopers.infrastructure.catalog.product.ProductEntity
import com.loopers.infrastructure.catalog.product.ProductJpaRepository
import com.loopers.infrastructure.catalog.product.ProductStockEntity
import com.loopers.infrastructure.catalog.product.ProductStockJpaRepository
import com.loopers.infrastructure.coupon.CouponTemplateEntity
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository
import com.loopers.infrastructure.coupon.UserCouponEntity
import com.loopers.infrastructure.coupon.UserCouponJpaRepository
import com.loopers.application.order.OrderFacade
import com.loopers.application.order.OrderItemCommand
import com.loopers.application.order.PlaceOrderCommand
import com.loopers.domain.catalog.product.ProductStatus
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@ActiveProfiles("test")
class CouponFacadeConcurrencyTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val productStockJpaRepository: ProductStockJpaRepository,
    private val couponTemplateJpaRepository: CouponTemplateJpaRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    @Test
    fun `동시에 5명이 같은 쿠폰으로 주문해도 쿠폰은 1번만 사용되어야 한다`() {
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
            ProductStockEntity(productId = product.id, quantity = 100)
        )
        val template = couponTemplateJpaRepository.save(
            CouponTemplateEntity(
                name = "3000원 할인",
                type = CouponType.FIXED,
                discountValue = 3000,
                minOrderAmount = 0,
                maxIssuance = null,
                expiresAt = LocalDate.now().plusDays(30),
            )
        )
        // 5명 모두 같은 쿠폰을 발급받은 상태 (각자 별도 UserCoupon)
        // 하지만 하나의 UserCoupon을 공유하는 시나리오 → 동시 사용 방지 테스트
        val userCoupon = userCouponJpaRepository.save(
            UserCouponEntity(userId = 1L, couponTemplateId = template.id)
        )

        val threadCount = 5
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // Act: 5 threads try to use the same userCoupon concurrently
        repeat(threadCount) { i ->
            executor.submit {
                try {
                    orderFacade.placeOrder(
                        userId = 1L,
                        cmd = PlaceOrderCommand(
                            items = listOf(OrderItemCommand(productId = product.id, quantity = 1)),
                            userCouponId = userCoupon.id,
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

        // Assert: only 1 should succeed (optimistic lock via @Version on user_coupons)
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failCount.get()).isEqualTo(4)

        val finalCoupon = userCouponJpaRepository.findById(userCoupon.id).get()
        assertThat(finalCoupon.status).isEqualTo(UserCouponStatus.USED)
        assertThat(finalCoupon.usedOrderId).isNotNull()
    }
}
