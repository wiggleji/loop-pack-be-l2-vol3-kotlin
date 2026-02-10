package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component


@Component
class UserPasswordEncoder: PasswordEncoder {

    private val encoder = BCryptPasswordEncoder()

    override fun encode(rawPassword: CharSequence?): String {
        return encoder.encode(rawPassword)
    }

    override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean {
        require(!encodedPassword.isNullOrBlank()) { throw CoreException(ErrorType.UNAUTHORIZED, "비밀번호는 암호화 시 필수로 입력해야 합니다.") }
        return encoder.matches(rawPassword, encodedPassword)
    }
}
