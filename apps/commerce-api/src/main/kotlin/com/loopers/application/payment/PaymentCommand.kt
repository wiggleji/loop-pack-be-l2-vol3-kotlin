package com.loopers.application.payment

import com.loopers.domain.payment.CardType

data class RequestPaymentCommand(
    val orderId: Long,
    val amount: Int,
    val cardType: CardType,
    val cardNo: String,
)

data class PaymentCallbackCommand(
    val transactionKey: String,
    val status: String,
    val reason: String?,
)
