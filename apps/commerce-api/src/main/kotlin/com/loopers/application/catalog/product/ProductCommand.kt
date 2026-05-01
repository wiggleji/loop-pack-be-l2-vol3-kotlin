package com.loopers.application.catalog.product

data class CreateProductCommand(
    val brandId: Long,
    val name: String,
    val description: String,
    val price: Int,
    val stock: Int,
)

data class UpdateProductCommand(
    val name: String,
    val description: String,
    val price: Int,
    val stock: Int,
)
