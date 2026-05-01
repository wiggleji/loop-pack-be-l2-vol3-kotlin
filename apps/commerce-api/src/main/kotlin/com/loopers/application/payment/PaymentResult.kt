package com.loopers.application.payment

import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import java.time.ZonedDateTime

data class PaymentResult(
    val id: Long,
    val orderId: Long,
    val transactionKey: String?,
    val amount: Int,
    val cardType: CardType,
    val cardNo: String,
    val status: PaymentStatus,
    val reason: String?,
    val callbackReceivedAt: ZonedDateTime?,
) {
    companion object {
        fun from(payment: Payment): PaymentResult = PaymentResult(
            id = payment.id,
            orderId = payment.orderId,
            transactionKey = payment.transactionKey,
            amount = payment.amount,
            cardType = payment.cardType,
            cardNo = payment.cardNo,
            status = payment.status,
            reason = payment.reason,
            callbackReceivedAt = payment.callbackReceivedAt,
        )
    }
}
