package com.loopers.interfaces.api.like

import com.loopers.application.like.LikeFacade
import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.security.LoginUser
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class LikeV1Controller(
    private val likeFacade: LikeFacade,
) : LikeV1ApiSpec {

    @PostMapping("/api/v1/products/{productId}/likes")
    override fun addLike(
        @LoginUser user: User,
        @PathVariable productId: Long,
    ): ApiResponse<Any> {
        likeFacade.addLike(user.id, productId)
        return ApiResponse.success()
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    override fun removeLike(
        @LoginUser user: User,
        @PathVariable productId: Long,
    ): ApiResponse<Any> {
        likeFacade.removeLike(user.id, productId)
        return ApiResponse.success()
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    override fun getLikedProducts(
        @LoginUser user: User,
        @PathVariable userId: Long,
    ): ApiResponse<List<LikeV1Dto.LikedProductResponse>> {
        if (user.id != userId) throw CoreException(ErrorType.UNAUTHORIZED, "다른 사용자의 정보에 접근할 수 없습니다.")
        return likeFacade.getLikedProducts(user.id)
            .map { LikeV1Dto.LikedProductResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
