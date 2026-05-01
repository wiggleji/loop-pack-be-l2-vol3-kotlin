package com.loopers.interfaces.api.catalog.brand

import com.loopers.application.catalog.brand.BrandFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/brands")
class BrandV1Controller(
    private val brandFacade: BrandFacade,
) : BrandV1ApiSpec {

    @GetMapping("/{brandId}")
    override fun getBrand(@PathVariable brandId: Long): ApiResponse<BrandV1Dto.BrandResponse> =
        brandFacade.getBrand(brandId)
            .let { BrandV1Dto.BrandResponse(id = it.id, name = it.name, description = it.description) }
            .let { ApiResponse.success(it) }
}
