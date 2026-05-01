package com.loopers.infrastructure.coupon

import com.loopers.domain.BaseEntity
import com.loopers.domain.coupon.CouponIssueRequest
import com.loopers.domain.coupon.CouponIssueStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "coupon_issue_requests",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "coupon_template_id"])],
    indexes = [
        Index(name = "idx_coupon_issue_requests_user_template", columnList = "user_id, coupon_template_id"),
    ],
)
class CouponIssueRequestEntity(
    userId: Long,
    couponTemplateId: Long,
    status: CouponIssueStatus = CouponIssueStatus.REQUESTED,
    failReason: String? = null,
) : BaseEntity() {

    @Column(name = "user_id", nullable = false)
    val userId: Long = userId

    @Column(name = "coupon_template_id", nullable = false)
    val couponTemplateId: Long = couponTemplateId

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CouponIssueStatus = status
        protected set

    @Column(name = "fail_reason")
    var failReason: String? = failReason
        protected set

    fun updateStatus(status: CouponIssueStatus, failReason: String? = null) {
        this.status = status
        this.failReason = failReason
    }

    fun toDomain(): CouponIssueRequest = CouponIssueRequest(
        id = this.id,
        userId = this.userId,
        couponTemplateId = this.couponTemplateId,
        status = this.status,
        failReason = this.failReason,
    )

    companion object {
        fun from(request: CouponIssueRequest): CouponIssueRequestEntity = CouponIssueRequestEntity(
            userId = request.userId,
            couponTemplateId = request.couponTemplateId,
            status = request.status,
            failReason = request.failReason,
        )
    }
}
