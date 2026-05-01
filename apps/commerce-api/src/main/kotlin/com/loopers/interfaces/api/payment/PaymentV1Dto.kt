package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentResult
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentStatus
import java.time.ZonedDateTime

class PaymentV1Dto {

    data class PaymentCallbackRequest(
        val transactionKey: String,
        val status: String,
        val reason: String? = null,
    )

    data class PaymentResponse(
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
            fun from(result: PaymentResult) = PaymentResponse(
                id = result.id,
                orderId = result.orderId,
                transactionKey = result.transactionKey,
                amount = result.amount,
                cardType = result.cardType,
                cardNo = result.cardNo,
                status = result.status,
                reason = result.reason,
                callbackReceivedAt = result.callbackReceivedAt,
            )
        }
    }
}
