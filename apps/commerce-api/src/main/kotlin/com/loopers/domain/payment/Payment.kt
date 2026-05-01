package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.ZonedDateTime

/**
 * 결제 도메인 모델 (JPA 비의존)
 *
 * 상태 전이: PENDING → PAID / FAILED, UNKNOWN → PAID / FAILED
 */
class Payment(
    val orderId: Long,
    val amount: Int,
    val cardType: CardType,
    val cardNo: String,
    val id: Long = 0L,
    transactionKey: String? = null,
    status: PaymentStatus = PaymentStatus.PENDING,
    reason: String? = null,
    callbackReceivedAt: ZonedDateTime? = null,
) {
    var transactionKey: String? = transactionKey
        private set

    var status: PaymentStatus = status
        private set

    var reason: String? = reason
        private set

    var callbackReceivedAt: ZonedDateTime? = callbackReceivedAt
        private set

    init {
        if (amount < 0) throw CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0 이상이어야 합니다.")
        if (cardNo.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "카드 번호는 비어있을 수 없습니다.")
    }

    fun confirmPaid(receivedAt: ZonedDateTime) {
        requireNotTerminal()
        this.status = PaymentStatus.PAID
        this.callbackReceivedAt = receivedAt
    }

    fun confirmFailed(reason: String?, receivedAt: ZonedDateTime) {
        requireNotTerminal()
        this.status = PaymentStatus.FAILED
        this.reason = reason
        this.callbackReceivedAt = receivedAt
    }

    fun markUnknown(reason: String?) {
        requireNotTerminal()
        this.status = PaymentStatus.UNKNOWN
        this.reason = reason
    }

    fun isTerminal(): Boolean = status.isTerminal()

    private fun requireNotTerminal() {
        if (status.isTerminal()) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "[${status}] 이미 최종 상태인 결제는 변경할 수 없습니다.",
            )
        }
    }
}
