package com.loopers.infrastructure.coupon

import com.loopers.domain.BaseEntity
import com.loopers.domain.coupon.CouponTemplate
import com.loopers.domain.coupon.CouponType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "coupon_templates")
class CouponTemplateEntity(
    name: String,
    type: CouponType,
    discountValue: Int,
    minOrderAmount: Int,
    maxIssuance: Int?,
    issuedCount: Int = 0,
    expiresAt: LocalDate,
) : BaseEntity() {

    @Column(nullable = false)
    var name: String = name
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: CouponType = type
        protected set

    @Column(name = "discount_value", nullable = false)
    var discountValue: Int = discountValue
        protected set

    @Column(name = "min_order_amount", nullable = false)
    var minOrderAmount: Int = minOrderAmount
        protected set

    @Column(name = "max_issuance")
    var maxIssuance: Int? = maxIssuance
        protected set

    @Column(name = "issued_count", nullable = false)
    var issuedCount: Int = issuedCount
        protected set

    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDate = expiresAt
        protected set

    fun updateIssuedCount(count: Int) {
        this.issuedCount = count
    }

    fun toDomain(): CouponTemplate = CouponTemplate(
        id = this.id,
        name = this.name,
        type = this.type,
        discountValue = this.discountValue,
        minOrderAmount = this.minOrderAmount,
        maxIssuance = this.maxIssuance,
        issuedCount = this.issuedCount,
        expiresAt = this.expiresAt,
    )

    companion object {
        fun from(template: CouponTemplate): CouponTemplateEntity = CouponTemplateEntity(
            name = template.name,
            type = template.type,
            discountValue = template.discountValue,
            minOrderAmount = template.minOrderAmount,
            maxIssuance = template.maxIssuance,
            issuedCount = template.issuedCount,
            expiresAt = template.expiresAt,
        )
    }
}
