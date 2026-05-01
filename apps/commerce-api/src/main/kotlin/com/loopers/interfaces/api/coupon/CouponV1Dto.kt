package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponIssueRequestResult
import com.loopers.application.coupon.CouponTemplateResult
import com.loopers.application.coupon.CreateCouponTemplateCommand
import com.loopers.application.coupon.UserCouponResult
import com.loopers.domain.coupon.CouponIssueStatus
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponStatus
import java.time.LocalDate

class CouponV1Dto {

    // ─── Admin ───

    data class CreateTemplateRequest(
        val name: String,
        val type: CouponType,
        val discountValue: Int,
        val minOrderAmount: Int = 0,
        val maxIssuance: Int? = null,
        val expiresAt: LocalDate,
    ) {
        fun toCommand(): CreateCouponTemplateCommand = CreateCouponTemplateCommand(
            name = name,
            type = type,
            discountValue = discountValue,
            minOrderAmount = minOrderAmount,
            maxIssuance = maxIssuance,
            expiresAt = expiresAt,
        )
    }

    data class TemplateResponse(
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
            fun from(result: CouponTemplateResult): TemplateResponse = TemplateResponse(
                id = result.id,
                name = result.name,
                type = result.type,
                discountValue = result.discountValue,
                minOrderAmount = result.minOrderAmount,
                maxIssuance = result.maxIssuance,
                issuedCount = result.issuedCount,
                expiresAt = result.expiresAt,
            )
        }
    }

    // ─── User ───

    data class IssueCouponRequest(
        val couponTemplateId: Long,
    )

    data class UserCouponResponse(
        val id: Long,
        val userId: Long,
        val couponTemplateId: Long,
        val status: UserCouponStatus,
        val usedOrderId: Long?,
        val template: TemplateResponse?,
    ) {
        companion object {
            fun from(result: UserCouponResult): UserCouponResponse = UserCouponResponse(
                id = result.id,
                userId = result.userId,
                couponTemplateId = result.couponTemplateId,
                status = result.status,
                usedOrderId = result.usedOrderId,
                template = result.template?.let { TemplateResponse.from(it) },
            )
        }
    }

    data class CouponIssueResponse(
        val requestId: Long,
        val couponTemplateId: Long,
        val status: CouponIssueStatus,
        val failReason: String?,
    ) {
        companion object {
            fun from(result: CouponIssueRequestResult): CouponIssueResponse = CouponIssueResponse(
                requestId = result.id,
                couponTemplateId = result.couponTemplateId,
                status = result.status,
                failReason = result.failReason,
            )
        }
    }
}
