package com.loopers.infrastructure.like

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface LikeJpaRepository : JpaRepository<LikeEntity, Long> {

    fun findByUserIdAndProductId(userId: Long, productId: Long): LikeEntity?

    fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean

    @Query("SELECT l FROM LikeEntity l WHERE l.userId = :userId AND l.deletedAt IS NULL")
    fun findAllByUserId(userId: Long): List<LikeEntity>

    fun deleteByUserIdAndProductId(userId: Long, productId: Long)

    @Query("SELECT l FROM LikeEntity l WHERE l.productId = :productId AND l.deletedAt IS NULL")
    fun findAllByProductId(productId: Long): List<LikeEntity>
}
