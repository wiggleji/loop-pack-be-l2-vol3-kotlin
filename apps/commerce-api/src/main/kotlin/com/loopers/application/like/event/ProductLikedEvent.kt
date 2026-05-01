package com.loopers.application.like.event

data class ProductLikedEvent(
    val userId: Long,
    val productId: Long,
)

data class ProductUnlikedEvent(
    val userId: Long,
    val productId: Long,
)
