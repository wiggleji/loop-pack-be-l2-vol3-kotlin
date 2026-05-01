package com.loopers.domain.payment

interface PaymentRepository {
    fun save(payment: Payment): Payment
    fun findByOrderId(orderId: Long): Payment?
    fun findByTransactionKey(transactionKey: String): Payment?
    fun findByStatusIn(statuses: List<PaymentStatus>): List<Payment>
    fun updateStatus(payment: Payment)
}
