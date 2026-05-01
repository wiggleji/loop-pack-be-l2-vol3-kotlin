package com.loopers.interfaces.api.catalog.product

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.security.AdminHeader
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Product Admin V1 API", description = "어드민 상품 관련 API입니다.")
interface ProductAdminV1ApiSpec {

    @Operation(summary = "상품 목록 조회", description = "조건에 맞는 상품 목록을 조회합니다.",
        parameters = [Parameter(name = AdminHeader.HEADER_LDAP, `in` = ParameterIn.HEADER, required = true)])
    fun getProducts(brandId: Long?, sort: String?, page: Int, size: Int): ApiResponse<List<ProductAdminV1Dto.ProductSummaryResponse>>

    @Operation(summary = "상품 상세 조회", description = "상품 ID로 상품 상세 정보를 조회합니다.",
        parameters = [Parameter(name = AdminHeader.HEADER_LDAP, `in` = ParameterIn.HEADER, required = true)])
    fun getProduct(productId: Long): ApiResponse<ProductAdminV1Dto.ProductDetailResponse>

    @Operation(summary = "상품 생성", description = "새로운 상품을 생성합니다.",
        parameters = [Parameter(name = AdminHeader.HEADER_LDAP, `in` = ParameterIn.HEADER, required = true)])
    fun createProduct(request: ProductAdminV1Dto.CreateProductRequest): ApiResponse<ProductAdminV1Dto.ProductDetailResponse>

    @Operation(summary = "상품 수정", description = "상품 정보를 수정합니다. brandId는 변경 불가합니다.",
        parameters = [Parameter(name = AdminHeader.HEADER_LDAP, `in` = ParameterIn.HEADER, required = true)])
    fun updateProduct(productId: Long, request: ProductAdminV1Dto.UpdateProductRequest): ApiResponse<ProductAdminV1Dto.ProductDetailResponse>

    @Operation(summary = "상품 삭제", description = "상품을 삭제합니다.",
        parameters = [Parameter(name = AdminHeader.HEADER_LDAP, `in` = ParameterIn.HEADER, required = true)])
    fun deleteProduct(productId: Long): ApiResponse<Any>
}
