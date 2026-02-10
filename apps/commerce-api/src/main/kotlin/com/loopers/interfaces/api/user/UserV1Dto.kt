package com.loopers.interfaces.api.user

import com.loopers.domain.user.UserModel
import java.time.LocalDate

/**
 * 사용자 V1 API DTO
 */
class UserV1Dto {

    companion object {
        // NOTE: 보안 관련 정책 및 모듈 정의시 이전필요
        const val MASK_LETTER = "*"
    }

    data class SignupRequest(
        val userId: String,
        val password: String,
        val name: String,
        val birthDate: LocalDate,
        val email: String,
    )

    data class UserChangePasswordRequest(
        val oldPassword: String,
        val newPassword: String,
    )

    data class UserResponse(
        val userId: String,
        val name: String,
        val birthDate: LocalDate,
        val email: String,
    ) {
        companion object {
            fun from(user: UserModel): UserResponse {
                return UserResponse(
                    userId = user.userId,
                    name = user.name,
                    birthDate = user.birthDate,
                    email = user.email,
                )
            }

            fun fromMasked(user: UserModel): UserResponse {
                return UserResponse(
                    userId = user.userId,
                    name = user.name.mask(),
                    birthDate = user.birthDate,
                    email = user.email,
                )
            }

            /**
             * 문자의 마지막 마스킹 확장함수
             * NOTE: 보안 관련 정책 및 모듈 정의시 이전필요
             */
            private fun String.mask() =
                if (isEmpty())
                    MASK_LETTER
                else
                    replaceRange(length - 1, length, MASK_LETTER)
        }
    }
}
