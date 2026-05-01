package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

/**
 * 사용자 쿠폰 도메인 모델 (JPA 비의존)
 *
 * @property id 식별자
 * @property userId 사용자 ID
 * @property couponTemplateId 쿠폰 템플릿 ID
 * @property status 쿠폰 상태
 * @property usedOrderId 사용된 주문 ID (사용 완료 시)
 */
class UserCoupon(
    val userId: Long,
    val couponTemplateId: Long,
    status: UserCouponStatus = UserCouponStatus.AVAILABLE,
    usedOrderId: Long? = null,
    val id: Long = 0L,
) {
    var status: UserCouponStatus = status
        private set

    var usedOrderId: Long? = usedOrderId
        private set

    fun requireAvailable() {
        if (status != UserCouponStatus.AVAILABLE)
            throw CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다. 현재 상태: $status")
    }

    fun use(orderId: Long) {
        requireAvailable()
        this.status = UserCouponStatus.USED
        this.usedOrderId = orderId
    }

    fun expire() {
        if (status != UserCouponStatus.AVAILABLE)
            throw CoreException(ErrorType.BAD_REQUEST, "사용 가능한 쿠폰만 만료 처리할 수 있습니다.")
        this.status = UserCouponStatus.EXPIRED
    }
}
