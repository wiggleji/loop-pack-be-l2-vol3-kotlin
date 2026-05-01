package com.loopers.domain.catalog.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProductServiceUnitTest {

    private val mockRepository = mockk<ProductRepository>()
    private val productService = ProductService(mockRepository)

    // ─── createProduct ───

    @Test
    fun `createProduct() should create product with valid data`() {
        // Arrange
        every { mockRepository.save(any()) } returns createProduct(name = "Shoes")

        // Act
        val product = productService.createProduct(
            brandId = 1L,
            name = "Shoes",
            description = "Running shoes",
            price = 50000,
        )

        // Assert
        assertThat(product.name).isEqualTo("Shoes")
        verify { mockRepository.save(any()) }
    }

    // ─── getById ───

    @Test
    fun `getById() throws CoreException(NOT_FOUND) when product does not exist`() {
        // Arrange
        every { mockRepository.findById(99L) } returns null

        // Act & Assert
        assertThrows<CoreException> {
            productService.getById(99L)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @Test
    fun `getById() returns product when it exists`() {
        // Arrange
        val product = createProduct(id = 1L, name = "Shoes")
        every { mockRepository.findById(1L) } returns product

        // Act
        val result = productService.getById(1L)

        // Assert
        assertThat(result.id).isEqualTo(1L)
        assertThat(result.name).isEqualTo("Shoes")
    }

    // ─── incrementLikeCount ───

    @Test
    fun `incrementLikeCount() should call incrementLikeCountAtomic`() {
        // Arrange
        every { mockRepository.incrementLikeCountAtomic(1L) } returns true

        // Act
        productService.incrementLikeCount(1L)

        // Assert
        verify { mockRepository.incrementLikeCountAtomic(1L) }
    }

    @Test
    fun `incrementLikeCount() throws NOT_FOUND when product does not exist`() {
        // Arrange
        every { mockRepository.incrementLikeCountAtomic(99L) } returns false

        // Act & Assert
        assertThrows<CoreException> {
            productService.incrementLikeCount(99L)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    // ─── decrementLikeCount ───

    @Test
    fun `decrementLikeCount() should call decrementLikeCountAtomic`() {
        // Arrange
        every { mockRepository.decrementLikeCountAtomic(1L) } returns true

        // Act
        productService.decrementLikeCount(1L)

        // Assert
        verify { mockRepository.decrementLikeCountAtomic(1L) }
    }

    @Test
    fun `decrementLikeCount() throws NOT_FOUND when product does not exist`() {
        // Arrange
        every { mockRepository.decrementLikeCountAtomic(99L) } returns false

        // Act & Assert
        assertThrows<CoreException> {
            productService.decrementLikeCount(99L)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    // ─── updateStockStatus ───

    @Test
    fun `updateStockStatus() should mark product as SOLD_OUT when stock is 0 and status is ACTIVE`() {
        // Arrange
        val product = createProduct(id = 1L, status = ProductStatus.ACTIVE)
        every { mockRepository.findById(1L) } returns product
        every { mockRepository.save(any()) } answers { firstArg() }

        // Act
        productService.updateStockStatus(1L, 0)

        // Assert
        assertThat(product.status).isEqualTo(ProductStatus.SOLD_OUT)
        verify { mockRepository.save(any()) }
    }

    @Test
    fun `updateStockStatus() should restock product when stock is positive and status is SOLD_OUT`() {
        // Arrange
        val product = createProduct(id = 1L, status = ProductStatus.SOLD_OUT)
        every { mockRepository.findById(1L) } returns product
        every { mockRepository.save(any()) } answers { firstArg() }

        // Act
        productService.updateStockStatus(1L, 10)

        // Assert
        assertThat(product.status).isEqualTo(ProductStatus.ACTIVE)
        verify { mockRepository.save(any()) }
    }

    @Test
    fun `updateStockStatus() should not change status when stock is positive and status is ACTIVE`() {
        // Arrange
        val product = createProduct(id = 1L, status = ProductStatus.ACTIVE)
        every { mockRepository.findById(1L) } returns product

        // Act
        productService.updateStockStatus(1L, 10)

        // Assert
        assertThat(product.status).isEqualTo(ProductStatus.ACTIVE)
        verify(exactly = 0) { mockRepository.save(any()) }
    }

    // ─── delete ───

    @Test
    fun `delete() should call repository deleteById`() {
        // Arrange
        every { mockRepository.deleteById(1L) } returns Unit

        // Act
        productService.delete(1L)

        // Assert
        verify { mockRepository.deleteById(1L) }
    }

    // ─── deleteAllByBrandId ───

    @Test
    fun `deleteAllByBrandId() should call repository deleteAllByBrandId`() {
        // Arrange
        every { mockRepository.deleteAllByBrandId(1L) } returns Unit

        // Act
        productService.deleteAllByBrandId(1L)

        // Assert
        verify { mockRepository.deleteAllByBrandId(1L) }
    }

    // ─── findAll ───

    @Test
    fun `findAll() should return products matching condition`() {
        // Arrange
        val products = listOf(createProduct(id = 1L), createProduct(id = 2L))
        every { mockRepository.findAll(any()) } returns products

        // Act
        val result = productService.findAll(ProductSearchCondition())

        // Assert
        assertThat(result).hasSize(2)
    }

    @Test
    fun `findAll() should return empty list when no products match`() {
        // Arrange
        every { mockRepository.findAll(any()) } returns emptyList()

        // Act
        val result = productService.findAll(ProductSearchCondition())

        // Assert
        assertThat(result).isEmpty()
    }

    private fun createProduct(
        id: Long = 0L,
        brandId: Long = 1L,
        name: String = "Test Product",
        description: String = "Test Description",
        price: Int = 10000,
        likeCount: Int = 0,
        status: ProductStatus = ProductStatus.ACTIVE,
    ): Product = Product(
        id = id,
        brandId = brandId,
        name = name,
        description = description,
        price = price,
        likeCount = likeCount,
        status = status,
    )
}
