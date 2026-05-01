package com.loopers.infrastructure.coupon

import com.loopers.domain.BaseEntity
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version

@Entity
@Table(
    name = "user_coupons",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "coupon_template_id"])],
    indexes = [
        Index(name = "idx_user_coupons_user_id", columnList = "user_id"),
    ]
)
class UserCouponEntity(
    userId: Long,
    couponTemplateId: Long,
    status: UserCouponStatus = UserCouponStatus.AVAILABLE,
    usedOrderId: Long? = null,
) : BaseEntity() {

    @Column(name = "user_id", nullable = false)
    val userId: Long = userId

    @Column(name = "coupon_template_id", nullable = false)
    val couponTemplateId: Long = couponTemplateId

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UserCouponStatus = status
        protected set

    @Column(name = "used_order_id")
    var usedOrderId: Long? = usedOrderId
        protected set

    @Version
    @Column(nullable = false)
    var version: Long = 0L
        protected set

    fun updateStatus(status: UserCouponStatus) {
        this.status = status
    }

    fun updateUsedOrderId(orderId: Long) {
        this.usedOrderId = orderId
    }

    fun toDomain(): UserCoupon = UserCoupon(
        id = this.id,
        userId = this.userId,
        couponTemplateId = this.couponTemplateId,
        status = this.status,
        usedOrderId = this.usedOrderId,
    )

    companion object {
        fun from(userCoupon: UserCoupon): UserCouponEntity = UserCouponEntity(
            userId = userCoupon.userId,
            couponTemplateId = userCoupon.couponTemplateId,
            status = userCoupon.status,
            usedOrderId = userCoupon.usedOrderId,
        )
    }
}
