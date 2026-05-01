package com.loopers.interfaces.api.coupon

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Coupon Admin V1 API", description = "쿠폰 관리자 API입니다.")
interface CouponAdminV1ApiSpec {

    @Operation(summary = "쿠폰 템플릿 생성", description = "새로운 쿠폰 템플릿을 생성합니다.")
    fun createTemplate(request: CouponV1Dto.CreateTemplateRequest): ApiResponse<CouponV1Dto.TemplateResponse>

    @Operation(summary = "쿠폰 템플릿 목록 조회", description = "모든 쿠폰 템플릿을 조회합니다.")
    fun getTemplates(): ApiResponse<List<CouponV1Dto.TemplateResponse>>

    @Operation(summary = "쿠폰 템플릿 상세 조회", description = "특정 쿠폰 템플릿을 조회합니다.")
    fun getTemplate(id: Long): ApiResponse<CouponV1Dto.TemplateResponse>

    @Operation(summary = "쿠폰 템플릿 삭제", description = "특정 쿠폰 템플릿을 삭제합니다.")
    fun deleteTemplate(id: Long): ApiResponse<Any>
}
