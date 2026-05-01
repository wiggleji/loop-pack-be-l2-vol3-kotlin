package com.loopers.application.catalog.brand

import com.loopers.domain.catalog.brand.BrandService
import com.loopers.domain.catalog.product.ProductService
import com.loopers.infrastructure.catalog.brand.BrandCacheService
import com.loopers.infrastructure.catalog.product.ProductCacheService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BrandFacade(
    private val brandService: BrandService,
    private val productService: ProductService,
    private val productCacheService: ProductCacheService,
    private val brandCacheService: BrandCacheService,
) {

    fun getBrand(brandId: Long): BrandResult {
        brandCacheService.getBrand(brandId)?.let { return it }
        val brand = brandService.getById(brandId)
        val result = BrandResult.from(brand)
        brandCacheService.putBrand(brandId, result)
        return result
    }

    fun getBrands(page: Int, size: Int): List<BrandResult> {
        brandCacheService.getBrandList(page, size)?.let { return it }
        val brands = brandService.findAll(page, size)
        val results = brands.map { BrandResult.from(it) }
        brandCacheService.putBrandList(page, size, results)
        return results
    }

    @Transactional
    fun createBrand(name: String, description: String): BrandResult {
        val brand = brandService.createBrand(name, description)
        brandCacheService.evictAllBrandLists()
        return BrandResult.from(brand)
    }

    @Transactional
    fun updateBrand(brandId: Long, name: String, description: String): BrandResult {
        val brand = brandService.update(brandId, name, description)
        brandCacheService.evictBrand(brandId)
        brandCacheService.evictAllBrandLists()
        return BrandResult.from(brand)
    }

    /**
     * 브랜드 삭제
     * 브랜드 삭제 시 해당 브랜드에 속한 상품들도 함께 삭제되어야 합니다. (cascade delete)
     * @param brandId 삭제할 브랜드의 ID
     */
    @Transactional
    fun deleteBrand(brandId: Long) {
        brandService.getById(brandId)
        productService.deleteAllByBrandId(brandId)
        brandService.delete(brandId)
        brandCacheService.evictBrand(brandId)
        brandCacheService.evictAllBrandLists()
        productCacheService.evictAllProductLists()
    }
}
