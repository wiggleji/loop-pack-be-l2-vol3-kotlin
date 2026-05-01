package com.loopers.infrastructure.coupon

import org.springframework.data.jpa.repository.JpaRepository

interface CouponIssueRequestJpaRepository : JpaRepository<CouponIssueRequestEntity, Long> {
    fun findByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): CouponIssueRequestEntity?
}
