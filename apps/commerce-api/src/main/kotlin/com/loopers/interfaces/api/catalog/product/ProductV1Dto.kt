package com.loopers.interfaces.api.catalog.product

import com.loopers.application.catalog.product.ProductDetailResult
import com.loopers.application.catalog.product.ProductSummaryResult

class ProductV1Dto {

    data class ProductDetailResponse(
        val id: Long,
        val name: String,
        val description: String,
        val price: Int,
        val stock: Int,
        val likeCount: Int,
        val brandId: Long,
        val brandName: String,
        /** 오늘 랭킹 0-based 순위. 진입하지 못했으면 null. */
        val rank: Long?,
    ) {
        companion object {
            fun from(result: ProductDetailResult) = ProductDetailResponse(
                id = result.id,
                name = result.name,
                description = result.description,
                price = result.price,
                stock = result.stock,
                likeCount = result.likeCount,
                brandId = result.brand.id,
                brandName = result.brand.name,
                rank = result.rank,
            )
        }
    }

    data class ProductSummaryResponse(
        val id: Long,
        val name: String,
        val price: Int,
        val likeCount: Int,
        val brandId: Long,
        val brandName: String,
    ) {
        companion object {
            fun from(result: ProductSummaryResult) = ProductSummaryResponse(
                id = result.id,
                name = result.name,
                price = result.price,
                likeCount = result.likeCount,
                brandId = result.brandId,
                brandName = result.brandName,
            )
        }
    }
}
