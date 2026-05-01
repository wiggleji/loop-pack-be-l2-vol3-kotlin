package com.loopers.domain.coupon

interface CouponTemplateRepository {
    fun save(template: CouponTemplate): CouponTemplate
    fun findById(id: Long): CouponTemplate?
    fun findByIdWithLock(id: Long): CouponTemplate?
    fun findAllByIds(ids: List<Long>): List<CouponTemplate>
    fun findAll(): List<CouponTemplate>
    fun deleteById(id: Long)
}
