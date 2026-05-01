package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponIssueRequestService(
    private val couponIssueRequestRepository: CouponIssueRequestRepository,
) {

    @Transactional
    fun create(userId: Long, couponTemplateId: Long): CouponIssueRequest =
        couponIssueRequestRepository.save(
            CouponIssueRequest(userId = userId, couponTemplateId = couponTemplateId),
        )

    @Transactional(readOnly = true)
    fun findById(id: Long): CouponIssueRequest? =
        couponIssueRequestRepository.findById(id)

    @Transactional(readOnly = true)
    fun findByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): CouponIssueRequest? =
        couponIssueRequestRepository.findByUserIdAndCouponTemplateId(userId, couponTemplateId)

    @Transactional(readOnly = true)
    fun getByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): CouponIssueRequest =
        couponIssueRequestRepository.findByUserIdAndCouponTemplateId(userId, couponTemplateId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "발급 요청이 존재하지 않습니다.")

    @Transactional
    fun save(request: CouponIssueRequest): CouponIssueRequest =
        couponIssueRequestRepository.save(request)
}
