package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class UserCouponServiceUnitTest {

    private val mockUserCouponRepository = mockk<UserCouponRepository>()
    private val mockTemplateRepository = mockk<CouponTemplateRepository>()
    private val service = UserCouponService(mockUserCouponRepository, mockTemplateRepository)

    // ─── issue ───

    @Test
    fun `issue() should create and return user coupon`() {
        val template = createTemplate(id = 1L, maxIssuance = 100, issuedCount = 0)
        every { mockTemplateRepository.findById(1L) } returns template
        every { mockUserCouponRepository.existsByUserIdAndCouponTemplateId(1L, 1L) } returns false
        every { mockTemplateRepository.save(any()) } returns template
        every { mockUserCouponRepository.save(any()) } answers {
            firstArg<UserCoupon>()
        }

        val result = service.issue(userId = 1L, couponTemplateId = 1L)

        assertThat(result.userId).isEqualTo(1L)
        assertThat(result.couponTemplateId).isEqualTo(1L)
        assertThat(result.status).isEqualTo(UserCouponStatus.AVAILABLE)
    }

    @Test
    fun `issue() should throw NOT_FOUND when template does not exist`() {
        every { mockTemplateRepository.findById(99L) } returns null

        assertThrows<CoreException> {
            service.issue(userId = 1L, couponTemplateId = 99L)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @Test
    fun `issue() should throw CONFLICT when already issued`() {
        val template = createTemplate(id = 1L)
        every { mockTemplateRepository.findById(1L) } returns template
        every { mockUserCouponRepository.existsByUserIdAndCouponTemplateId(1L, 1L) } returns true

        assertThrows<CoreException> {
            service.issue(userId = 1L, couponTemplateId = 1L)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    @Test
    fun `issue() should throw BAD_REQUEST when template expired`() {
        val template = createTemplate(id = 1L, expiresAt = LocalDate.now().minusDays(1))
        every { mockTemplateRepository.findById(1L) } returns template
        every { mockUserCouponRepository.existsByUserIdAndCouponTemplateId(1L, 1L) } returns false

        assertThrows<CoreException> {
            service.issue(userId = 1L, couponTemplateId = 1L)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `issue() should throw BAD_REQUEST when max issuance reached`() {
        val template = createTemplate(id = 1L, maxIssuance = 5, issuedCount = 5)
        every { mockTemplateRepository.findById(1L) } returns template
        every { mockUserCouponRepository.existsByUserIdAndCouponTemplateId(1L, 1L) } returns false

        assertThrows<CoreException> {
            service.issue(userId = 1L, couponTemplateId = 1L)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    // ─── useForOrder ───

    @Test
    fun `useForOrder() should mark coupon as USED`() {
        val userCoupon = UserCoupon(id = 1L, userId = 1L, couponTemplateId = 1L)
        every { mockUserCouponRepository.findById(1L) } returns userCoupon
        every { mockUserCouponRepository.save(any()) } answers { firstArg() }

        val result = service.useForOrder(userCouponId = 1L, orderId = 100L)

        assertThat(result.status).isEqualTo(UserCouponStatus.USED)
        assertThat(result.usedOrderId).isEqualTo(100L)
        verify { mockUserCouponRepository.findById(1L) }
    }

    @Test
    fun `useForOrder() should throw NOT_FOUND when coupon does not exist`() {
        every { mockUserCouponRepository.findById(99L) } returns null

        assertThrows<CoreException> {
            service.useForOrder(userCouponId = 99L, orderId = 100L)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @Test
    fun `useForOrder() should throw BAD_REQUEST when coupon already used`() {
        val userCoupon = UserCoupon(id = 1L, userId = 1L, couponTemplateId = 1L, status = UserCouponStatus.USED)
        every { mockUserCouponRepository.findById(1L) } returns userCoupon

        assertThrows<CoreException> {
            service.useForOrder(userCouponId = 1L, orderId = 100L)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    private fun createTemplate(
        id: Long = 0L,
        maxIssuance: Int? = null,
        issuedCount: Int = 0,
        expiresAt: LocalDate = LocalDate.now().plusDays(30),
    ): CouponTemplate = CouponTemplate(
        id = id,
        name = "Test Coupon",
        type = CouponType.RATE,
        discountValue = 10,
        minOrderAmount = 0,
        maxIssuance = maxIssuance,
        issuedCount = issuedCount,
        expiresAt = expiresAt,
    )
}
