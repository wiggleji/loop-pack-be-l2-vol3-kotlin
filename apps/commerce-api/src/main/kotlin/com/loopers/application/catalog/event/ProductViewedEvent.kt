package com.loopers.application.catalog.event

data class ProductViewedEvent(
    val userId: Long?,
    val productId: Long,
)
