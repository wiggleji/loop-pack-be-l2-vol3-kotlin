package com.loopers.infrastructure.user

import com.loopers.domain.BaseEntity
import com.loopers.domain.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate

/**
 * 사용자 JPA 엔티티 (Infrastructure Layer)
 *
 * 데이터베이스 영속화 전용 엔티티이며,
 * 도메인 엔티티([User])와의 변환은 [toDomain]/[from]을 통해 수행한다.
 */
@Entity
@Table(name = "users")
class UserEntity(
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

    /**
     * 사용자 암호 변경
     * @param newEncryptedPassword 암호화된 신규 패스워드
     */
    fun updatePassword(newEncryptedPassword: String) {
        this.encryptedPassword = newEncryptedPassword
    }

    /**
     * JPA 엔티티를 도메인 엔티티로 변환
     * @return [User] 도메인 엔티티
     */
    fun toDomain(): User = User(
        id = this.id,
        userId = this.userId,
        encryptedPassword = this.encryptedPassword,
        name = this.name,
        birthDate = this.birthDate,
        email = this.email,
    )

    companion object {
        /**
         * 도메인 엔티티를 JPA 엔티티로 변환
         * @param user 도메인 엔티티
         * @return [UserEntity] JPA 엔티티
         */
        fun from(user: User): UserEntity = UserEntity(
            userId = user.userId,
            encryptedPassword = user.encryptedPassword,
            name = user.name,
            birthDate = user.birthDate,
            email = user.email,
        )
    }
}
