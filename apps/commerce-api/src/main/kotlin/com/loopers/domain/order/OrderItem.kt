package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

/**
 * 주문 항목 도메인 모델
 *
 * Order aggregate의 일부로, 주문 당시 상품 스냅샷을 보관한다.
 *
 * @property orderId 주문 ID (Order 생성 전에는 0L)
 * @property productId 상품 ID (스냅샷)
 * @property productName 상품명 (스냅샷)
 * @property brandId 브랜드 ID (스냅샷)
 * @property brandName 브랜드명 (스냅샷)
 * @property price 주문 당시 가격 (스냅샷)
 * @property quantity 주문 수량
 */
class OrderItem(
    val orderId: Long,
    val productId: Long,
    val productName: String,
    val brandId: Long,
    val brandName: String,
    val price: Int,
    val quantity: Int,
    val id: Long = 0L,
) {
    init {
        if (productName.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.")
        if (brandName.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.")
        if (price < 0) throw CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.")
        if (quantity <= 0) throw CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.")
    }

    fun subtotal(): Int = price * quantity
}
