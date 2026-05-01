package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponType
import java.time.LocalDate

data class CreateCouponTemplateCommand(
    val name: String,
    val type: CouponType,
    val discountValue: Int,
    val minOrderAmount: Int = 0,
    val maxIssuance: Int? = null,
    val expiresAt: LocalDate,
)
