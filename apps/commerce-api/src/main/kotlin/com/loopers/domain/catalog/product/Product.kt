package com.loopers.domain.catalog.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

/**
 * 상품 도메인 모델 (JPA 비의존)
 *
 * @property id 식별자 (영속화 전에는 0L)
 * @property brandId 브랜드 ID (참조 키)
 * @property name 상품명
 * @property description 상품 설명
 * @property price 가격 (0 이상)
 * @property likeCount 좋아요 수 (비정규화, 0 이상)
 * @property status 상품 상태
 */
class Product(
    brandId: Long,
    name: String,
    description: String,
    price: Int,
    likeCount: Int = 0,
    status: ProductStatus = ProductStatus.HIDDEN,
    val id: Long = 0L,
) {
    var brandId: Long = brandId
        private set

    var name: String = name
        private set

    var description: String = description
        private set

    var price: Int = price
        private set

    var likeCount: Int = likeCount
        private set

    var status: ProductStatus = status
        private set

    init {
        if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.")
        if (price < 0) throw CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.")
    }

    fun update(name: String, description: String, price: Int) {
        if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.")
        if (price < 0) throw CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.")
        this.name = name
        this.description = description
        this.price = price
    }

    fun incrementLike() {
        this.likeCount++
    }

    fun decrementLike() {
        if (likeCount <= 0) throw CoreException(ErrorType.BAD_REQUEST, "좋아요 수는 0 미만이 될 수 없습니다.")
        this.likeCount--
    }

    // ACTIVE → HIDDEN
    fun hide() {
        if (status != ProductStatus.ACTIVE)
            throw CoreException(ErrorType.BAD_REQUEST, "판매중인 상품만 숨김 처리할 수 있습니다.")
        this.status = ProductStatus.HIDDEN
    }

    // HIDDEN → ACTIVE
    fun show() {
        if (status != ProductStatus.HIDDEN)
            throw CoreException(ErrorType.BAD_REQUEST, "숨김 상태인 상품만 노출 복구할 수 있습니다.")
        this.status = ProductStatus.ACTIVE
    }

    // ACTIVE → SOLD_OUT
    fun markSoldOut() {
        if (status != ProductStatus.ACTIVE)
            throw CoreException(ErrorType.BAD_REQUEST, "판매중인 상품만 품절 처리할 수 있습니다.")
        this.status = ProductStatus.SOLD_OUT
    }

    // SOLD_OUT → ACTIVE
    fun restock() {
        if (status != ProductStatus.SOLD_OUT)
            throw CoreException(ErrorType.BAD_REQUEST, "품절 상태인 상품만 재입고 처리할 수 있습니다.")
        this.status = ProductStatus.ACTIVE
    }

    // ACTIVE, HIDDEN, SOLD_OUT → SUSPENDED
    fun suspend() {
        if (status == ProductStatus.SUSPENDED)
            throw CoreException(ErrorType.CONFLICT, "이미 판매중지된 상품입니다.")
        if (status == ProductStatus.DISCONTINUED)
            throw CoreException(ErrorType.BAD_REQUEST, "판매종료된 상품은 상태를 변경할 수 없습니다.")
        this.status = ProductStatus.SUSPENDED
    }

    // SUSPENDED → ACTIVE
    fun reinstate() {
        if (status != ProductStatus.SUSPENDED)
            throw CoreException(ErrorType.BAD_REQUEST, "판매중지 상태인 상품만 제재 해제할 수 있습니다.")
        this.status = ProductStatus.ACTIVE
    }

    // any except DISCONTINUED → DISCONTINUED (terminal)
    fun discontinue() {
        if (status == ProductStatus.DISCONTINUED)
            throw CoreException(ErrorType.CONFLICT, "이미 판매종료된 상품입니다.")
        this.status = ProductStatus.DISCONTINUED
    }

    fun requireOrderable() {
        if (status != ProductStatus.ACTIVE)
            throw CoreException(ErrorType.BAD_REQUEST, "주문할 수 없는 상품입니다. 현재 상태: $status")
    }
}
