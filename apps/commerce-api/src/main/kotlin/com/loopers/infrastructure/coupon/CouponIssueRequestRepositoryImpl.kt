package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponIssueRequest
import com.loopers.domain.coupon.CouponIssueRequestRepository
import org.springframework.stereotype.Repository

@Repository
class CouponIssueRequestRepositoryImpl(
    private val couponIssueRequestJpaRepository: CouponIssueRequestJpaRepository,
) : CouponIssueRequestRepository {

    override fun save(request: CouponIssueRequest): CouponIssueRequest {
        val entity = if (request.id > 0L) {
            couponIssueRequestJpaRepository.getReferenceById(request.id).apply {
                updateStatus(request.status, request.failReason)
            }
        } else {
            CouponIssueRequestEntity.from(request)
        }
        return couponIssueRequestJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): CouponIssueRequest? =
        couponIssueRequestJpaRepository.findById(id)
            .map { it.toDomain() }
            .orElse(null)

    override fun findByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): CouponIssueRequest? =
        couponIssueRequestJpaRepository.findByUserIdAndCouponTemplateId(userId, couponTemplateId)
            ?.toDomain()
}
