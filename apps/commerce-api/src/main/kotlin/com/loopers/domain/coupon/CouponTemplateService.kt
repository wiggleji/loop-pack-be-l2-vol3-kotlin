package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponTemplateService(
    private val couponTemplateRepository: CouponTemplateRepository,
) {

    @Transactional
    fun create(template: CouponTemplate): CouponTemplate =
        couponTemplateRepository.save(template)

    @Transactional(readOnly = true)
    fun getById(id: Long): CouponTemplate =
        couponTemplateRepository.findById(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[$id] 해당 ID에 해당하는 쿠폰 템플릿이 존재하지 않습니다.")

    @Transactional(readOnly = true)
    fun findAllByIds(ids: List<Long>): List<CouponTemplate> =
        couponTemplateRepository.findAllByIds(ids)

    @Transactional(readOnly = true)
    fun findAll(): List<CouponTemplate> =
        couponTemplateRepository.findAll()

    @Transactional
    fun delete(id: Long) {
        couponTemplateRepository.deleteById(id)
    }
}
