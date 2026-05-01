package com.loopers.application.order

import com.loopers.domain.payment.CardType

data class OrderItemCommand(
    val productId: Long,
    val quantity: Int,
)

data class PlaceOrderCommand(
    val items: List<OrderItemCommand>,
    val userCouponId: Long? = null,
    val cardType: CardType,
    val cardNo: String,
)
