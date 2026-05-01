package com.loopers.infrastructure.like

import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeRepository
import org.springframework.stereotype.Repository

@Repository
class LikeRepositoryImpl(
    private val likeJpaRepository: LikeJpaRepository,
) : LikeRepository {

    override fun save(like: Like): Like {
        val entity = LikeEntity.from(like)
        return likeJpaRepository.save(entity).toDomain()
    }

    override fun findByUserIdAndProductId(userId: Long, productId: Long): Like? =
        likeJpaRepository.findByUserIdAndProductId(userId, productId)
            ?.takeIf { it.deletedAt == null }
            ?.toDomain()

    override fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean =
        likeJpaRepository.findByUserIdAndProductId(userId, productId)
            ?.let { it.deletedAt == null } ?: false

    override fun findAllByUserId(userId: Long): List<Like> =
        likeJpaRepository.findAllByUserId(userId).map { it.toDomain() }

    override fun deleteByUserIdAndProductId(userId: Long, productId: Long) {
        likeJpaRepository.findByUserIdAndProductId(userId, productId)
            ?.takeIf { it.deletedAt == null }
            ?.delete()
    }

    override fun deleteAllByProductId(productId: Long) {
        likeJpaRepository.findAllByProductId(productId).forEach { it.delete() }
    }
}
