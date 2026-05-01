package com.loopers.infrastructure.order

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.ZonedDateTime

interface OrderJpaRepository : JpaRepository<OrderEntity, Long> {

    fun findByUserId(userId: Long): List<OrderEntity>

    @Query("SELECT o FROM OrderEntity o WHERE o.userId = :userId AND o.createdAt >= :startAt AND o.createdAt <= :endAt AND o.deletedAt IS NULL")
    fun findByUserIdAndCreatedAtBetween(userId: Long, startAt: ZonedDateTime, endAt: ZonedDateTime): List<OrderEntity>

    @Query("SELECT o FROM OrderEntity o WHERE o.deletedAt IS NULL")
    fun findAllActive(pageable: Pageable): List<OrderEntity>

    @Query("SELECT o FROM OrderEntity o WHERE o.status = :status AND o.deletedAt IS NULL ORDER BY o.createdAt DESC")
    fun findByStatusAndDeletedAtIsNull(status: String, pageable: Pageable): List<OrderEntity>

    @Query("SELECT o FROM OrderEntity o WHERE o.status = :status AND o.createdAt >= :startAt AND o.createdAt <= :endAt AND o.deletedAt IS NULL ORDER BY o.createdAt DESC")
    fun findByStatusAndCreatedAtBetween(status: String, startAt: ZonedDateTime, endAt: ZonedDateTime, pageable: Pageable): List<OrderEntity>

    @Query("SELECT o FROM OrderEntity o WHERE o.status = :status AND o.createdAt < :olderThan AND o.deletedAt IS NULL ORDER BY o.createdAt ASC")
    fun findByStatusAndCreatedAtBefore(status: String, olderThan: ZonedDateTime, pageable: Pageable): List<OrderEntity>
}

interface OrderItemJpaRepository : JpaRepository<OrderItemEntity, Long> {

    fun findByOrderId(orderId: Long): List<OrderItemEntity>

    fun findByOrderIdIn(orderIds: List<Long>): List<OrderItemEntity>

    @Query("SELECT oi FROM OrderItemEntity oi WHERE oi.brandId = :brandId AND oi.deletedAt IS NULL ORDER BY oi.orderId DESC")
    fun findByBrandId(brandId: Long, pageable: Pageable): List<OrderItemEntity>
}
