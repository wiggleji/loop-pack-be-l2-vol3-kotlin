package com.loopers.domain.like

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

/**
 * 좋아요 도메인 모델 (JPA 비의존)
 *
 * @property id 식별자 (영속화 전에는 0L)
 * @property userId 사용자 DB ID
 * @property productId 상품 ID
 */
class Like(
    val userId: Long,
    val productId: Long,
    val id: Long = 0L,
) {
    init {
        if (userId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 사용자 ID입니다.")
        if (productId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 상품 ID입니다.")
    }
}
