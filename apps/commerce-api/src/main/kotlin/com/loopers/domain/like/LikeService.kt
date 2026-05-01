package com.loopers.domain.like

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LikeService(
    private val likeRepository: LikeRepository,
) {

    @Transactional
    fun addLike(userId: Long, productId: Long): Like {
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            throw CoreException(ErrorType.CONFLICT, "이미 좋아요를 누른 상품입니다.")
        }
        val like = Like(userId = userId, productId = productId)
        return likeRepository.save(like)
    }

    @Transactional
    fun removeLike(userId: Long, productId: Long) {
        if (!likeRepository.existsByUserIdAndProductId(userId, productId)) {
            throw CoreException(ErrorType.NOT_FOUND, "좋아요 기록이 존재하지 않습니다.")
        }
        likeRepository.deleteByUserIdAndProductId(userId, productId)
    }

    @Transactional(readOnly = true)
    fun getLikedByUser(userId: Long): List<Like> =
        likeRepository.findAllByUserId(userId)
}
