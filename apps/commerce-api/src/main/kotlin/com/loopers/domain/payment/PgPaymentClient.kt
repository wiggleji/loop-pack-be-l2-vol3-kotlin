package com.loopers.domain.payment

/**
 * PG 결제 클라이언트 - 도메인 포트 인터페이스
 * Infrastructure 레이어에서 RestClient로 구현
 */
interface PgPaymentClient {
    fun requestPayment(request: PgPaymentRequest): PgPaymentResponse
    fun getPaymentStatus(transactionKey: String): PgPaymentStatusResponse
}

data class PgPaymentRequest(
    val orderId: Long,
    val amount: Int,
    val cardType: String,
    val cardNo: String,
    val callbackUrl: String,
)

data class PgPaymentResponse(
    val transactionKey: String,
    val status: String,
)

data class PgPaymentStatusResponse(
    val transactionKey: String,
    val status: String,
    val reason: String?,
)
