package com.loopers.domain.catalog.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

/**
 * 브랜드 도메인 모델 (JPA 비의존)
 *
 * @property id 식별자 (영속화 전에는 0L)
 * @property name 브랜드명
 * @property description 브랜드 설명
 * @property status 브랜드 상태
 */
class Brand(
    name: String,
    description: String,
    status: BrandStatus = BrandStatus.ACTIVE,
    val id: Long = 0L,
) {
    var name: String = name
        private set

    var description: String = description
        private set

    var status: BrandStatus = status
        private set

    init {
        if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.")
    }

    fun update(name: String, description: String) {
        if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.")
        this.name = name
        this.description = description
    }

    // INACTIVE or SUSPENDED → ACTIVE
    fun activate() {
        if (status == BrandStatus.ACTIVE)
            throw CoreException(ErrorType.CONFLICT, "이미 운영중인 브랜드입니다.")
        this.status = BrandStatus.ACTIVE
    }

    // ACTIVE → INACTIVE
    fun deactivate() {
        if (status != BrandStatus.ACTIVE)
            throw CoreException(ErrorType.BAD_REQUEST, "운영중인 브랜드만 일시중단할 수 있습니다.")
        this.status = BrandStatus.INACTIVE
    }

    // ACTIVE or INACTIVE → SUSPENDED
    fun suspend() {
        if (status == BrandStatus.SUSPENDED)
            throw CoreException(ErrorType.CONFLICT, "이미 제재된 브랜드입니다.")
        this.status = BrandStatus.SUSPENDED
    }
}
