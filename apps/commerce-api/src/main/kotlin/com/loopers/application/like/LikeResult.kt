package com.loopers.application.like

import com.loopers.application.catalog.brand.BrandResult
import com.loopers.domain.catalog.brand.Brand
import com.loopers.domain.catalog.product.Product

data class LikedProductResult(
    val productId: Long,
    val productName: String,
    val price: Int,
    val likeCount: Int,
    val brand: BrandResult,
) {
    companion object {
        fun from(product: Product, brand: Brand): LikedProductResult = LikedProductResult(
            productId = product.id,
            productName = product.name,
            price = product.price,
            likeCount = product.likeCount,
            brand = BrandResult.from(brand),
        )
    }
}
