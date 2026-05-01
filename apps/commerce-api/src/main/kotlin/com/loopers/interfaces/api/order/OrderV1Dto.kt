package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderItemResult
import com.loopers.application.order.OrderResult
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentStatus
import java.time.ZonedDateTime

class OrderV1Dto {

    data class OrderItemRequest(
        val productId: Long,
        val quantity: Int,
    )

    data class PlaceOrderRequest(
        val items: List<OrderItemRequest>,
        val userCouponId: Long? = null,
        val cardType: CardType,
        val cardNo: String,
    )

    data class UpdateStatusRequest(
        val action: String,
    )

    data class OrderItemResponse(
        val productId: Long,
        val productName: String,
        val brandId: Long,
        val brandName: String,
        val price: Int,
        val quantity: Int,
        val subtotal: Int,
    ) {
        companion object {
            fun from(result: OrderItemResult) = OrderItemResponse(
                productId = result.productId,
                productName = result.productName,
                brandId = result.brandId,
                brandName = result.brandName,
                price = result.price,
                quantity = result.quantity,
                subtotal = result.subtotal,
            )

            fun from(item: OrderItem) = OrderItemResponse(
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

    data class OrderResponse(
        val id: Long,
        val userId: Long,
        val originalTotalPrice: Int,
        val discountAmount: Int,
        val totalPrice: Int,
        val userCouponId: Long?,
        val status: OrderStatus,
        val paymentStatus: PaymentStatus?,
        val paidAt: ZonedDateTime?,
        val shippedAt: ZonedDateTime?,
        val deliveredAt: ZonedDateTime?,
        val cancelledAt: ZonedDateTime?,
        val items: List<OrderItemResponse>,
    ) {
        companion object {
            fun from(result: OrderResult, paymentStatus: PaymentStatus? = null) = OrderResponse(
                id = result.id,
                userId = result.userId,
                originalTotalPrice = result.originalTotalPrice,
                discountAmount = result.discountAmount,
                totalPrice = result.totalPrice,
                userCouponId = result.userCouponId,
                status = result.status,
                paymentStatus = paymentStatus,
                paidAt = result.paidAt,
                shippedAt = result.shippedAt,
                deliveredAt = result.deliveredAt,
                cancelledAt = result.cancelledAt,
                items = result.items.map { OrderItemResponse.from(it) },
            )

            fun from(order: Order) = OrderResponse(
                id = order.id,
                userId = order.userId,
                originalTotalPrice = order.originalTotalPrice,
                discountAmount = order.discountAmount,
                totalPrice = order.totalPrice,
                userCouponId = order.userCouponId,
                status = order.status,
                paymentStatus = null,
                paidAt = order.paidAt,
                shippedAt = order.shippedAt,
                deliveredAt = order.deliveredAt,
                cancelledAt = order.cancelledAt,
                items = order.items.map { OrderItemResponse.from(it) },
            )
        }
    }
}
