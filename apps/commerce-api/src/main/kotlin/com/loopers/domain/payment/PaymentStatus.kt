package com.loopers.domain.payment

enum class PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    UNKNOWN,
    ;

    fun isTerminal(): Boolean = this == PAID || this == FAILED
}
