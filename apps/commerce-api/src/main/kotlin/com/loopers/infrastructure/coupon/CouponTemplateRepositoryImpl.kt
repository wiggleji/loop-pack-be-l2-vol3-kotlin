package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponTemplate
import com.loopers.domain.coupon.CouponTemplateRepository
import org.springframework.stereotype.Repository

@Repository
class CouponTemplateRepositoryImpl(
    private val couponTemplateJpaRepository: CouponTemplateJpaRepository,
) : CouponTemplateRepository {

    override fun save(template: CouponTemplate): CouponTemplate {
        val entity = if (template.id > 0L) {
            couponTemplateJpaRepository.getReferenceById(template.id).apply {
                updateIssuedCount(template.issuedCount)
            }
        } else {
            CouponTemplateEntity.from(template)
        }
        return couponTemplateJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): CouponTemplate? =
        couponTemplateJpaRepository.findById(id)
            .filter { it.deletedAt == null }
            .map { it.toDomain() }
            .orElse(null)

    override fun findByIdWithLock(id: Long): CouponTemplate? =
        couponTemplateJpaRepository.findByIdWithLock(id)?.toDomain()

    override fun findAllByIds(ids: List<Long>): List<CouponTemplate> =
        if (ids.isEmpty()) emptyList()
        else couponTemplateJpaRepository.findAllByIdIn(ids).map { it.toDomain() }

    override fun findAll(): List<CouponTemplate> =
        couponTemplateJpaRepository.findAllActive().map { it.toDomain() }

    override fun deleteById(id: Long) {
        couponTemplateJpaRepository.findById(id)
            .filter { it.deletedAt == null }
            .ifPresent { it.delete() }
    }
}
