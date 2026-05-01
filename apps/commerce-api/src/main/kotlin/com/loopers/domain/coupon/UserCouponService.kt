package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserCouponService(
    private val userCouponRepository: UserCouponRepository,
    private val couponTemplateRepository: CouponTemplateRepository,
) {

    @Transactional
    fun issue(userId: Long, couponTemplateId: Long): UserCoupon {
        val template = couponTemplateRepository.findById(couponTemplateId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[$couponTemplateId] 해당 ID에 해당하는 쿠폰 템플릿이 존재하지 않습니다.")

        return doIssue(userId, couponTemplateId, template)
    }

    /**
     * 비관적 락을 사용한 쿠폰 발급 (선착순 동시성 제어)
     */
    @Transactional
    fun issueWithLock(userId: Long, couponTemplateId: Long): UserCoupon {
        val template = couponTemplateRepository.findByIdWithLock(couponTemplateId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[$couponTemplateId] 해당 ID에 해당하는 쿠폰 템플릿이 존재하지 않습니다.")

        return doIssue(userId, couponTemplateId, template)
    }

    private fun doIssue(userId: Long, couponTemplateId: Long, template: CouponTemplate): UserCoupon {
        template.requireIssuable()

        if (userCouponRepository.existsByUserIdAndCouponTemplateId(userId, couponTemplateId))
            throw CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.")

        template.incrementIssuedCount()
        couponTemplateRepository.save(template)

        val userCoupon = UserCoupon(userId = userId, couponTemplateId = couponTemplateId)
        return userCouponRepository.save(userCoupon)
    }

    @Transactional
    fun useForOrder(userCouponId: Long, orderId: Long): UserCoupon {
        val userCoupon = userCouponRepository.findById(userCouponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[$userCouponId] 해당 ID에 해당하는 사용자 쿠폰이 존재하지 않습니다.")

        userCoupon.use(orderId)
        return userCouponRepository.save(userCoupon)
    }

    @Transactional(readOnly = true)
    fun getById(id: Long): UserCoupon =
        userCouponRepository.findById(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[$id] 해당 ID에 해당하는 사용자 쿠폰이 존재하지 않습니다.")

    @Transactional(readOnly = true)
    fun findByUserId(userId: Long): List<UserCoupon> =
        userCouponRepository.findByUserId(userId)
}
