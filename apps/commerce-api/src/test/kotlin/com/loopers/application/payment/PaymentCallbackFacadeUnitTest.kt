package com.loopers.application.payment

import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.infrastructure.outbox.OutboxEventService
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PaymentCallbackFacadeUnitTest {

    private val mockPaymentService = mockk<PaymentService>()
    private val mockOutboxEventService = mockk<OutboxEventService>(relaxed = true)

    private val callbackFacade = PaymentCallbackFacade(mockPaymentService, mockOutboxEventService)

    // ─── handleCallback SUCCESS ───

    @Test
    fun `handleCallback() should confirm paid and publish PaymentConfirmedEvent on SUCCESS`() {
        val payment = createPayment(transactionKey = "txn-123", status = PaymentStatus.PENDING)

        every { mockPaymentService.getByTransactionKey("txn-123") } returns payment
        every { mockPaymentService.updatePaymentStatus(any()) } just Runs

        val result = callbackFacade.handleCallback(
            PaymentCallbackCommand(transactionKey = "txn-123", status = "SUCCESS", reason = null),
        )

        assertThat(result.status).isEqualTo(PaymentStatus.PAID)
        verify { mockPaymentService.updatePaymentStatus(any()) }
        verify { mockOutboxEventService.save(any(), any(), any(), any(), any(), any()) }
    }

    // ─── handleCallback FAILED ───

    @Test
    fun `handleCallback() should confirm failed and publish PaymentFailedEvent on FAILED`() {
        val payment = createPayment(transactionKey = "txn-456", status = PaymentStatus.PENDING)

        every { mockPaymentService.getByTransactionKey("txn-456") } returns payment
        every { mockPaymentService.updatePaymentStatus(any()) } just Runs

        val result = callbackFacade.handleCallback(
            PaymentCallbackCommand(transactionKey = "txn-456", status = "FAILED", reason = "insufficient funds"),
        )

        assertThat(result.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(result.reason).isEqualTo("insufficient funds")
        verify { mockPaymentService.updatePaymentStatus(any()) }
        verify { mockOutboxEventService.save(any(), any(), any(), any(), any(), any()) }
    }

    // ─── handleCallback idempotency ───

    @Test
    fun `handleCallback() should skip if payment is already PAID (idempotency)`() {
        val payment = createPayment(transactionKey = "txn-789", status = PaymentStatus.PAID)

        every { mockPaymentService.getByTransactionKey("txn-789") } returns payment

        val result = callbackFacade.handleCallback(
            PaymentCallbackCommand(transactionKey = "txn-789", status = "SUCCESS", reason = null),
        )

        assertThat(result.status).isEqualTo(PaymentStatus.PAID)
        verify(exactly = 0) { mockPaymentService.updatePaymentStatus(any()) }
        verify(exactly = 0) { mockOutboxEventService.save(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `handleCallback() should skip if payment is already FAILED (idempotency)`() {
        val payment = createPayment(transactionKey = "txn-000", status = PaymentStatus.FAILED)

        every { mockPaymentService.getByTransactionKey("txn-000") } returns payment

        val result = callbackFacade.handleCallback(
            PaymentCallbackCommand(transactionKey = "txn-000", status = "FAILED", reason = "rejected"),
        )

        assertThat(result.status).isEqualTo(PaymentStatus.FAILED)
        verify(exactly = 0) { mockPaymentService.updatePaymentStatus(any()) }
    }

    // ─── Helpers ───

    private fun createPayment(
        orderId: Long = 1L,
        transactionKey: String? = null,
        status: PaymentStatus = PaymentStatus.PENDING,
    ): Payment {
        val payment = Payment(
            orderId = orderId,
            amount = 10000,
            cardType = CardType.SAMSUNG,
            cardNo = "1234-5678-9012-3456",
            transactionKey = transactionKey,
        )
        if (status == PaymentStatus.PAID) {
            payment.confirmPaid(java.time.ZonedDateTime.now())
        } else if (status == PaymentStatus.FAILED) {
            payment.confirmFailed("previous failure", java.time.ZonedDateTime.now())
        }
        return payment
    }

}
