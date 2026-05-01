package com.loopers.infrastructure.coupon

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserCouponJpaRepository : JpaRepository<UserCouponEntity, Long> {

    @Query("SELECT uc FROM UserCouponEntity uc WHERE uc.userId = :userId AND uc.deletedAt IS NULL")
    fun findAllByUserId(@Param("userId") userId: Long): List<UserCouponEntity>

    fun existsByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): Boolean
}
