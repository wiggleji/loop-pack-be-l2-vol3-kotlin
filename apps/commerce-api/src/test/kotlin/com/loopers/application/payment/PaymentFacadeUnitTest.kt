package com.loopers.application.payment

import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgPaymentResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PaymentFacadeUnitTest {

    private val mockPaymentService = mockk<PaymentService>()
    private val mockPgPaymentCaller = mockk<PgPaymentCaller>()

    private val paymentFacade = PaymentFacade(mockPaymentService, mockPgPaymentCaller)

    // ─── requestPayment ───

    @Test
    fun `requestPayment() should return existing payment when already exists (idempotency)`() {
        val existing = createPayment(orderId = 1L, transactionKey = "txn-123")
        every { mockPaymentService.findByOrderId(1L) } returns existing

        val result = paymentFacade.requestPayment(createCommand(orderId = 1L))

        assertThat(result.orderId).isEqualTo(1L)
        assertThat(result.transactionKey).isEqualTo("txn-123")
        verify(exactly = 0) { mockPgPaymentCaller.call(any()) }
    }

    @Test
    fun `requestPayment() should call PG and save PENDING payment on success`() {
        every { mockPaymentService.findByOrderId(1L) } returns null
        every { mockPgPaymentCaller.call(any()) } returns PgPaymentResponse(
            transactionKey = "txn-new",
            status = "PENDING",
        )
        val paymentSlot = slot<Payment>()
        every { mockPaymentService.createPayment(capture(paymentSlot)) } answers {
            paymentSlot.captured
        }

        val result = paymentFacade.requestPayment(createCommand(orderId = 1L))

        assertThat(result.status).isEqualTo(PaymentStatus.PENDING)
        assertThat(result.transactionKey).isEqualTo("txn-new")
        verify { mockPgPaymentCaller.call(any()) }
        verify { mockPaymentService.createPayment(any()) }
    }

    @Test
    fun `requestPayment() should save UNKNOWN payment when PG call fails`() {
        every { mockPaymentService.findByOrderId(1L) } returns null
        every { mockPgPaymentCaller.call(any()) } throws PgPaymentException("PG timeout")
        val paymentSlot = slot<Payment>()
        every { mockPaymentService.createPayment(capture(paymentSlot)) } answers {
            paymentSlot.captured
        }

        val result = paymentFacade.requestPayment(createCommand(orderId = 1L))

        assertThat(result.status).isEqualTo(PaymentStatus.UNKNOWN)
        assertThat(paymentSlot.captured.reason).isEqualTo("PG timeout")
    }

    // ─── Helpers ───

    private fun createCommand(
        orderId: Long = 1L,
        amount: Int = 10000,
        cardType: CardType = CardType.SAMSUNG,
        cardNo: String = "1234-5678-9012-3456",
    ) = RequestPaymentCommand(
        orderId = orderId,
        amount = amount,
        cardType = cardType,
        cardNo = cardNo,
    )

    private fun createPayment(
        orderId: Long = 1L,
        amount: Int = 10000,
        cardType: CardType = CardType.SAMSUNG,
        cardNo: String = "1234-5678-9012-3456",
        transactionKey: String? = null,
        status: PaymentStatus = PaymentStatus.PENDING,
    ) = Payment(
        orderId = orderId,
        amount = amount,
        cardType = cardType,
        cardNo = cardNo,
        transactionKey = transactionKey,
        status = status,
    )
}
