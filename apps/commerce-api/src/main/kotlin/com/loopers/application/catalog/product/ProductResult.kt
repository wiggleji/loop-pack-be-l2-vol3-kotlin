package com.loopers.application.catalog.product

import com.loopers.application.catalog.brand.BrandResult
import com.loopers.domain.catalog.product.Product

data class ProductDetailResult(
    val id: Long,
    val name: String,
    val description: String,
    val price: Int,
    val stock: Int,
    val likeCount: Int,
    val brand: BrandResult,
    /** 오늘 일간 랭킹에서의 0-based 순위. 랭킹에 진입하지 못했으면 null. */
    val rank: Long? = null,
)

data class ProductSummaryResult(
    val id: Long,
    val name: String,
    val price: Int,
    val likeCount: Int,
    val brandId: Long,
    val brandName: String,
) {
    companion object {
        fun from(product: Product, brandName: String): ProductSummaryResult = ProductSummaryResult(
            id = product.id,
            name = product.name,
            price = product.price,
            likeCount = product.likeCount,
            brandId = product.brandId,
            brandName = brandName,
        )
    }
}
