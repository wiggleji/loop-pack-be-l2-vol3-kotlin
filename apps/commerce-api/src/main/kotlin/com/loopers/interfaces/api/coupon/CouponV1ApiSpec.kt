package com.loopers.interfaces.api.coupon

import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.security.AuthHeader
import com.loopers.interfaces.api.security.LoginUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Coupon V1 API", description = "쿠폰 사용자 API입니다.")
interface CouponV1ApiSpec {

    @Operation(
        summary = "선착순 쿠폰 발급 요청",
        description = "쿠폰 발급을 요청합니다. 비동기로 처리되며, 결과는 상태 조회 API로 확인합니다.",
        parameters = [
            Parameter(name = AuthHeader.HEADER_LOGIN_ID, `in` = ParameterIn.HEADER, required = true),
            Parameter(name = AuthHeader.HEADER_LOGIN_PW, `in` = ParameterIn.HEADER, required = true, hidden = true),
        ]
    )
    fun issueCoupon(@LoginUser user: User, request: CouponV1Dto.IssueCouponRequest): ApiResponse<CouponV1Dto.CouponIssueResponse>

    @Operation(
        summary = "쿠폰 발급 상태 조회",
        description = "쿠폰 발급 요청의 현재 상태를 조회합니다. (polling)",
        parameters = [
            Parameter(name = AuthHeader.HEADER_LOGIN_ID, `in` = ParameterIn.HEADER, required = true),
            Parameter(name = AuthHeader.HEADER_LOGIN_PW, `in` = ParameterIn.HEADER, required = true, hidden = true),
        ]
    )
    fun getIssueStatus(@LoginUser user: User, couponTemplateId: Long): ApiResponse<CouponV1Dto.CouponIssueResponse>

    @Operation(
        summary = "보유 쿠폰 목록 조회",
        description = "사용자가 보유한 쿠폰 목록을 조회합니다.",
        parameters = [
            Parameter(name = AuthHeader.HEADER_LOGIN_ID, `in` = ParameterIn.HEADER, required = true),
            Parameter(name = AuthHeader.HEADER_LOGIN_PW, `in` = ParameterIn.HEADER, required = true, hidden = true),
        ]
    )
    fun getUserCoupons(@LoginUser user: User): ApiResponse<List<CouponV1Dto.UserCouponResponse>>
}
