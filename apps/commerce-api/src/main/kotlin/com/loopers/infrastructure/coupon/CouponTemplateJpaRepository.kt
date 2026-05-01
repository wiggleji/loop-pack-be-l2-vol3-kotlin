package com.loopers.infrastructure.coupon

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface CouponTemplateJpaRepository : JpaRepository<CouponTemplateEntity, Long> {

    @Query("SELECT ct FROM CouponTemplateEntity ct WHERE ct.id IN :ids AND ct.deletedAt IS NULL")
    fun findAllByIdIn(ids: List<Long>): List<CouponTemplateEntity>

    @Query("SELECT ct FROM CouponTemplateEntity ct WHERE ct.deletedAt IS NULL")
    fun findAllActive(): List<CouponTemplateEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ct FROM CouponTemplateEntity ct WHERE ct.id = :id AND ct.deletedAt IS NULL")
    fun findByIdWithLock(id: Long): CouponTemplateEntity?
}
