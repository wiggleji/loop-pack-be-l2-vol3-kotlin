package com.loopers.application.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PgPaymentRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PaymentFacade(
    private val paymentService: PaymentService,
    private val pgPaymentCaller: PgPaymentCaller,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * PG 결제 요청 (트랜잭션 없음 — PG 호출이 DB 커넥션을 점유하지 않도록)
     *
     * 1. 기존 결제 확인 (멱등성)
     * 2. PG 호출 (retry + circuit breaker)
     * 3. 결제 저장: PG 성공 시 PENDING, 실패 시 UNKNOWN
     */
    fun requestPayment(cmd: RequestPaymentCommand): PaymentResult {
        // 1. 멱등성 체크: 이미 결제가 존재하면 그대로 반환
        val existing = paymentService.findByOrderId(cmd.orderId)
        if (existing != null) {
            log.info("[Payment] 이미 존재하는 결제: orderId=${cmd.orderId}, status=${existing.status}")
            return PaymentResult.from(existing)
        }

        // 2. PG 호출
        val callbackUrl = "/api/v1/payments/callback"
        val pgRequest = PgPaymentRequest(
            orderId = cmd.orderId,
            amount = cmd.amount,
            cardType = cmd.cardType.name,
            cardNo = cmd.cardNo,
            callbackUrl = callbackUrl,
        )

        return try {
            val pgResponse = pgPaymentCaller.call(pgRequest)

            // 3. PENDING 결제 저장
            val payment = Payment(
                orderId = cmd.orderId,
                amount = cmd.amount,
                cardType = cmd.cardType,
                cardNo = cmd.cardNo,
                transactionKey = pgResponse.transactionKey,
            )
            val saved = paymentService.createPayment(payment)
            log.info("[Payment] PG 요청 성공: orderId=${cmd.orderId}, transactionKey=${pgResponse.transactionKey}")
            PaymentResult.from(saved)
        } catch (ex: PgPaymentException) {
            // 3. UNKNOWN 결제 저장 (PG 호출 실패)
            val payment = Payment(
                orderId = cmd.orderId,
                amount = cmd.amount,
                cardType = cmd.cardType,
                cardNo = cmd.cardNo,
            )
            payment.markUnknown(ex.message)
            val saved = paymentService.createPayment(payment)
            log.warn("[Payment] PG 요청 실패 → UNKNOWN: orderId=${cmd.orderId}, reason=${ex.message}")
            PaymentResult.from(saved)
        }
    }
}
