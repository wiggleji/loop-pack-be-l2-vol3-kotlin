package com.loopers.infrastructure.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(
    name = "orders",
    indexes = [
        Index(name = "idx_orders_user_created", columnList = "user_id, created_at DESC"),
        Index(name = "idx_orders_status_created", columnList = "status, created_at"),
    ]
)
class OrderEntity(
    userId: Long,
    originalTotalPrice: Int,
    discountAmount: Int = 0,
    totalPrice: Int,
    userCouponId: Long? = null,
    status: String = OrderStatus.PLACED.name,
    paidAt: ZonedDateTime? = null,
    shippedAt: ZonedDateTime? = null,
    deliveredAt: ZonedDateTime? = null,
    cancelledAt: ZonedDateTime? = null,
) : BaseEntity() {

    @Column(name = "user_id", nullable = false)
    val userId: Long = userId

    @Column(name = "original_total_price", nullable = false)
    val originalTotalPrice: Int = originalTotalPrice

    @Column(name = "discount_amount", nullable = false)
    val discountAmount: Int = discountAmount

    @Column(name = "total_price", nullable = false)
    val totalPrice: Int = totalPrice

    @Column(name = "user_coupon_id")
    val userCouponId: Long? = userCouponId

    @Column(name = "status", nullable = false, length = 20)
    var status: String = status
        protected set

    @Column(name = "paid_at")
    var paidAt: ZonedDateTime? = paidAt
        protected set

    @Column(name = "shipped_at")
    var shippedAt: ZonedDateTime? = shippedAt
        protected set

    @Column(name = "delivered_at")
    var deliveredAt: ZonedDateTime? = deliveredAt
        protected set

    @Column(name = "cancelled_at")
    var cancelledAt: ZonedDateTime? = cancelledAt
        protected set

    fun updateStatus(order: Order) {
        this.status = order.status.name
        this.paidAt = order.paidAt
        this.shippedAt = order.shippedAt
        this.deliveredAt = order.deliveredAt
        this.cancelledAt = order.cancelledAt
    }

    fun toDomain(items: List<OrderItemEntity>): Order = Order(
        id = this.id,
        userId = this.userId,
        items = items.map { it.toDomain() },
        originalTotalPrice = this.originalTotalPrice,
        discountAmount = this.discountAmount,
        userCouponId = this.userCouponId,
        status = OrderStatus.valueOf(this.status),
        paidAt = this.paidAt,
        shippedAt = this.shippedAt,
        deliveredAt = this.deliveredAt,
        cancelledAt = this.cancelledAt,
    )

    companion object {
        fun from(order: Order): OrderEntity = OrderEntity(
            userId = order.userId,
            originalTotalPrice = order.originalTotalPrice,
            discountAmount = order.discountAmount,
            totalPrice = order.totalPrice,
            userCouponId = order.userCouponId,
            status = order.status.name,
            paidAt = order.paidAt,
            shippedAt = order.shippedAt,
            deliveredAt = order.deliveredAt,
            cancelledAt = order.cancelledAt,
        )
    }
}
