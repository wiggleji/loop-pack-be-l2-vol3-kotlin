package com.loopers.domain.catalog.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BrandServiceUnitTest {

    private val mockRepository = mockk<BrandRepository>()
    private val brandService = BrandService(mockRepository)

    // ─── createBrand ───

    @Test
    fun `createBrand() should create brand with valid data`() {
        // Arrange
        every { mockRepository.save(any()) } returns createBrand(name = "Nike")

        // Act
        val brand = brandService.createBrand(name = "Nike", description = "Just Do It")

        // Assert
        assertThat(brand.name).isEqualTo("Nike")
        verify { mockRepository.save(any()) }
    }

    @Test
    fun `createBrand() throws CoreException(BAD_REQUEST) when name is blank`() {
        // Act
        val exception = assertThrows<CoreException> {
            brandService.createBrand(name = "  ", description = "desc")
        }

        // Assert
        verify(exactly = 0) { mockRepository.save(any()) }
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    // ─── getById ───

    @Test
    fun `getById() throws CoreException(NOT_FOUND) when brand does not exist`() {
        // Arrange
        every { mockRepository.findById(99L) } returns null

        // Act & Assert
        assertThrows<CoreException> {
            brandService.getById(99L)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @Test
    fun `getById() returns brand when it exists`() {
        // Arrange
        val brand = createBrand(id = 1L, name = "Nike")
        every { mockRepository.findById(1L) } returns brand

        // Act
        val result = brandService.getById(1L)

        // Assert
        assertThat(result.id).isEqualTo(1L)
        assertThat(result.name).isEqualTo("Nike")
    }

    // ─── update ───

    @Test
    fun `update() should update brand with valid data`() {
        // Arrange
        val brand = createBrand(id = 1L, name = "Nike")
        every { mockRepository.findById(1L) } returns brand
        every { mockRepository.save(any()) } returns createBrand(id = 1L, name = "Adidas")

        // Act
        val updated = brandService.update(1L, name = "Adidas", description = "Impossible is Nothing")

        // Assert
        assertThat(updated.name).isEqualTo("Adidas")
    }

    @Test
    fun `update() throws CoreException(BAD_REQUEST) when name is blank`() {
        // Arrange
        val brand = createBrand(id = 1L, name = "Nike")
        every { mockRepository.findById(1L) } returns brand

        // Act & Assert
        assertThrows<CoreException> {
            brandService.update(1L, name = "  ", description = "desc")
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        verify(exactly = 0) { mockRepository.save(any()) }
    }

    @Test
    fun `update() throws CoreException(NOT_FOUND) when brand does not exist`() {
        // Arrange
        every { mockRepository.findById(99L) } returns null

        // Act & Assert
        assertThrows<CoreException> {
            brandService.update(99L, name = "Adidas", description = "desc")
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    // ─── delete ───

    @Test
    fun `delete() should call repository deleteById`() {
        // Arrange
        every { mockRepository.deleteById(1L) } returns Unit

        // Act
        brandService.delete(1L)

        // Assert
        verify { mockRepository.deleteById(1L) }
    }

    // ─── findAll ───

    @Test
    fun `findAll() should return paged list of brands`() {
        // Arrange
        val brands = listOf(createBrand(id = 1L, name = "Nike"), createBrand(id = 2L, name = "Adidas"))
        every { mockRepository.findAll(0, 20) } returns brands

        // Act
        val result = brandService.findAll(0, 20)

        // Assert
        assertThat(result).hasSize(2)
        assertThat(result.map { it.name }).containsExactly("Nike", "Adidas")
    }

    @Test
    fun `findAll() should return empty list when no brands exist`() {
        // Arrange
        every { mockRepository.findAll(0, 20) } returns emptyList()

        // Act
        val result = brandService.findAll(0, 20)

        // Assert
        assertThat(result).isEmpty()
    }

    private fun createBrand(
        id: Long = 0L,
        name: String = "TestBrand",
        description: String = "Test Description",
    ): Brand = Brand(id = id, name = name, description = description)
}
