package com.loopers.application.payment

import com.loopers.domain.payment.PgPaymentClient
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgPaymentResponse
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PgPaymentCaller(
    private val pgPaymentClient: PgPaymentClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Retry(name = "pgPayment")
    @CircuitBreaker(name = "pgPayment", fallbackMethod = "fallback")
    fun call(request: PgPaymentRequest): PgPaymentResponse {
        return pgPaymentClient.requestPayment(request)
    }

    private fun fallback(request: PgPaymentRequest, ex: Throwable): PgPaymentResponse {
        log.error("[PG Fallback] orderId=${request.orderId}, error=${ex.message}", ex)
        throw PgPaymentException("PG 결제 요청 실패: ${ex.message}", ex)
    }
}

class PgPaymentException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
