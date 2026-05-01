package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class CouponTemplateDomainTest {

    @Test
    fun `FIXED discount should return discountValue capped at totalPrice`() {
        val template = CouponTemplate(
            name = "3000원 할인",
            type = CouponType.FIXED,
            discountValue = 3000,
            expiresAt = LocalDate.now().plusDays(30),
        )

        assertThat(template.discount(10000)).isEqualTo(3000)
        assertThat(template.discount(2000)).isEqualTo(2000) // capped
    }

    @Test
    fun `RATE discount should return percentage of totalPrice`() {
        val template = CouponTemplate(
            name = "10% 할인",
            type = CouponType.RATE,
            discountValue = 10,
            expiresAt = LocalDate.now().plusDays(30),
        )

        assertThat(template.discount(10000)).isEqualTo(1000)
        assertThat(template.discount(15000)).isEqualTo(1500)
    }

    @Test
    fun `RATE discount over 100 should throw BAD_REQUEST`() {
        assertThrows<CoreException> {
            CouponTemplate(
                name = "Invalid",
                type = CouponType.RATE,
                discountValue = 150,
                expiresAt = LocalDate.now().plusDays(30),
            )
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `isExpired() should return true when past expiresAt`() {
        val template = CouponTemplate(
            name = "Expired",
            type = CouponType.FIXED,
            discountValue = 1000,
            expiresAt = LocalDate.now().minusDays(1),
        )

        assertThat(template.isExpired()).isTrue()
    }

    @Test
    fun `requireIssuable() should throw when max issuance reached`() {
        val template = CouponTemplate(
            name = "Full",
            type = CouponType.FIXED,
            discountValue = 1000,
            maxIssuance = 5,
            issuedCount = 5,
            expiresAt = LocalDate.now().plusDays(30),
        )

        assertThrows<CoreException> {
            template.requireIssuable()
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `incrementIssuedCount() should increase count by 1`() {
        val template = CouponTemplate(
            name = "Test",
            type = CouponType.FIXED,
            discountValue = 1000,
            issuedCount = 3,
            expiresAt = LocalDate.now().plusDays(30),
        )

        template.incrementIssuedCount()

        assertThat(template.issuedCount).isEqualTo(4)
    }
}
