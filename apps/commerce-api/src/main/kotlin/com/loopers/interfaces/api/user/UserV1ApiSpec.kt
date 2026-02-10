package com.loopers.interfaces.api.user

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.security.AuthHeader
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1 API", description = "사용자 관련 API 입니다.")
interface UserV1ApiSpec {

    @Operation(
        summary = "회원가입",
        description = "새로운 사용자 계정을 생성합니다.",
    )
    fun signup(request: UserV1Dto.SignupRequest): ApiResponse<UserV1Dto.UserResponse>

    @Operation(
        summary = "내 정보 조회",
        description = "로그인된 사용자의 정보를 조회합니다. 이름은 마스킹됩니다.",
    )
    fun getMyInfo(
        @Parameter(
            name = AuthHeader.HEADER_LOGIN_ID,
            description = "로그인 아이디",
            `in` = ParameterIn.HEADER,
            required = true
        ) loginId: String,
        @Parameter(
            name = AuthHeader.HEADER_LOGIN_PW,
            description = "로그인 비밀번호",
            `in` = ParameterIn.HEADER,
            required = true,
            hidden = true
        ) loginPw: String
    ): ApiResponse<UserV1Dto.UserResponse>

    @Operation(
        summary = "비밀번호 수정",
        description = "로그인된 사용자의 비밀번호를 수정합니다.",
    )
    fun changePassword(
        @Parameter(
            name = AuthHeader.HEADER_LOGIN_ID,
            description = "로그인 아이디",
            `in` = ParameterIn.HEADER,
            required = true
        ) loginId: String,
        @Parameter(
            name = AuthHeader.HEADER_LOGIN_PW,
            description = "로그인 비밀번호",
            `in` = ParameterIn.HEADER,
            required = true,
            hidden = true
        ) loginPw: String,
        request: UserV1Dto.UserChangePasswordRequest
    ): ApiResponse<Any>
}
