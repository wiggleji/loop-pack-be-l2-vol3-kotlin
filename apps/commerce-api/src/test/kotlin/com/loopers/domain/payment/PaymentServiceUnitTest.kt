package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PaymentServiceUnitTest {

    private val mockRepository = mockk<PaymentRepository>()
    private val paymentService = PaymentService(mockRepository)

    // ─── createPayment ───

    @Test
    fun `createPayment() should save and return payment`() {
        val payment = createPayment()
        every { mockRepository.save(payment) } returns payment

        val result = paymentService.createPayment(payment)

        assertThat(result).isEqualTo(payment)
        verify { mockRepository.save(payment) }
    }

    // ─── getByOrderId ───

    @Test
    fun `getByOrderId() should return payment when found`() {
        val payment = createPayment(orderId = 1L)
        every { mockRepository.findByOrderId(1L) } returns payment

        val result = paymentService.getByOrderId(1L)

        assertThat(result).isEqualTo(payment)
    }

    @Test
    fun `getByOrderId() should throw NOT_FOUND when not found`() {
        every { mockRepository.findByOrderId(999L) } returns null

        assertThrows<CoreException> {
            paymentService.getByOrderId(999L)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    // ─── findByOrderId ───

    @Test
    fun `findByOrderId() should return null when not found`() {
        every { mockRepository.findByOrderId(999L) } returns null

        assertThat(paymentService.findByOrderId(999L)).isNull()
    }

    // ─── getByTransactionKey ───

    @Test
    fun `getByTransactionKey() should return payment when found`() {
        val payment = createPayment(transactionKey = "txn-123")
        every { mockRepository.findByTransactionKey("txn-123") } returns payment

        val result = paymentService.getByTransactionKey("txn-123")

        assertThat(result).isEqualTo(payment)
    }

    @Test
    fun `getByTransactionKey() should throw NOT_FOUND when not found`() {
        every { mockRepository.findByTransactionKey("unknown") } returns null

        assertThrows<CoreException> {
            paymentService.getByTransactionKey("unknown")
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    // ─── findByStatusIn ───

    @Test
    fun `findByStatusIn() should return matching payments`() {
        val payments = listOf(createPayment(status = PaymentStatus.PENDING))
        every { mockRepository.findByStatusIn(listOf(PaymentStatus.PENDING)) } returns payments

        val result = paymentService.findByStatusIn(listOf(PaymentStatus.PENDING))

        assertThat(result).hasSize(1)
    }

    // ─── updatePaymentStatus ───

    @Test
    fun `updatePaymentStatus() should delegate to repository`() {
        val payment = createPayment()
        every { mockRepository.updateStatus(payment) } just Runs

        paymentService.updatePaymentStatus(payment)

        verify { mockRepository.updateStatus(payment) }
    }

    // ─── Helpers ───

    private fun createPayment(
        orderId: Long = 1L,
        amount: Int = 10000,
        cardType: CardType = CardType.SAMSUNG,
        cardNo: String = "1234-5678-9012-3456",
        transactionKey: String? = null,
        status: PaymentStatus = PaymentStatus.PENDING,
    ): Payment = Payment(
        orderId = orderId,
        amount = amount,
        cardType = cardType,
        cardNo = cardNo,
        transactionKey = transactionKey,
        status = status,
    )
}
