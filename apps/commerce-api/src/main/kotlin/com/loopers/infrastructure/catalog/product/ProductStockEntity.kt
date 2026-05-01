package com.loopers.infrastructure.catalog.product

import com.loopers.domain.BaseEntity
import com.loopers.domain.catalog.product.ProductStock
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "product_stocks")
class ProductStockEntity(
    productId: Long,
    quantity: Int,
) : BaseEntity() {

    @Column(name = "product_id", nullable = false, unique = true)
    var productId: Long = productId
        protected set

    @Column(nullable = false)
    var quantity: Int = quantity
        protected set

    fun update(quantity: Int) {
        this.quantity = quantity
    }

    fun toDomain(): ProductStock = ProductStock(
        id = this.id,
        productId = this.productId,
        quantity = this.quantity,
    )

    companion object {
        fun from(stock: ProductStock): ProductStockEntity = ProductStockEntity(
            productId = stock.productId,
            quantity = stock.quantity,
        )
    }
}
