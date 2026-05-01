package com.loopers.interfaces.api.catalog.product

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Product V1 API", description = "상품 관련 API입니다.")
interface ProductV1ApiSpec {

    @Operation(summary = "상품 목록 조회", description = "조건에 맞는 상품 목록을 조회합니다.")
    fun getProducts(
        brandId: Long?,
        sort: String?,
        page: Int,
        size: Int,
    ): ApiResponse<List<ProductV1Dto.ProductSummaryResponse>>

    @Operation(summary = "상품 상세 조회", description = "상품 ID로 상품 상세 정보를 조회합니다.")
    fun getProduct(productId: Long): ApiResponse<ProductV1Dto.ProductDetailResponse>
}
