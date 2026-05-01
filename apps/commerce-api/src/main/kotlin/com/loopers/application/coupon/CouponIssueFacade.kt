package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponIssueRequest
import com.loopers.domain.coupon.CouponIssueRequestService
import com.loopers.domain.coupon.CouponIssueStatus
import com.loopers.domain.coupon.CouponTemplateService
import com.loopers.domain.coupon.UserCouponService
import com.loopers.infrastructure.outbox.KafkaTopics
import com.loopers.infrastructure.outbox.OutboxEventService
import com.loopers.support.error.CoreException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponIssueFacade(
    private val couponIssueRequestService: CouponIssueRequestService,
    private val couponTemplateService: CouponTemplateService,
    private val userCouponService: UserCouponService,
    private val outboxEventService: OutboxEventService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 쿠폰 발급 요청 (비동기)
     * CouponIssueRequest(REQUESTED) 저장 + Outbox 저장 → Kafka → Consumer가 실제 발급
     */
    @Transactional
    fun requestIssue(userId: Long, couponTemplateId: Long): CouponIssueRequestResult {
        // 멱등성: 이미 요청한 적 있으면 기존 결과 반환
        val existing = couponIssueRequestService.findByUserIdAndCouponTemplateId(userId, couponTemplateId)
        if (existing != null) {
            log.info("[CouponIssue] 이미 요청됨: userId=$userId, templateId=$couponTemplateId, status=${existing.status}")
            return CouponIssueRequestResult.from(existing)
        }

        // 템플릿 존재 확인 (빠른 실패)
        couponTemplateService.getById(couponTemplateId)

        // 발급 요청 저장
        val request = couponIssueRequestService.create(userId, couponTemplateId)

        // Outbox 저장 (같은 TX)
        outboxEventService.save(
            aggregateType = "COUPON",
            aggregateId = couponTemplateId.toString(),
            eventType = "COUPON_ISSUE_REQUESTED",
            payload = CouponIssueRequestedEvent(
                requestId = request.id,
                userId = userId,
                couponTemplateId = couponTemplateId,
            ),
            topic = KafkaTopics.COUPON_ISSUE_REQUESTS,
            partitionKey = couponTemplateId.toString(),
        )

        log.info("[CouponIssue] 발급 요청 접수: requestId=${request.id}, userId=$userId, templateId=$couponTemplateId")
        return CouponIssueRequestResult.from(request)
    }

    /**
     * 쿠폰 발급 처리 (Consumer에서 호출)
     * 비관적 락으로 동시성 제어 + 중복 발급 방지
     */
    @Transactional
    fun processIssue(requestId: Long, userId: Long, couponTemplateId: Long) {
        val request = couponIssueRequestService.findById(requestId)
        if (request == null) {
            log.warn("[CouponIssue] 요청을 찾을 수 없음: requestId=$requestId")
            return
        }

        // 멱등성: 이미 처리된 요청은 스킵
        if (request.status != CouponIssueStatus.REQUESTED) {
            log.info("[CouponIssue] 이미 처리된 요청: requestId=$requestId, status=${request.status}")
            return
        }

        try {
            userCouponService.issueWithLock(userId, couponTemplateId)

            request.markIssued()
            couponIssueRequestService.save(request)

            log.info("[CouponIssue] 발급 완료: requestId=$requestId, userId=$userId, templateId=$couponTemplateId")
        } catch (ex: CoreException) {
            request.markFailed(ex.message ?: "발급 실패")
            couponIssueRequestService.save(request)
            log.warn("[CouponIssue] 발급 실패: requestId=$requestId, reason=${ex.message}")
        }
    }

    /**
     * 발급 요청 상태 조회 (polling)
     */
    @Transactional(readOnly = true)
    fun getIssueStatus(userId: Long, couponTemplateId: Long): CouponIssueRequestResult =
        CouponIssueRequestResult.from(
            couponIssueRequestService.getByUserIdAndCouponTemplateId(userId, couponTemplateId),
        )
}

data class CouponIssueRequestedEvent(
    val requestId: Long,
    val userId: Long,
    val couponTemplateId: Long,
)

data class CouponIssueRequestResult(
    val id: Long,
    val userId: Long,
    val couponTemplateId: Long,
    val status: CouponIssueStatus,
    val failReason: String?,
) {
    companion object {
        fun from(request: CouponIssueRequest): CouponIssueRequestResult = CouponIssueRequestResult(
            id = request.id,
            userId = request.userId,
            couponTemplateId = request.couponTemplateId,
            status = request.status,
            failReason = request.failReason,
        )
    }
}
