package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponTemplate
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponStatus
import java.time.LocalDate

data class CouponTemplateResult(
    val id: Long,
    val name: String,
    val type: CouponType,
    val discountValue: Int,
    val minOrderAmount: Int,
    val maxIssuance: Int?,
    val issuedCount: Int,
    val expiresAt: LocalDate,
) {
    companion object {
        fun from(template: CouponTemplate): CouponTemplateResult = CouponTemplateResult(
            id = template.id,
            name = template.name,
            type = template.type,
            discountValue = template.discountValue,
            minOrderAmount = template.minOrderAmount,
            maxIssuance = template.maxIssuance,
            issuedCount = template.issuedCount,
            expiresAt = template.expiresAt,
        )
    }
}

data class UserCouponResult(
    val id: Long,
    val userId: Long,
    val couponTemplateId: Long,
    val status: UserCouponStatus,
    val usedOrderId: Long?,
    val template: CouponTemplateResult?,
) {
    companion object {
        fun from(userCoupon: UserCoupon, template: CouponTemplate? = null): UserCouponResult = UserCouponResult(
            id = userCoupon.id,
            userId = userCoupon.userId,
            couponTemplateId = userCoupon.couponTemplateId,
            status = userCoupon.status,
            usedOrderId = userCoupon.usedOrderId,
            template = template?.let { CouponTemplateResult.from(it) },
        )
    }
}
