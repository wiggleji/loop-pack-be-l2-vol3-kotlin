package com.loopers.domain.coupon

class CouponIssueRequest(
    val userId: Long,
    val couponTemplateId: Long,
    status: CouponIssueStatus = CouponIssueStatus.REQUESTED,
    failReason: String? = null,
    val id: Long = 0L,
) {
    var status: CouponIssueStatus = status
        private set

    var failReason: String? = failReason
        private set

    fun markIssued() {
        this.status = CouponIssueStatus.ISSUED
    }

    fun markFailed(reason: String) {
        this.status = CouponIssueStatus.FAILED
        this.failReason = reason
    }
}

enum class CouponIssueStatus {
    REQUESTED,
    ISSUED,
    FAILED,
}
