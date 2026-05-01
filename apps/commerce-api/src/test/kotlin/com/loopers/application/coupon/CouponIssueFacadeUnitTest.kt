package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponIssueRequest
import com.loopers.domain.coupon.CouponIssueRequestService
import com.loopers.domain.coupon.CouponIssueStatus
import com.loopers.domain.coupon.CouponTemplate
import com.loopers.domain.coupon.CouponTemplateService
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponService
import com.loopers.infrastructure.outbox.OutboxEventService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class CouponIssueFacadeUnitTest {

    private val mockIssueRequestService = mockk<CouponIssueRequestService>(relaxed = true)
    private val mockTemplateService = mockk<CouponTemplateService>(relaxed = true)
    private val mockUserCouponService = mockk<UserCouponService>(relaxed = true)
    private val mockOutboxEventService = mockk<OutboxEventService>(relaxed = true)

    private val facade = CouponIssueFacade(
        mockIssueRequestService, mockTemplateService, mockUserCouponService, mockOutboxEventService,
    )

    // ─── requestIssue ───

    @Test
    fun `requestIssue() should save request and outbox`() {
        val template = createTemplate(id = 1L)
        val savedRequest = CouponIssueRequest(id = 100L, userId = 1L, couponTemplateId = 1L)

        every { mockIssueRequestService.findByUserIdAndCouponTemplateId(1L, 1L) } returns null
        every { mockTemplateService.getById(1L) } returns template
        every { mockIssueRequestService.create(1L, 1L) } returns savedRequest

        val result = facade.requestIssue(userId = 1L, couponTemplateId = 1L)

        assertThat(result.status).isEqualTo(CouponIssueStatus.REQUESTED)
        assertThat(result.id).isEqualTo(100L)
        verify { mockOutboxEventService.save(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `requestIssue() should return existing request if already requested (idempotency)`() {
        val existing = CouponIssueRequest(
            id = 100L, userId = 1L, couponTemplateId = 1L, status = CouponIssueStatus.ISSUED,
        )
        every { mockIssueRequestService.findByUserIdAndCouponTemplateId(1L, 1L) } returns existing

        val result = facade.requestIssue(userId = 1L, couponTemplateId = 1L)

        assertThat(result.status).isEqualTo(CouponIssueStatus.ISSUED)
        verify(exactly = 0) { mockOutboxEventService.save(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `requestIssue() should throw NOT_FOUND when template does not exist`() {
        every { mockIssueRequestService.findByUserIdAndCouponTemplateId(1L, 99L) } returns null
        every { mockTemplateService.getById(99L) } throws CoreException(ErrorType.NOT_FOUND, "not found")

        assertThrows<CoreException> {
            facade.requestIssue(userId = 1L, couponTemplateId = 99L)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    // ─── processIssue ───

    @Test
    fun `processIssue() should issue coupon and mark request as ISSUED`() {
        val request = CouponIssueRequest(id = 100L, userId = 1L, couponTemplateId = 1L)
        val userCoupon = UserCoupon(id = 1L, userId = 1L, couponTemplateId = 1L)

        every { mockIssueRequestService.findById(100L) } returns request
        every { mockUserCouponService.issueWithLock(1L, 1L) } returns userCoupon

        facade.processIssue(requestId = 100L, userId = 1L, couponTemplateId = 1L)

        assertThat(request.status).isEqualTo(CouponIssueStatus.ISSUED)
        verify { mockUserCouponService.issueWithLock(1L, 1L) }
        verify { mockIssueRequestService.save(request) }
    }

    @Test
    fun `processIssue() should mark FAILED when quantity exceeded`() {
        val request = CouponIssueRequest(id = 100L, userId = 1L, couponTemplateId = 1L)

        every { mockIssueRequestService.findById(100L) } returns request
        every { mockUserCouponService.issueWithLock(1L, 1L) } throws
            CoreException(ErrorType.BAD_REQUEST, "발급 가능 수량을 초과했습니다.")

        facade.processIssue(requestId = 100L, userId = 1L, couponTemplateId = 1L)

        assertThat(request.status).isEqualTo(CouponIssueStatus.FAILED)
        assertThat(request.failReason).contains("수량")
        verify { mockIssueRequestService.save(request) }
    }

    @Test
    fun `processIssue() should mark FAILED when user already has coupon`() {
        val request = CouponIssueRequest(id = 100L, userId = 1L, couponTemplateId = 1L)

        every { mockIssueRequestService.findById(100L) } returns request
        every { mockUserCouponService.issueWithLock(1L, 1L) } throws
            CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.")

        facade.processIssue(requestId = 100L, userId = 1L, couponTemplateId = 1L)

        assertThat(request.status).isEqualTo(CouponIssueStatus.FAILED)
        assertThat(request.failReason).contains("이미")
        verify { mockIssueRequestService.save(request) }
    }

    @Test
    fun `processIssue() should skip if request already processed (idempotency)`() {
        val request = CouponIssueRequest(
            id = 100L, userId = 1L, couponTemplateId = 1L, status = CouponIssueStatus.ISSUED,
        )

        every { mockIssueRequestService.findById(100L) } returns request

        facade.processIssue(requestId = 100L, userId = 1L, couponTemplateId = 1L)

        verify(exactly = 0) { mockUserCouponService.issueWithLock(any(), any()) }
        verify(exactly = 0) { mockIssueRequestService.save(any()) }
    }

    // ─── Helpers ───

    private fun createTemplate(
        id: Long = 1L,
        maxIssuance: Int? = 100,
        issuedCount: Int = 0,
    ): CouponTemplate = CouponTemplate(
        id = id,
        name = "Test Coupon",
        type = CouponType.FIXED,
        discountValue = 1000,
        minOrderAmount = 0,
        maxIssuance = maxIssuance,
        issuedCount = issuedCount,
        expiresAt = LocalDate.now().plusDays(30),
    )
}
