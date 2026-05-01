package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
) {

    @Transactional
    fun createPayment(payment: Payment): Payment =
        paymentRepository.save(payment)

    @Transactional(readOnly = true)
    fun getByOrderId(orderId: Long): Payment =
        paymentRepository.findByOrderId(orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[$orderId] 해당 주문의 결제 정보가 존재하지 않습니다.")

    @Transactional(readOnly = true)
    fun findByOrderId(orderId: Long): Payment? =
        paymentRepository.findByOrderId(orderId)

    @Transactional(readOnly = true)
    fun getByTransactionKey(transactionKey: String): Payment =
        paymentRepository.findByTransactionKey(transactionKey)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[$transactionKey] 해당 트랜잭션의 결제 정보가 존재하지 않습니다.")

    @Transactional(readOnly = true)
    fun findByStatusIn(statuses: List<PaymentStatus>): List<Payment> =
        paymentRepository.findByStatusIn(statuses)

    @Transactional
    fun updatePaymentStatus(payment: Payment) {
        paymentRepository.updateStatus(payment)
    }
}
