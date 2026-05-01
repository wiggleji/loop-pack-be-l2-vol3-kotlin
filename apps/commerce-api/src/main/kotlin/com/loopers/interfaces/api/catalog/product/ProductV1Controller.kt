package com.loopers.interfaces.api.catalog.product

import com.loopers.application.catalog.product.ProductFacade
import com.loopers.domain.catalog.product.ProductSearchCondition
import com.loopers.domain.catalog.product.ProductSort
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
class ProductV1Controller(
    private val productFacade: ProductFacade,
) : ProductV1ApiSpec {

    @GetMapping
    override fun getProducts(
        @RequestParam brandId: Long?,
        @RequestParam sort: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<List<ProductV1Dto.ProductSummaryResponse>> {
        val productSort = when (sort?.lowercase()) {
            "price_asc" -> ProductSort.PRICE_ASC
            "likes_desc" -> ProductSort.LIKES_DESC
            else -> ProductSort.LATEST
        }
        val condition = ProductSearchCondition(brandId = brandId, sort = productSort, page = page, size = size)
        return productFacade.findProducts(condition)
            .map { ProductV1Dto.ProductSummaryResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{productId}")
    override fun getProduct(@PathVariable productId: Long): ApiResponse<ProductV1Dto.ProductDetailResponse> =
        productFacade.getProductDetail(productId)
            .let { ProductV1Dto.ProductDetailResponse.from(it) }
            .let { ApiResponse.success(it) }
}
