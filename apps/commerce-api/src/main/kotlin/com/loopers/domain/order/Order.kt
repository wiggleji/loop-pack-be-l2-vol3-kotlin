package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.ZonedDateTime

/**
 * 주문 도메인 모델 - Aggregate Root (JPA 비의존)
 *
 * @property id 식별자 (영속화 전에는 0L)
 * @property userId 주문한 사용자 DB ID
 * @property items 주문 항목 목록 (스냅샷)
 * @property originalTotalPrice 할인 전 총 금액
 * @property discountAmount 할인 금액
 * @property totalPrice 최종 결제 금액 (originalTotalPrice - discountAmount)
 * @property userCouponId 사용된 쿠폰 ID (nullable)
 * @property status 주문 상태
 * @property paidAt 결제 시점
 * @property shippedAt 배송 시작 시점
 * @property deliveredAt 배송 완료 시점
 * @property cancelledAt 취소/환불 시점
 */
class Order(
    val userId: Long,
    val items: List<OrderItem>,
    val originalTotalPrice: Int,
    val discountAmount: Int = 0,
    val userCouponId: Long? = null,
    val id: Long = 0L,
    status: OrderStatus = OrderStatus.PLACED,
    paidAt: ZonedDateTime? = null,
    shippedAt: ZonedDateTime? = null,
    deliveredAt: ZonedDateTime? = null,
    cancelledAt: ZonedDateTime? = null,
) {
    val totalPrice: Int get() = originalTotalPrice - discountAmount

    var status: OrderStatus = status
        private set

    var paidAt: ZonedDateTime? = paidAt
        private set

    var shippedAt: ZonedDateTime? = shippedAt
        private set

    var deliveredAt: ZonedDateTime? = deliveredAt
        private set

    var cancelledAt: ZonedDateTime? = cancelledAt
        private set

    init {
        if (items.isEmpty()) throw CoreException(ErrorType.BAD_REQUEST, "주문 항목이 비어있을 수 없습니다.")
        if (originalTotalPrice < 0) throw CoreException(ErrorType.BAD_REQUEST, "총 금액은 0 이상이어야 합니다.")
        if (discountAmount < 0) throw CoreException(ErrorType.BAD_REQUEST, "할인 금액은 0 이상이어야 합니다.")
        if (totalPrice < 0) throw CoreException(ErrorType.BAD_REQUEST, "최종 결제 금액은 0 이상이어야 합니다.")
    }

    fun pay(paidAt: ZonedDateTime) {
        requireStatus(OrderStatus.PLACED, "결제")
        this.status = OrderStatus.PAID
        this.paidAt = paidAt
    }

    fun startPreparing() {
        requireStatus(OrderStatus.PAID, "준비")
        this.status = OrderStatus.PREPARING
    }

    fun ship(shippedAt: ZonedDateTime) {
        requireStatus(OrderStatus.PREPARING, "배송")
        this.status = OrderStatus.SHIPPING
        this.shippedAt = shippedAt
    }

    fun deliver(deliveredAt: ZonedDateTime) {
        requireStatus(OrderStatus.SHIPPING, "배송완료")
        this.status = OrderStatus.DELIVERED
        this.deliveredAt = deliveredAt
    }

    fun cancel(cancelledAt: ZonedDateTime) {
        if (status != OrderStatus.PLACED && status != OrderStatus.PAID) {
            throw CoreException(ErrorType.BAD_REQUEST, "[${status}] 상태에서는 취소할 수 없습니다. (PLACED, PAID만 가능)")
        }
        this.status = OrderStatus.CANCELLED
        this.cancelledAt = cancelledAt
    }

    fun refund(cancelledAt: ZonedDateTime) {
        requireStatus(OrderStatus.DELIVERED, "환불")
        this.status = OrderStatus.REFUNDED
        this.cancelledAt = cancelledAt
    }

    private fun requireStatus(expected: OrderStatus, action: String) {
        if (status != expected) {
            throw CoreException(ErrorType.BAD_REQUEST, "[${status}] 상태에서는 ${action}할 수 없습니다. (${expected}만 가능)")
        }
    }

    companion object {
        fun create(
            userId: Long,
            items: List<OrderItem>,
            discountAmount: Int = 0,
            userCouponId: Long? = null,
        ): Order {
            if (items.isEmpty()) throw CoreException(ErrorType.BAD_REQUEST, "주문 항목이 비어있을 수 없습니다.")
            val originalTotalPrice = items.sumOf { it.subtotal() }
            return Order(
                userId = userId,
                items = items,
                originalTotalPrice = originalTotalPrice,
                discountAmount = discountAmount,
                userCouponId = userCouponId,
                status = OrderStatus.PLACED,
            )
        }
    }
}
