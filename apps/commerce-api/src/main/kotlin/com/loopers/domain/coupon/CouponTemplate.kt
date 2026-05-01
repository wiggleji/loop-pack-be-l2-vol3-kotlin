package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate

/**
 * 쿠폰 템플릿 도메인 모델 (JPA 비의존)
 *
 * @property id 식별자
 * @property name 쿠폰명
 * @property type 할인 유형 (FIXED / RATE)
 * @property discountValue 할인 값 (FIXED: 원, RATE: %)
 * @property minOrderAmount 최소 주문 금액
 * @property maxIssuance 최대 발급 수 (null = 무제한)
 * @property issuedCount 현재 발급 수
 * @property expiresAt 만료일
 */
class CouponTemplate(
    name: String,
    val type: CouponType,
    val discountValue: Int,
    val minOrderAmount: Int = 0,
    val maxIssuance: Int? = null,
    issuedCount: Int = 0,
    val expiresAt: LocalDate,
    val id: Long = 0L,
) {
    var name: String = name
        private set

    var issuedCount: Int = issuedCount
        private set

    init {
        if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.")
        if (discountValue <= 0) throw CoreException(ErrorType.BAD_REQUEST, "할인 값은 0보다 커야 합니다.")
        if (type == CouponType.RATE && discountValue > 100)
            throw CoreException(ErrorType.BAD_REQUEST, "정률 할인은 100%를 초과할 수 없습니다.")
        if (minOrderAmount < 0) throw CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 이상이어야 합니다.")
    }

    fun discount(totalPrice: Int): Int = when (type) {
        CouponType.FIXED -> discountValue.coerceAtMost(totalPrice)
        CouponType.RATE -> (totalPrice * discountValue / 100).coerceAtMost(totalPrice)
    }

    fun isExpired(today: LocalDate = LocalDate.now()): Boolean = today.isAfter(expiresAt)

    fun requireIssuable(today: LocalDate = LocalDate.now()) {
        if (isExpired(today))
            throw CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.")
        if (maxIssuance != null && issuedCount >= maxIssuance)
            throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 발급 수량이 초과되었습니다.")
    }

    fun incrementIssuedCount() {
        issuedCount++
    }
}
