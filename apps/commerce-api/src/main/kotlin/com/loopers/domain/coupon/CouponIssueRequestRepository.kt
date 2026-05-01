package com.loopers.domain.coupon

interface CouponIssueRequestRepository {
    fun save(request: CouponIssueRequest): CouponIssueRequest
    fun findById(id: Long): CouponIssueRequest?
    fun findByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): CouponIssueRequest?
}
