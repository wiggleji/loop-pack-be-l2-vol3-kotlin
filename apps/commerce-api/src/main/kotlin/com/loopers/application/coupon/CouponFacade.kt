package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponTemplate
import com.loopers.domain.coupon.CouponTemplateService
import com.loopers.domain.coupon.UserCouponService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponFacade(
    private val couponTemplateService: CouponTemplateService,
    private val userCouponService: UserCouponService,
) {

    @Transactional
    fun createTemplate(cmd: CreateCouponTemplateCommand): CouponTemplateResult {
        val template = CouponTemplate(
            name = cmd.name,
            type = cmd.type,
            discountValue = cmd.discountValue,
            minOrderAmount = cmd.minOrderAmount,
            maxIssuance = cmd.maxIssuance,
            expiresAt = cmd.expiresAt,
        )
        return CouponTemplateResult.from(couponTemplateService.create(template))
    }

    @Transactional(readOnly = true)
    fun getTemplates(): List<CouponTemplateResult> =
        couponTemplateService.findAll().map { CouponTemplateResult.from(it) }

    @Transactional(readOnly = true)
    fun getTemplate(id: Long): CouponTemplateResult =
        CouponTemplateResult.from(couponTemplateService.getById(id))

    @Transactional
    fun deleteTemplate(id: Long) = couponTemplateService.delete(id)

    @Transactional
    fun issueCoupon(userId: Long, couponTemplateId: Long): UserCouponResult {
        val userCoupon = userCouponService.issue(userId, couponTemplateId)
        val template = couponTemplateService.getById(couponTemplateId)
        return UserCouponResult.from(userCoupon, template)
    }

    @Transactional(readOnly = true)
    fun getUserCoupons(userId: Long): List<UserCouponResult> {
        val userCoupons = userCouponService.findByUserId(userId)
        val templateIds = userCoupons.map { it.couponTemplateId }.distinct()
        val templateMap = couponTemplateService.findAllByIds(templateIds).associateBy { it.id }

        return userCoupons.mapNotNull { userCoupon ->
            val template = templateMap[userCoupon.couponTemplateId] ?: return@mapNotNull null
            UserCouponResult.from(userCoupon, template)
        }
    }
}
