package com.loopers.application.payment

import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgPaymentClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

@Service
class PaymentRecoveryFacade(
    private val paymentService: PaymentService,
    private val pgPaymentClient: PgPaymentClient,
    private val orderService: OrderService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val PG_STATUS_SUCCESS = "SUCCESS"
        private const val PG_STATUS_FAILED = "FAILED"
    }

    @Scheduled(fixedDelay = 30_000)
    fun recoverPendingPayments() {
        val payments = paymentService.findByStatusIn(
            listOf(PaymentStatus.PENDING, PaymentStatus.UNKNOWN),
        )

        if (payments.isEmpty()) return

        log.info("[Recovery] ${payments.size}건의 미완료 결제 처리 시작")

        for (payment in payments) {
            try {
                recoverSingle(payment.id, payment.orderId, payment.transactionKey)
            } catch (ex: Exception) {
                log.warn("[Recovery] 결제 복구 실패: paymentId=${payment.id}, error=${ex.message}")
            }
        }
    }

    fun recoverSingle(paymentId: Long, orderId: Long, transactionKey: String?) {
        if (transactionKey == null) {
            log.info("[Recovery] transactionKey 없음 — 복구 불가: paymentId=$paymentId")
            return
        }

        val pgStatus = pgPaymentClient.getPaymentStatus(transactionKey)
        val payment = paymentService.getByOrderId(orderId)

        if (payment.isTerminal()) {
            log.info("[Recovery] 이미 최종 상태: paymentId=$paymentId, status=${payment.status}")
            return
        }

        val now = ZonedDateTime.now()

        when (pgStatus.status) {
            PG_STATUS_SUCCESS -> {
                payment.confirmPaid(now)
                paymentService.updatePaymentStatus(payment)
                orderService.updateStatus(orderId) { order -> order.pay(now) }
                log.info("[Recovery] 결제 성공 확인: paymentId=$paymentId")
            }
            PG_STATUS_FAILED -> {
                payment.confirmFailed(pgStatus.reason, now)
                paymentService.updatePaymentStatus(payment)
                log.info("[Recovery] 결제 실패 확인: paymentId=$paymentId, reason=${pgStatus.reason}")
            }
            else -> {
                log.info("[Recovery] PG 아직 처리 중: paymentId=$paymentId, pgStatus=${pgStatus.status}")
            }
        }
    }
}
