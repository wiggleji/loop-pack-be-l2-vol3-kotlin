package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate


@Entity
@Table(name = "users")
class UserModel(
    userId: String,
    encryptedPassword: String,
    name: String,
    birthDate: LocalDate,
    email: String,
) : BaseEntity() {

    @Column(unique = true, nullable = false)
    var userId: String = userId
        protected set

    @Column(nullable = false, name = "encrypted_password")
    var encryptedPassword: String = encryptedPassword
        protected set

    @Column(nullable = false)
    var name: String = name
        protected set

    @Column(nullable = false, name = "birth_date")
    var birthDate: LocalDate = birthDate
        protected set

    @Column(nullable = false)
    var email: String = email
        protected set

    init {
        if (userId.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "아이디는 비어있을 수 없습니다.")
        if (encryptedPassword.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "암호는 비어있을 수 없습니다.")
        if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "이름는 비어있을 수 없습니다.")
        if (email.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "이메일은 비어있을 수 없습니다.")
    }

    /**
     * 사용자 암호 변경
     * @param newEncryptedPassword[String] 암호화된 신규 패스워드
     */
    fun updatePassword(newEncryptedPassword: String) {
        if (newEncryptedPassword.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "암호는 비어있을 수 없습니다.")
        this.encryptedPassword = newEncryptedPassword
    }
}
