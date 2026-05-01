package com.loopers.interfaces.api.like

import com.loopers.application.like.LikedProductResult

class LikeV1Dto {

    data class LikedProductResponse(
        val productId: Long,
        val productName: String,
        val price: Int,
        val likeCount: Int,
        val brandId: Long,
        val brandName: String,
    ) {
        companion object {
            fun from(result: LikedProductResult) = LikedProductResponse(
                productId = result.productId,
                productName = result.productName,
                price = result.price,
                likeCount = result.likeCount,
                brandId = result.brand.id,
                brandName = result.brand.name,
            )
        }
    }
}
