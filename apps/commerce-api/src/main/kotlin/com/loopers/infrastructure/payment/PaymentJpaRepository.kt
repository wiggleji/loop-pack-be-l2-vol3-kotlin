package com.loopers.infrastructure.payment

import org.springframework.data.jpa.repository.JpaRepository

interface PaymentJpaRepository : JpaRepository<PaymentEntity, Long> {

    fun findByOrderIdAndDeletedAtIsNull(orderId: Long): PaymentEntity?

    fun findByTransactionKeyAndDeletedAtIsNull(transactionKey: String): PaymentEntity?

    fun findByStatusInAndDeletedAtIsNull(statuses: List<String>): List<PaymentEntity>
}
