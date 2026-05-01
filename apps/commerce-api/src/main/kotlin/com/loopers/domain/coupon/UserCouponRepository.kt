package com.loopers.domain.coupon

interface UserCouponRepository {
    fun save(userCoupon: UserCoupon): UserCoupon
    fun findById(id: Long): UserCoupon?
    fun findByUserId(userId: Long): List<UserCoupon>
    fun existsByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): Boolean
}
