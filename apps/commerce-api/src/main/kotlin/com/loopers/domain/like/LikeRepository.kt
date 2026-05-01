package com.loopers.domain.like

interface LikeRepository {
    fun save(like: Like): Like
    fun findByUserIdAndProductId(userId: Long, productId: Long): Like?
    fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean
    fun findAllByUserId(userId: Long): List<Like>
    fun deleteByUserIdAndProductId(userId: Long, productId: Long)
    fun deleteAllByProductId(productId: Long)
}
