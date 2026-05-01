package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

/**
 * 이메일 Value Object
 *
 * 생성 시점에 포맷 검증을 수행하며, 유효하지 않은 이메일은 인스턴스화할 수 없다.
 *
 * @property value 검증된 이메일 문자열
 * @throws CoreException 유효하지 않은 이메일 포맷인 경우 (BAD_REQUEST)
 */
class Email(val value: String) {

    init {
        if (!value.matches(FORMAT_REGEX))
            throw CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 이메일 포맷입니다.")
    }

    companion object {
        // `{local}@{domain}` 형식의 이메일 포맷 정규식
        private val FORMAT_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex()
    }
}
