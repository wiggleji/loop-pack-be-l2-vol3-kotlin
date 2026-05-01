package com.loopers.infrastructure.payment

import com.loopers.domain.BaseEntity
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(
    name = "payments",
    indexes = [
        Index(name = "idx_payments_order_id", columnList = "order_id", unique = true),
        Index(name = "idx_payments_transaction_key", columnList = "transaction_key", unique = true),
        Index(name = "idx_payments_status", columnList = "status"),
    ],
)
class PaymentEntity(
    orderId: Long,
    amount: Int,
    cardType: String,
    cardNo: String,
    transactionKey: String? = null,
    status: String = PaymentStatus.PENDING.name,
    reason: String? = null,
    callbackReceivedAt: ZonedDateTime? = null,
) : BaseEntity() {

    @Column(name = "order_id", nullable = false)
    val orderId: Long = orderId

    @Column(name = "amount", nullable = false)
    val amount: Int = amount

    @Column(name = "card_type", nullable = false, length = 20)
    val cardType: String = cardType

    @Column(name = "card_no", nullable = false, length = 20)
    val cardNo: String = cardNo

    @Column(name = "transaction_key", length = 100)
    var transactionKey: String? = transactionKey
        protected set

    @Column(name = "status", nullable = false, length = 20)
    var status: String = status
        protected set

    @Column(name = "reason")
    var reason: String? = reason
        protected set

    @Column(name = "callback_received_at")
    var callbackReceivedAt: ZonedDateTime? = callbackReceivedAt
        protected set

    fun updateFromDomain(payment: Payment) {
        this.transactionKey = payment.transactionKey
        this.status = payment.status.name
        this.reason = payment.reason
        this.callbackReceivedAt = payment.callbackReceivedAt
    }

    fun toDomain(): Payment = Payment(
        id = this.id,
        orderId = this.orderId,
        amount = this.amount,
        cardType = CardType.valueOf(this.cardType),
        cardNo = this.cardNo,
        transactionKey = this.transactionKey,
        status = PaymentStatus.valueOf(this.status),
        reason = this.reason,
        callbackReceivedAt = this.callbackReceivedAt,
    )

    companion object {
        fun from(payment: Payment): PaymentEntity = PaymentEntity(
            orderId = payment.orderId,
            amount = payment.amount,
            cardType = payment.cardType.name,
            cardNo = payment.cardNo,
            transactionKey = payment.transactionKey,
            status = payment.status.name,
            reason = payment.reason,
            callbackReceivedAt = payment.callbackReceivedAt,
        )
    }
}
