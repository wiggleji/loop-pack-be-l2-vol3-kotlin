package com.loopers.application.payment.event

import java.time.ZonedDateTime

data class PaymentConfirmedEvent(
    val paymentId: Long,
    val orderId: Long,
    val transactionKey: String,
    val amount: Int,
    val paidAt: ZonedDateTime,
)

data class PaymentFailedEvent(
    val paymentId: Long,
    val orderId: Long,
    val reason: String?,
)
