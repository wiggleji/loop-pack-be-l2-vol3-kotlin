package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import org.springframework.stereotype.Repository

@Repository
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {

    override fun save(payment: Payment): Payment {
        val entity = paymentJpaRepository.save(PaymentEntity.from(payment))
        return entity.toDomain()
    }

    override fun findByOrderId(orderId: Long): Payment? =
        paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(orderId)?.toDomain()

    override fun findByTransactionKey(transactionKey: String): Payment? =
        paymentJpaRepository.findByTransactionKeyAndDeletedAtIsNull(transactionKey)?.toDomain()

    override fun findByStatusIn(statuses: List<PaymentStatus>): List<Payment> =
        paymentJpaRepository.findByStatusInAndDeletedAtIsNull(statuses.map { it.name })
            .map { it.toDomain() }

    override fun updateStatus(payment: Payment) {
        val entity = paymentJpaRepository.findById(payment.id)
            .orElseThrow { IllegalStateException("Payment not found: ${payment.id}") }
        entity.updateFromDomain(payment)
    }
}
