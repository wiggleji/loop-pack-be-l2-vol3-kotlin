package com.loopers.infrastructure.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.order.OrderItem
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "order_items",
    indexes = [
        Index(name = "idx_order_items_order_id", columnList = "order_id"),
        Index(name = "idx_order_items_brand_order", columnList = "brand_id, order_id"),
    ]
)
class OrderItemEntity(
    orderId: Long,
    productId: Long,
    productName: String,
    brandId: Long,
    brandName: String,
    price: Int,
    quantity: Int,
) : BaseEntity() {

    @Column(name = "order_id", nullable = false)
    val orderId: Long = orderId

    @Column(name = "product_id", nullable = false)
    val productId: Long = productId

    @Column(name = "product_name", nullable = false)
    val productName: String = productName

    @Column(name = "brand_id", nullable = false)
    val brandId: Long = brandId

    @Column(name = "brand_name", nullable = false)
    val brandName: String = brandName

    @Column(nullable = false)
    val price: Int = price

    @Column(nullable = false)
    val quantity: Int = quantity

    fun toDomain(): OrderItem = OrderItem(
        id = this.id,
        orderId = this.orderId,
        productId = this.productId,
        productName = this.productName,
        brandId = this.brandId,
        brandName = this.brandName,
        price = this.price,
        quantity = this.quantity,
    )

    companion object {
        fun from(item: OrderItem, orderId: Long): OrderItemEntity = OrderItemEntity(
            orderId = orderId,
            productId = item.productId,
            productName = item.productName,
            brandId = item.brandId,
            brandName = item.brandName,
            price = item.price,
            quantity = item.quantity,
        )
    }
}
