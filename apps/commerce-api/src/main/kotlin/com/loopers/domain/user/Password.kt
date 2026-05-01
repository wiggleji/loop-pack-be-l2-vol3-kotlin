package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 비밀번호 Value Object
 *
 * 팩토리 메서드 [create]를 통해서만 인스턴스를 생성할 수 있으며,
 * 생성 시점에 길이, 포맷, 생년월일 포함 여부를 검증한다.
 *
 * @property value 검증된 원문 비밀번호 (암호화 전)
 */
class Password private constructor(val value: String) {

    companion object {
        // 영문 대소문자, 숫자, 특수문자만 허용
        private val FORMAT_REGEX = "^[A-Za-z0-9!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]+$".toRegex()
        private const val MIN_LENGTH = 8
        private const val MAX_LENGTH = 16

        /**
         * 비밀번호 생성 팩토리 메서드
         *
         * 검증 순서: 길이 -> 포맷 -> 생년월일 포함 여부
         *
         * @param rawPassword 원문 비밀번호
         * @param birthDate 사용자 생년월일 (비밀번호에 포함될 수 없음)
         * @return 검증된 [Password] 인스턴스
         * @throws CoreException 유효하지 않은 비밀번호인 경우 (BAD_REQUEST)
         */
        fun create(rawPassword: String, birthDate: LocalDate): Password {
            validateLength(rawPassword)
            validateFormat(rawPassword)
            validateNoBirthDatePattern(rawPassword, birthDate)
            return Password(rawPassword)
        }

        /**
         * 비밀번호 길이 검증 (8~16자)
         */
        private fun validateLength(password: String) {
            if (password.length < MIN_LENGTH || password.length > MAX_LENGTH) {
                throw CoreException(
                    errorType = ErrorType.BAD_REQUEST,
                    customMessage = "비밀번호 길이는 8~16자리로 설정가능합니다."
                )
            }
        }

        /**
         * 비밀번호 문자 포맷 검증 (영문 대소문자, 숫자, 특수문자만 허용)
         */
        private fun validateFormat(password: String) {
            if (!password.matches(FORMAT_REGEX)) {
                throw CoreException(
                    errorType = ErrorType.BAD_REQUEST,
                    customMessage = "비밀번호는 영문 대소문자, 숫자, 특수문자만 가능합니다."
                )
            }
        }

        /**
         * 비밀번호 내 생년월일 패턴 포함 여부 검증
         *
         * yyyyMMdd, yyMMdd, MMdd 세 가지 형식을 검사한다.
         */
        private fun validateNoBirthDatePattern(password: String, birthDate: LocalDate) {
            val birthDatePatterns = listOf(
                birthDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                birthDate.format(DateTimeFormatter.ofPattern("yyMMdd")),
                birthDate.format(DateTimeFormatter.ofPattern("MMdd"))
            )
            for (pattern in birthDatePatterns) {
                if (password.contains(pattern)) {
                    throw CoreException(
                        errorType = ErrorType.BAD_REQUEST,
                        customMessage = "생년월일은 비밀번호 내에 포함될 수 없습니다."
                    )
                }
            }
        }
    }
}
