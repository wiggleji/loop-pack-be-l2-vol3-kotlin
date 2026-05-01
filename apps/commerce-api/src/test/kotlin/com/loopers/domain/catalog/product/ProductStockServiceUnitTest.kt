package com.loopers.domain.catalog.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProductStockServiceUnitTest {

    private val mockRepository = mockk<ProductStockRepository>()
    private val productStockService = ProductStockService(mockRepository)

    // ─── getByProductId ───

    @Test
    fun `getByProductId() should return stock when it exists`() {
        // Arrange
        val stock = ProductStock(productId = 1L, quantity = 10, id = 1L)
        every { mockRepository.findByProductId(1L) } returns stock

        // Act
        val result = productStockService.getByProductId(1L)

        // Assert
        assertThat(result.productId).isEqualTo(1L)
        assertThat(result.quantity).isEqualTo(10)
    }

    @Test
    fun `getByProductId() throws CoreException(NOT_FOUND) when stock does not exist`() {
        // Arrange
        every { mockRepository.findByProductId(99L) } returns null

        // Act & Assert
        assertThrows<CoreException> {
            productStockService.getByProductId(99L)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    // ─── createStock ───

    @Test
    fun `createStock() should create and save stock`() {
        // Arrange
        every { mockRepository.save(any()) } answers { firstArg() }

        // Act
        val result = productStockService.createStock(1L, 100)

        // Assert
        assertThat(result.productId).isEqualTo(1L)
        assertThat(result.quantity).isEqualTo(100)
        verify { mockRepository.save(any()) }
    }

    // ─── decrementStock ───

    @Test
    fun `decrementStock() should decrement and save`() {
        // Arrange
        val stock = ProductStock(productId = 1L, quantity = 10, id = 1L)
        every { mockRepository.findByProductIdForUpdate(1L) } returns stock
        every { mockRepository.save(any()) } answers { firstArg() }

        // Act
        val result = productStockService.decrementStock(1L, 3)

        // Assert
        assertThat(result.quantity).isEqualTo(7)
        verify { mockRepository.save(any()) }
    }

    @Test
    fun `decrementStock() throws CoreException(BAD_REQUEST) when quantity exceeds stock`() {
        // Arrange
        val stock = ProductStock(productId = 1L, quantity = 2, id = 1L)
        every { mockRepository.findByProductIdForUpdate(1L) } returns stock

        // Act & Assert
        assertThrows<CoreException> {
            productStockService.decrementStock(1L, 5)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
        verify(exactly = 0) { mockRepository.save(any()) }
    }

    @Test
    fun `decrementStock() throws CoreException(NOT_FOUND) when stock does not exist`() {
        // Arrange
        every { mockRepository.findByProductIdForUpdate(99L) } returns null

        // Act & Assert
        assertThrows<CoreException> {
            productStockService.decrementStock(99L, 1)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    // ─── updateStock ───

    @Test
    fun `updateStock() should update and save`() {
        // Arrange
        val stock = ProductStock(productId = 1L, quantity = 10, id = 1L)
        every { mockRepository.findByProductId(1L) } returns stock
        every { mockRepository.save(any()) } answers { firstArg() }

        // Act
        val result = productStockService.updateStock(1L, 50)

        // Assert
        assertThat(result.quantity).isEqualTo(50)
        verify { mockRepository.save(any()) }
    }

    @Test
    fun `updateStock() throws CoreException(NOT_FOUND) when stock does not exist`() {
        // Arrange
        every { mockRepository.findByProductId(99L) } returns null

        // Act & Assert
        assertThrows<CoreException> {
            productStockService.updateStock(99L, 50)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
