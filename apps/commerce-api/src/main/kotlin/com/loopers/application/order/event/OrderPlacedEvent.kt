package com.loopers.application.order.event

import com.loopers.domain.payment.CardType

data class OrderItemSnapshot(
    val productId: Long,
    val quantity: Int,
    val price: Int,
)

data class OrderPlacedEvent(
    val orderId: Long,
    val userId: Long,
    val items: List<OrderItemSnapshot>,
    val originalTotalPrice: Int,
    val discountAmount: Int,
    val totalPrice: Int,
    val userCouponId: Long?,
    val cardType: CardType,
    val cardNo: String,
)
