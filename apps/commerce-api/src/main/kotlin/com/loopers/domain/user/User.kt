package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate

/**
 * 사용자 도메인 엔티티 (JPA 비의존)
 *
 * JPA 엔티티([com.loopers.infrastructure.user.UserEntity])와 분리된 순수 도메인 객체이다.
 * Repository 계층에서 JPA 엔티티와 상호 변환하여 사용한다.
 *
 * @property id 엔티티 식별자 (영속화 전에는 0L)
 * @property userId 사용자 로그인 ID
 * @property encryptedPassword 암호화된 비밀번호
 * @property name 사용자 이름
 * @property birthDate 생년월일
 * @property email 이메일
 */
class User(
    userId: String,
    encryptedPassword: String,
    name: String,
    birthDate: LocalDate,
    email: String,
    val id: Long = 0L,
) {
    var userId: String = userId
        private set

    var encryptedPassword: String = encryptedPassword
        private set

    var name: String = name
        private set

    var birthDate: LocalDate = birthDate
        private set

    var email: String = email
        private set

    init {
        // 기본 null/blank 검증만 수행 (비즈니스 검증은 서비스 계층에서 처리)
        if (userId.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "아이디는 비어있을 수 없습니다.")
        if (encryptedPassword.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "암호는 비어있을 수 없습니다.")
        if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "이름는 비어있을 수 없습니다.")
        if (email.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "이메일은 비어있을 수 없습니다.")
    }

    /**
     * 사용자 암호 변경
     *
     * init 블록과 동일한 불변조건(invariant)을 유지한다.
     *
     * @param newEncryptedPassword 암호화된 신규 패스워드
     * @throws CoreException 비어있는 패스워드인 경우 (BAD_REQUEST)
     */
    fun updatePassword(newEncryptedPassword: String) {
        if (newEncryptedPassword.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "암호는 비어있을 수 없습니다.")
        this.encryptedPassword = newEncryptedPassword
    }
}
