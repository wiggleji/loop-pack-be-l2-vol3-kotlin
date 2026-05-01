package com.loopers.application.payment

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgPaymentClient
import com.loopers.domain.payment.PgPaymentStatusResponse
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PaymentRecoveryFacadeUnitTest {

    private val mockPaymentService = mockk<PaymentService>()
    private val mockPgPaymentClient = mockk<PgPaymentClient>()
    private val mockOrderService = mockk<OrderService>()

    private val recoveryFacade = PaymentRecoveryFacade(mockPaymentService, mockPgPaymentClient, mockOrderService)

    @Test
    fun `recoverSingle() should confirm paid when PG returns SUCCESS`() {
        val payment = createPayment(orderId = 1L, transactionKey = "txn-123", status = PaymentStatus.PENDING)

        every { mockPgPaymentClient.getPaymentStatus("txn-123") } returns PgPaymentStatusResponse(
            transactionKey = "txn-123",
            status = "SUCCESS",
            reason = null,
        )
        every { mockPaymentService.getByOrderId(1L) } returns payment
        every { mockPaymentService.updatePaymentStatus(any()) } just Runs
        val order = createOrder()
        every { mockOrderService.updateStatus(eq(1L), any()) } answers {
            val action = secondArg<(Order) -> Unit>()
            action(order)
            order
        }

        recoveryFacade.recoverSingle(1L, 1L, "txn-123")

        assertThat(payment.status).isEqualTo(PaymentStatus.PAID)
        assertThat(order.status).isEqualTo(OrderStatus.PAID)
        verify { mockPaymentService.updatePaymentStatus(any()) }
    }

    @Test
    fun `recoverSingle() should confirm failed when PG returns FAILED`() {
        val payment = createPayment(orderId = 2L, transactionKey = "txn-456", status = PaymentStatus.UNKNOWN)

        every { mockPgPaymentClient.getPaymentStatus("txn-456") } returns PgPaymentStatusResponse(
            transactionKey = "txn-456",
            status = "FAILED",
            reason = "card declined",
        )
        every { mockPaymentService.getByOrderId(2L) } returns payment
        every { mockPaymentService.updatePaymentStatus(any()) } just Runs

        recoveryFacade.recoverSingle(2L, 2L, "txn-456")

        assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(payment.reason).isEqualTo("card declined")
        verify(exactly = 0) { mockOrderService.updateStatus(any(), any()) }
    }

    @Test
    fun `recoverSingle() should skip when transactionKey is null`() {
        recoveryFacade.recoverSingle(1L, 1L, null)

        verify(exactly = 0) { mockPgPaymentClient.getPaymentStatus(any()) }
    }

    @Test
    fun `recoverSingle() should skip when payment is already terminal`() {
        val payment = createPayment(orderId = 3L, transactionKey = "txn-789", status = PaymentStatus.PAID)

        every { mockPgPaymentClient.getPaymentStatus("txn-789") } returns PgPaymentStatusResponse(
            transactionKey = "txn-789",
            status = "SUCCESS",
            reason = null,
        )
        every { mockPaymentService.getByOrderId(3L) } returns payment

        recoveryFacade.recoverSingle(3L, 3L, "txn-789")

        verify(exactly = 0) { mockPaymentService.updatePaymentStatus(any()) }
    }

    @Test
    fun `recoverSingle() should do nothing when PG status is still processing`() {
        val payment = createPayment(orderId = 4L, transactionKey = "txn-abc", status = PaymentStatus.PENDING)

        every { mockPgPaymentClient.getPaymentStatus("txn-abc") } returns PgPaymentStatusResponse(
            transactionKey = "txn-abc",
            status = "PROCESSING",
            reason = null,
        )
        every { mockPaymentService.getByOrderId(4L) } returns payment

        recoveryFacade.recoverSingle(4L, 4L, "txn-abc")

        assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        verify(exactly = 0) { mockPaymentService.updatePaymentStatus(any()) }
    }

    @Test
    fun `recoverPendingPayments() should skip when no pending payments`() {
        every { mockPaymentService.findByStatusIn(any()) } returns emptyList()

        recoveryFacade.recoverPendingPayments()

        verify(exactly = 0) { mockPgPaymentClient.getPaymentStatus(any()) }
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
        when (status) {
            PaymentStatus.PAID -> payment.confirmPaid(java.time.ZonedDateTime.now())
            PaymentStatus.FAILED -> payment.confirmFailed("previous failure", java.time.ZonedDateTime.now())
            PaymentStatus.UNKNOWN -> payment.markUnknown("timeout")
            else -> {} // PENDING is default
        }
        return payment
    }

    private fun createOrder(): Order = Order.create(
        userId = 1L,
        items = listOf(
            OrderItem(
                orderId = 0L,
                productId = 1L,
                productName = "Test Product",
                brandId = 1L,
                brandName = "Test Brand",
                price = 10000,
                quantity = 1,
            ),
        ),
    )
}
