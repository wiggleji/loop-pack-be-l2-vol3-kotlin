package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/coupons")
class CouponAdminV1Controller(
    private val couponFacade: CouponFacade,
) : CouponAdminV1ApiSpec {

    @PostMapping
    override fun createTemplate(
        @RequestBody request: CouponV1Dto.CreateTemplateRequest,
    ): ApiResponse<CouponV1Dto.TemplateResponse> =
        couponFacade.createTemplate(request.toCommand())
            .let { CouponV1Dto.TemplateResponse.from(it) }
            .let { ApiResponse.success(it) }

    @GetMapping
    override fun getTemplates(): ApiResponse<List<CouponV1Dto.TemplateResponse>> =
        couponFacade.getTemplates()
            .map { CouponV1Dto.TemplateResponse.from(it) }
            .let { ApiResponse.success(it) }

    @GetMapping("/{id}")
    override fun getTemplate(
        @PathVariable id: Long,
    ): ApiResponse<CouponV1Dto.TemplateResponse> =
        couponFacade.getTemplate(id)
            .let { CouponV1Dto.TemplateResponse.from(it) }
            .let { ApiResponse.success(it) }

    @DeleteMapping("/{id}")
    override fun deleteTemplate(
        @PathVariable id: Long,
    ): ApiResponse<Any> {
        couponFacade.deleteTemplate(id)
        return ApiResponse.success()
    }
}
