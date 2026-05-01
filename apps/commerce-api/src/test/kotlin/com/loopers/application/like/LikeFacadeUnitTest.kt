package com.loopers.application.like

import com.loopers.domain.catalog.brand.Brand
import com.loopers.domain.catalog.brand.BrandRepository
import com.loopers.domain.catalog.product.Product
import com.loopers.domain.catalog.product.ProductRepository
import com.loopers.domain.catalog.product.ProductService
import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeService
import com.loopers.infrastructure.outbox.OutboxEventService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LikeFacadeUnitTest {

    private val mockLikeService = mockk<LikeService>()
    private val mockProductService = mockk<ProductService>()
    private val mockProductRepository = mockk<ProductRepository>()
    private val mockBrandRepository = mockk<BrandRepository>()
    private val mockOutboxEventService = mockk<OutboxEventService>(relaxed = true)

    private val likeFacade = LikeFacade(mockLikeService, mockProductService, mockProductRepository, mockBrandRepository, mockOutboxEventService)

    // ─── addLike ───

    @Test
    fun `addLike() should verify product exists, create like, and publish event`() {
        // Arrange
        val product = createProduct(id = 10L)
        every { mockProductService.getById(10L) } returns product
        every { mockLikeService.addLike(1L, 10L) } returns createLike(userId = 1L, productId = 10L)

        // Act
        likeFacade.addLike(userId = 1L, productId = 10L)

        // Assert
        verifyOrder {
            mockProductService.getById(10L)
            mockLikeService.addLike(1L, 10L)
        }
        verify { mockOutboxEventService.save(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `addLike() throws NOT_FOUND when product does not exist`() {
        // Arrange
        every { mockProductService.getById(99L) } throws CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다.")

        // Act & Assert
        assertThrows<CoreException> {
            likeFacade.addLike(userId = 1L, productId = 99L)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        verify(exactly = 0) { mockLikeService.addLike(any(), any()) }
        verify(exactly = 0) { mockOutboxEventService.save(any(), any(), any(), any(), any(), any()) }
    }

    // ─── removeLike ───

    @Test
    fun `removeLike() should delete like and publish event`() {
        // Arrange
        every { mockLikeService.removeLike(1L, 10L) } returns Unit

        // Act
        likeFacade.removeLike(userId = 1L, productId = 10L)

        // Assert
        verify { mockLikeService.removeLike(1L, 10L) }
        verify { mockOutboxEventService.save(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `removeLike() throws NOT_FOUND when like does not exist`() {
        // Arrange
        every { mockLikeService.removeLike(1L, 10L) } throws CoreException(ErrorType.NOT_FOUND, "좋아요 기록이 존재하지 않습니다.")

        // Act & Assert
        assertThrows<CoreException> {
            likeFacade.removeLike(userId = 1L, productId = 10L)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        verify(exactly = 0) { mockOutboxEventService.save(any(), any(), any(), any(), any(), any()) }
    }

    // ─── getLikedProducts ───

    @Test
    fun `getLikedProducts() should batch fetch products and brands instead of N+1`() {
        // Arrange
        val likes = listOf(createLike(userId = 1L, productId = 10L), createLike(userId = 1L, productId = 20L))
        val product1 = createProduct(id = 10L, brandId = 1L)
        val product2 = createProduct(id = 20L, brandId = 2L)
        val brand1 = createBrand(id = 1L, name = "Nike")
        val brand2 = createBrand(id = 2L, name = "Adidas")
        every { mockLikeService.getLikedByUser(1L) } returns likes
        every { mockProductRepository.findAllByIds(listOf(10L, 20L)) } returns listOf(product1, product2)
        every { mockBrandRepository.findAllByIds(listOf(1L, 2L)) } returns listOf(brand1, brand2)

        // Act
        val result = likeFacade.getLikedProducts(userId = 1L)

        // Assert
        assertThat(result).hasSize(2)
        assertThat(result[0].productId).isEqualTo(10L)
        assertThat(result[0].brand.name).isEqualTo("Nike")
        assertThat(result[1].productId).isEqualTo(20L)
        assertThat(result[1].brand.name).isEqualTo("Adidas")
        // Verify batch fetch, NOT individual getById calls
        verify(exactly = 1) { mockProductRepository.findAllByIds(any()) }
        verify(exactly = 1) { mockBrandRepository.findAllByIds(any()) }
        verify(exactly = 0) { mockProductService.getById(any()) }
    }

    private fun createLike(id: Long = 0L, userId: Long = 1L, productId: Long = 10L): Like =
        Like(id = id, userId = userId, productId = productId)

    private fun createProduct(
        id: Long = 0L,
        brandId: Long = 1L,
        name: String = "Test Product",
        likeCount: Int = 0,
    ): Product = Product(id = id, brandId = brandId, name = name, description = "desc", price = 10000, likeCount = likeCount)

    private fun createBrand(id: Long = 0L, name: String = "TestBrand"): Brand =
        Brand(id = id, name = name, description = "desc")
}
