package com.loopers.interfaces.api.user

import com.loopers.application.auth.AuthFacade
import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.security.LoginUser
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users")
class UserV1Controller(
    private val authFacade: AuthFacade,
) : UserV1ApiSpec {

    @PostMapping
    override fun signup(@RequestBody request: UserV1Dto.SignupRequest): ApiResponse<UserV1Dto.UserResponse> =
        authFacade.signup(
            userId = request.userId,
            rawPassword = request.password,
            name = request.name,
            birthDate = request.birthDate,
            email = request.email,
        )
            .let { UserV1Dto.UserResponse.from(it) }
            .let { ApiResponse.success(it) }

    @GetMapping("/me")
    override fun getMyInfo(@LoginUser user: User): ApiResponse<UserV1Dto.UserResponse> =
        UserV1Dto.UserResponse.fromMasked(user)
            .let { ApiResponse.success(it) }

    @PutMapping("/password")
    override fun changePassword(
        @LoginUser user: User,
        @RequestBody @Valid request: UserV1Dto.UserChangePasswordRequest,
    ): ApiResponse<Any> {
        authFacade.changePassword(user.userId, request.oldPassword, request.newPassword)
        return ApiResponse.success()
    }
}
