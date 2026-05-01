package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponFacade
import com.loopers.application.coupon.CouponIssueFacade
import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.security.LoginUser
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/coupons")
class CouponV1Controller(
    private val couponFacade: CouponFacade,
    private val couponIssueFacade: CouponIssueFacade,
) : CouponV1ApiSpec {

    @PostMapping("/issue")
    @ResponseStatus(HttpStatus.ACCEPTED)
    override fun issueCoupon(
        @LoginUser user: User,
        @RequestBody request: CouponV1Dto.IssueCouponRequest,
    ): ApiResponse<CouponV1Dto.CouponIssueResponse> =
        couponIssueFacade.requestIssue(user.id, request.couponTemplateId)
            .let { CouponV1Dto.CouponIssueResponse.from(it) }
            .let { ApiResponse.success(it) }

    @GetMapping("/issue/{couponTemplateId}/status")
    override fun getIssueStatus(
        @LoginUser user: User,
        @PathVariable couponTemplateId: Long,
    ): ApiResponse<CouponV1Dto.CouponIssueResponse> =
        couponIssueFacade.getIssueStatus(user.id, couponTemplateId)
            .let { CouponV1Dto.CouponIssueResponse.from(it) }
            .let { ApiResponse.success(it) }

    @GetMapping("/me")
    override fun getUserCoupons(
        @LoginUser user: User,
    ): ApiResponse<List<CouponV1Dto.UserCouponResponse>> =
        couponFacade.getUserCoupons(user.id)
            .map { CouponV1Dto.UserCouponResponse.from(it) }
            .let { ApiResponse.success(it) }
}
