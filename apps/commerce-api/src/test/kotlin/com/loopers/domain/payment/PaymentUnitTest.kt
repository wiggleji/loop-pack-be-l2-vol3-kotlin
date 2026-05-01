package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime

class PaymentUnitTest {

    // ─── init validation ───

    @Test
    fun `Payment should be created with valid fields`() {
        val payment = createPayment()

        assertThat(payment.orderId).isEqualTo(1L)
        assertThat(payment.amount).isEqualTo(10000)
        assertThat(payment.cardType).isEqualTo(CardType.SAMSUNG)
        assertThat(payment.cardNo).isEqualTo("1234-5678-9012-3456")
        assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        assertThat(payment.transactionKey).isNull()
    }

    @Test
    fun `Payment init throws BAD_REQUEST when amount is negative`() {
        assertThrows<CoreException> {
            createPayment(amount = -1)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `Payment init allows zero amount`() {
        val payment = createPayment(amount = 0)
        assertThat(payment.amount).isEqualTo(0)
    }

    @Test
    fun `Payment init throws BAD_REQUEST when cardNo is blank`() {
        assertThrows<CoreException> {
            createPayment(cardNo = "  ")
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    // ─── confirmPaid ───

    @Test
    fun `confirmPaid() should transition PENDING to PAID`() {
        val payment = createPayment()
        val now = ZonedDateTime.now()

        payment.confirmPaid(now)

        assertThat(payment.status).isEqualTo(PaymentStatus.PAID)
        assertThat(payment.callbackReceivedAt).isEqualTo(now)
    }

    @Test
    fun `confirmPaid() should transition UNKNOWN to PAID`() {
        val payment = createPayment()
        payment.markUnknown("timeout")
        val now = ZonedDateTime.now()

        payment.confirmPaid(now)

        assertThat(payment.status).isEqualTo(PaymentStatus.PAID)
    }

    @Test
    fun `confirmPaid() throws BAD_REQUEST when already PAID`() {
        val payment = createPayment()
        payment.confirmPaid(ZonedDateTime.now())

        assertThrows<CoreException> {
            payment.confirmPaid(ZonedDateTime.now())
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `confirmPaid() throws BAD_REQUEST when already FAILED`() {
        val payment = createPayment()
        payment.confirmFailed("rejected", ZonedDateTime.now())

        assertThrows<CoreException> {
            payment.confirmPaid(ZonedDateTime.now())
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    // ─── confirmFailed ───

    @Test
    fun `confirmFailed() should transition PENDING to FAILED`() {
        val payment = createPayment()
        val now = ZonedDateTime.now()

        payment.confirmFailed("insufficient funds", now)

        assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(payment.reason).isEqualTo("insufficient funds")
        assertThat(payment.callbackReceivedAt).isEqualTo(now)
    }

    @Test
    fun `confirmFailed() should transition UNKNOWN to FAILED`() {
        val payment = createPayment()
        payment.markUnknown("timeout")

        payment.confirmFailed("rejected", ZonedDateTime.now())

        assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
    }

    @Test
    fun `confirmFailed() throws BAD_REQUEST when already terminal`() {
        val payment = createPayment()
        payment.confirmPaid(ZonedDateTime.now())

        assertThrows<CoreException> {
            payment.confirmFailed("rejected", ZonedDateTime.now())
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    // ─── markUnknown ───

    @Test
    fun `markUnknown() should transition PENDING to UNKNOWN`() {
        val payment = createPayment()

        payment.markUnknown("timeout")

        assertThat(payment.status).isEqualTo(PaymentStatus.UNKNOWN)
        assertThat(payment.reason).isEqualTo("timeout")
    }

    @Test
    fun `markUnknown() throws BAD_REQUEST when already terminal`() {
        val payment = createPayment()
        payment.confirmPaid(ZonedDateTime.now())

        assertThrows<CoreException> {
            payment.markUnknown("timeout")
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    // ─── isTerminal ───

    @Test
    fun `isTerminal() returns false for PENDING`() {
        assertThat(createPayment().isTerminal()).isFalse()
    }

    @Test
    fun `isTerminal() returns false for UNKNOWN`() {
        val payment = createPayment()
        payment.markUnknown("timeout")
        assertThat(payment.isTerminal()).isFalse()
    }

    @Test
    fun `isTerminal() returns true for PAID`() {
        val payment = createPayment()
        payment.confirmPaid(ZonedDateTime.now())
        assertThat(payment.isTerminal()).isTrue()
    }

    @Test
    fun `isTerminal() returns true for FAILED`() {
        val payment = createPayment()
        payment.confirmFailed("rejected", ZonedDateTime.now())
        assertThat(payment.isTerminal()).isTrue()
    }

    // ─── PaymentStatus.isTerminal ───

    @Test
    fun `PaymentStatus isTerminal() returns correct values`() {
        assertThat(PaymentStatus.PENDING.isTerminal()).isFalse()
        assertThat(PaymentStatus.UNKNOWN.isTerminal()).isFalse()
        assertThat(PaymentStatus.PAID.isTerminal()).isTrue()
        assertThat(PaymentStatus.FAILED.isTerminal()).isTrue()
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
