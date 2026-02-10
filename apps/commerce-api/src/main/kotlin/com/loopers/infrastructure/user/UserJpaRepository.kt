package com.loopers.infrastructure.user

import com.loopers.domain.user.UserModel
import org.springframework.data.jpa.repository.JpaRepository

interface UserJpaRepository: JpaRepository<UserModel, Long> {
    fun findByUserId(userId: String): UserModel?
    fun existsByUserId(userId: String): Boolean
}
