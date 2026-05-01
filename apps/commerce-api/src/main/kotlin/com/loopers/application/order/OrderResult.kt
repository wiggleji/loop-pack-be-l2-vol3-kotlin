package com.loopers.application.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderStatus
import java.time.ZonedDateTime

data class OrderItemResult(
    val id: Long,
    val productId: Long,
    val productName: String,
    val brandId: Long,
    val brandName: String,
    val price: Int,
    val quantity: Int,
    val subtotal: Int,
) {
    companion object {
        fun from(item: OrderItem): OrderItemResult = OrderItemResult(
            id = item.id,
            productId = item.productId,
            productName = item.productName,
            brandId = item.brandId,
            brandName = item.brandName,
            price = item.price,
            quantity = item.quantity,
            subtotal = item.subtotal(),
        )
    }
}

data class OrderResult(
    val id: Long,
    val userId: Long,
    val originalTotalPrice: Int,
    val discountAmount: Int,
    val totalPrice: Int,
    val userCouponId: Long?,
    val status: OrderStatus,
    val paidAt: ZonedDateTime?,
    val shippedAt: ZonedDateTime?,
    val deliveredAt: ZonedDateTime?,
    val cancelledAt: ZonedDateTime?,
    val items: List<OrderItemResult>,
) {
    companion object {
        fun from(order: Order): OrderResult = OrderResult(
            id = order.id,
            userId = order.userId,
            originalTotalPrice = order.originalTotalPrice,
            discountAmount = order.discountAmount,
            totalPrice = order.totalPrice,
            userCouponId = order.userCouponId,
            status = order.status,
            paidAt = order.paidAt,
            shippedAt = order.shippedAt,
            deliveredAt = order.deliveredAt,
            cancelledAt = order.cancelledAt,
            items = order.items.map { OrderItemResult.from(it) },
        )
    }
}
