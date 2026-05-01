package com.loopers.application.catalog.brand

import com.loopers.domain.catalog.brand.Brand
import com.loopers.domain.catalog.brand.BrandService
import com.loopers.domain.catalog.product.ProductService
import com.loopers.infrastructure.catalog.brand.BrandCacheService
import com.loopers.infrastructure.catalog.product.ProductCacheService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BrandFacadeUnitTest {

    private val mockBrandService = mockk<BrandService>()
    private val mockProductService = mockk<ProductService>()
    private val mockProductCacheService = mockk<ProductCacheService>(relaxed = true)
    private val mockBrandCacheService = mockk<BrandCacheService>(relaxed = true)

    private val brandFacade = BrandFacade(
        mockBrandService,
        mockProductService,
        mockProductCacheService,
        mockBrandCacheService,
    )

    // ─── getBrand ───

    @Nested
    inner class GetBrand {

        @Test
        fun `getBrand() should return cached brand when cache hit`() {
            // Arrange
            val cached = BrandResult(id = 1L, name = "Nike", description = "Just Do It")
            every { mockBrandCacheService.getBrand(1L) } returns cached

            // Act
            val result = brandFacade.getBrand(1L)

            // Assert
            assertThat(result).isEqualTo(cached)
            verify(exactly = 0) { mockBrandService.getById(any()) }
        }

        @Test
        fun `getBrand() should load from DB and cache when cache miss`() {
            // Arrange
            val brand = createBrand(id = 1L)
            every { mockBrandCacheService.getBrand(1L) } returns null
            every { mockBrandService.getById(1L) } returns brand

            // Act
            val result = brandFacade.getBrand(1L)

            // Assert
            assertThat(result.id).isEqualTo(1L)
            verify { mockBrandCacheService.putBrand(1L, any()) }
        }
    }

    // ─── getBrands ───

    @Nested
    inner class GetBrands {

        @Test
        fun `getBrands() should return cached list when cache hit`() {
            // Arrange
            val cached = listOf(BrandResult(id = 1L, name = "Nike", description = "desc"))
            every { mockBrandCacheService.getBrandList(0, 20) } returns cached

            // Act
            val result = brandFacade.getBrands(0, 20)

            // Assert
            assertThat(result).isEqualTo(cached)
            verify(exactly = 0) { mockBrandService.findAll(any(), any()) }
        }

        @Test
        fun `getBrands() should load from DB and cache when cache miss`() {
            // Arrange
            val brands = listOf(createBrand(id = 1L))
            every { mockBrandCacheService.getBrandList(0, 20) } returns null
            every { mockBrandService.findAll(0, 20) } returns brands

            // Act
            val result = brandFacade.getBrands(0, 20)

            // Assert
            assertThat(result).hasSize(1)
            verify { mockBrandCacheService.putBrandList(0, 20, any()) }
        }
    }

    // ─── createBrand ───

    @Nested
    inner class CreateBrand {

        @Test
        fun `createBrand() should evict brand list cache`() {
            // Arrange
            val brand = createBrand(id = 1L, name = "New Brand")
            every { mockBrandService.createBrand("New Brand", "desc") } returns brand

            // Act
            brandFacade.createBrand("New Brand", "desc")

            // Assert
            verify { mockBrandCacheService.evictAllBrandLists() }
        }
    }

    // ─── updateBrand ───

    @Nested
    inner class UpdateBrand {

        @Test
        fun `updateBrand() should evict detail and list caches`() {
            // Arrange
            val brand = createBrand(id = 1L, name = "Updated")
            every { mockBrandService.update(1L, "Updated", "desc") } returns brand

            // Act
            brandFacade.updateBrand(1L, "Updated", "desc")

            // Assert
            verify { mockBrandCacheService.evictBrand(1L) }
            verify { mockBrandCacheService.evictAllBrandLists() }
        }
    }

    // ─── deleteBrand ───

    @Nested
    inner class DeleteBrand {

        @Test
        fun `deleteBrand() should delete products first then brand and evict caches`() {
            // Arrange
            val brand = createBrand(id = 1L)
            every { mockBrandService.getById(1L) } returns brand
            every { mockProductService.deleteAllByBrandId(1L) } returns Unit
            every { mockBrandService.delete(1L) } returns Unit

            // Act
            brandFacade.deleteBrand(1L)

            // Assert
            verifyOrder {
                mockBrandService.getById(1L)
                mockProductService.deleteAllByBrandId(1L)
                mockBrandService.delete(1L)
            }
            verify { mockBrandCacheService.evictBrand(1L) }
            verify { mockBrandCacheService.evictAllBrandLists() }
            verify { mockProductCacheService.evictAllProductLists() }
        }

        @Test
        fun `deleteBrand() throws NOT_FOUND when brand does not exist`() {
            // Arrange
            every { mockBrandService.getById(99L) } throws CoreException(ErrorType.NOT_FOUND, "브랜드가 존재하지 않습니다.")

            // Act & Assert
            assertThrows<CoreException> {
                brandFacade.deleteBrand(99L)
            }.also {
                assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
            }

            verify(exactly = 0) { mockProductService.deleteAllByBrandId(any()) }
            verify(exactly = 0) { mockBrandService.delete(any()) }
        }
    }

    private fun createBrand(
        id: Long = 0L,
        name: String = "TestBrand",
        description: String = "Test Description",
    ): Brand = Brand(id = id, name = name, description = description)
}
