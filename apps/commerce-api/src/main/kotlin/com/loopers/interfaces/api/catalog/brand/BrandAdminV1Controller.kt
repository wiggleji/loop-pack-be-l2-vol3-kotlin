package com.loopers.interfaces.api.catalog.brand

import com.loopers.application.catalog.brand.BrandFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/brands")
class BrandAdminV1Controller(
    private val brandFacade: BrandFacade,
) : BrandAdminV1ApiSpec {

    @GetMapping
    override fun getBrands(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<List<BrandAdminV1Dto.BrandResponse>> =
        brandFacade.getBrands(page, size)
            .map { BrandAdminV1Dto.BrandResponse(id = it.id, name = it.name, description = it.description) }
            .let { ApiResponse.success(it) }

    @GetMapping("/{brandId}")
    override fun getBrand(@PathVariable brandId: Long): ApiResponse<BrandAdminV1Dto.BrandResponse> =
        brandFacade.getBrand(brandId)
            .let { BrandAdminV1Dto.BrandResponse(id = it.id, name = it.name, description = it.description) }
            .let { ApiResponse.success(it) }

    @PostMapping
    override fun createBrand(
        @RequestBody request: BrandAdminV1Dto.CreateBrandRequest,
    ): ApiResponse<BrandAdminV1Dto.BrandResponse> =
        brandFacade.createBrand(name = request.name, description = request.description)
            .let { BrandAdminV1Dto.BrandResponse(id = it.id, name = it.name, description = it.description) }
            .let { ApiResponse.success(it) }

    @PutMapping("/{brandId}")
    override fun updateBrand(
        @PathVariable brandId: Long,
        @RequestBody request: BrandAdminV1Dto.UpdateBrandRequest,
    ): ApiResponse<BrandAdminV1Dto.BrandResponse> =
        brandFacade.updateBrand(brandId, name = request.name, description = request.description)
            .let { BrandAdminV1Dto.BrandResponse(id = it.id, name = it.name, description = it.description) }
            .let { ApiResponse.success(it) }

    @DeleteMapping("/{brandId}")
    override fun deleteBrand(@PathVariable brandId: Long): ApiResponse<Any> {
        brandFacade.deleteBrand(brandId)
        return ApiResponse.success()
    }
}
