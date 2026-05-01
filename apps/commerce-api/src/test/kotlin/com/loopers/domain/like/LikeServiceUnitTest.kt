package com.loopers.domain.like

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LikeServiceUnitTest {

    private val mockRepository = mockk<LikeRepository>()
    private val likeService = LikeService(mockRepository)

    // ─── addLike ───

    @Test
    fun `addLike() should save Like when not already liked`() {
        // Arrange
        every { mockRepository.existsByUserIdAndProductId(1L, 10L) } returns false
        every { mockRepository.save(any()) } returns createLike(userId = 1L, productId = 10L)

        // Act
        val result = likeService.addLike(userId = 1L, productId = 10L)

        // Assert
        assertThat(result.userId).isEqualTo(1L)
        assertThat(result.productId).isEqualTo(10L)
        verify { mockRepository.save(any()) }
    }

    @Test
    fun `addLike() throws CoreException(CONFLICT) when already liked`() {
        // Arrange
        every { mockRepository.existsByUserIdAndProductId(1L, 10L) } returns true

        // Act & Assert
        assertThrows<CoreException> {
            likeService.addLike(userId = 1L, productId = 10L)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.CONFLICT)
        }
        verify(exactly = 0) { mockRepository.save(any()) }
    }

    // ─── removeLike ───

    @Test
    fun `removeLike() should delete Like when it exists`() {
        // Arrange
        every { mockRepository.existsByUserIdAndProductId(1L, 10L) } returns true
        every { mockRepository.deleteByUserIdAndProductId(1L, 10L) } returns Unit

        // Act
        likeService.removeLike(userId = 1L, productId = 10L)

        // Assert
        verify { mockRepository.deleteByUserIdAndProductId(1L, 10L) }
    }

    @Test
    fun `removeLike() throws CoreException(NOT_FOUND) when Like does not exist`() {
        // Arrange
        every { mockRepository.existsByUserIdAndProductId(1L, 10L) } returns false

        // Act & Assert
        assertThrows<CoreException> {
            likeService.removeLike(userId = 1L, productId = 10L)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
        verify(exactly = 0) { mockRepository.deleteByUserIdAndProductId(any(), any()) }
    }

    // ─── getLikedByUser ───

    @Test
    fun `getLikedByUser() should return all likes for the user`() {
        // Arrange
        val likes = listOf(createLike(userId = 1L, productId = 10L), createLike(userId = 1L, productId = 20L))
        every { mockRepository.findAllByUserId(1L) } returns likes

        // Act
        val result = likeService.getLikedByUser(userId = 1L)

        // Assert
        assertThat(result).hasSize(2)
        assertThat(result.map { it.productId }).containsExactly(10L, 20L)
    }

    @Test
    fun `getLikedByUser() should return empty list when user has no likes`() {
        // Arrange
        every { mockRepository.findAllByUserId(1L) } returns emptyList()

        // Act
        val result = likeService.getLikedByUser(userId = 1L)

        // Assert
        assertThat(result).isEmpty()
    }

    private fun createLike(
        id: Long = 0L,
        userId: Long = 1L,
        productId: Long = 10L,
    ): Like = Like(id = id, userId = userId, productId = productId)
}
