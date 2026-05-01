package com.loopers.application.catalog.brand

import com.loopers.domain.catalog.brand.Brand

data class BrandResult(
    val id: Long,
    val name: String,
    val description: String,
) {
    companion object {
        fun from(brand: Brand): BrandResult = BrandResult(
            id = brand.id,
            name = brand.name,
            description = brand.description,
        )
    }
}
