package com.loopers.domain.catalog.product

data class ProductSearchCondition(
    val brandId: Long? = null,
    val sort: ProductSort = ProductSort.LATEST,
    val page: Int = 0,
    val size: Int = 20,
)
