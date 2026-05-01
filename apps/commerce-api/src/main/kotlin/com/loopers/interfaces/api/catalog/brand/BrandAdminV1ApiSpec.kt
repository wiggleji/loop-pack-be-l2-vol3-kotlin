package com.loopers.interfaces.api.catalog.brand

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.security.AdminHeader
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Brand Admin V1 API", description = "어드민 브랜드 관련 API입니다.")
interface BrandAdminV1ApiSpec {

    @Operation(summary = "브랜드 목록 조회", description = "브랜드 목록을 페이지네이션으로 조회합니다.",
        parameters = [Parameter(name = AdminHeader.HEADER_LDAP, `in` = ParameterIn.HEADER, required = true)])
    fun getBrands(page: Int, size: Int): ApiResponse<List<BrandAdminV1Dto.BrandResponse>>

    @Operation(summary = "브랜드 조회", description = "브랜드 ID로 브랜드를 조회합니다.",
        parameters = [Parameter(name = AdminHeader.HEADER_LDAP, `in` = ParameterIn.HEADER, required = true)])
    fun getBrand(brandId: Long): ApiResponse<BrandAdminV1Dto.BrandResponse>

    @Operation(summary = "브랜드 생성", description = "새로운 브랜드를 생성합니다.",
        parameters = [Parameter(name = AdminHeader.HEADER_LDAP, `in` = ParameterIn.HEADER, required = true)])
    fun createBrand(request: BrandAdminV1Dto.CreateBrandRequest): ApiResponse<BrandAdminV1Dto.BrandResponse>

    @Operation(summary = "브랜드 수정", description = "브랜드 정보를 수정합니다.",
        parameters = [Parameter(name = AdminHeader.HEADER_LDAP, `in` = ParameterIn.HEADER, required = true)])
    fun updateBrand(brandId: Long, request: BrandAdminV1Dto.UpdateBrandRequest): ApiResponse<BrandAdminV1Dto.BrandResponse>

    @Operation(summary = "브랜드 삭제", description = "브랜드와 연관된 상품을 모두 삭제합니다.",
        parameters = [Parameter(name = AdminHeader.HEADER_LDAP, `in` = ParameterIn.HEADER, required = true)])
    fun deleteBrand(brandId: Long): ApiResponse<Any>
}
