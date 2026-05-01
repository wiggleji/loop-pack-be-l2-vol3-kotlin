package com.loopers.domain.catalog.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProductUnitTest {

    // ─── incrementLike / decrementLike ───

    @Test
    fun `incrementLike() should increase likeCount by 1`() {
        // Arrange
        val product = createProduct(likeCount = 5)

        // Act
        product.incrementLike()

        // Assert
        assertThat(product.likeCount).isEqualTo(6)
    }

    @Test
    fun `decrementLike() should decrease likeCount by 1`() {
        // Arrange
        val product = createProduct(likeCount = 5)

        // Act
        product.decrementLike()

        // Assert
        assertThat(product.likeCount).isEqualTo(4)
    }

    @Test
    fun `decrementLike() throws CoreException(BAD_REQUEST) when likeCount is 0`() {
        // Arrange
        val product = createProduct(likeCount = 0)

        // Act & Assert
        assertThrows<CoreException> {
            product.decrementLike()
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    // ─── update ───

    @Test
    fun `update() should update name, description, and price`() {
        // Arrange
        val product = createProduct()

        // Act
        product.update("New Name", "New Desc", 20000)

        // Assert
        assertThat(product.name).isEqualTo("New Name")
        assertThat(product.description).isEqualTo("New Desc")
        assertThat(product.price).isEqualTo(20000)
    }

    @Test
    fun `update() throws CoreException(BAD_REQUEST) when name is blank`() {
        val product = createProduct()
        assertThrows<CoreException> {
            product.update("  ", "desc", 10000)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `update() throws CoreException(BAD_REQUEST) when price is negative`() {
        val product = createProduct()
        assertThrows<CoreException> {
            product.update("Name", "desc", -1)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    // ─── init validation ───

    @Test
    fun `Product init throws CoreException(BAD_REQUEST) when name is blank`() {
        assertThrows<CoreException> {
            createProduct(name = "  ")
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `Product init throws CoreException(BAD_REQUEST) when price is negative`() {
        assertThrows<CoreException> {
            createProduct(price = -1)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `Product init allows price of 0`() {
        // no exception
        val product = createProduct(price = 0)
        assertThat(product.price).isEqualTo(0)
    }

    private fun createProduct(
        id: Long = 0L,
        brandId: Long = 1L,
        name: String = "Test Product",
        description: String = "Test Description",
        price: Int = 10000,
        likeCount: Int = 0,
    ): Product = Product(
        id = id,
        brandId = brandId,
        name = name,
        description = description,
        price = price,
        likeCount = likeCount,
    )
}
