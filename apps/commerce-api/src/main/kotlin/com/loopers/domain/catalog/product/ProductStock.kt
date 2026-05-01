package com.loopers.domain.catalog.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class ProductStock(
    productId: Long,
    quantity: Int,
    val id: Long = 0L,
) {
    var productId: Long = productId
        private set

    var quantity: Int = quantity
        private set

    val isSoldOut: Boolean get() = quantity == 0

    init {
        if (quantity < 0) throw CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.")
    }

    fun validate(qty: Int) {
        if (qty <= 0) throw CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.")
        if (quantity < qty) throw CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다. 현재 재고: $quantity, 요청 수량: $qty")
    }

    fun decrement(qty: Int) {
        if (qty <= 0) throw CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.")
        if (quantity - qty < 0) throw CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다. 현재 재고: $quantity, 요청 수량: $qty")
        this.quantity -= qty
    }

    fun update(qty: Int) {
        if (qty < 0) throw CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.")
        this.quantity = qty
    }
}
