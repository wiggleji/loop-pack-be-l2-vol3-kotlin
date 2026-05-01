package com.loopers.interfaces.api.catalog.brand

import com.loopers.domain.catalog.brand.Brand

class BrandAdminV1Dto {

    data class CreateBrandRequest(
        val name: String,
        val description: String,
    )

    data class UpdateBrandRequest(
        val name: String,
        val description: String,
    )

    data class BrandResponse(
        val id: Long,
        val name: String,
        val description: String,
    ) {
        companion object {
            fun from(brand: Brand) = BrandResponse(
                id = brand.id,
                name = brand.name,
                description = brand.description,
            )
        }
    }
}
