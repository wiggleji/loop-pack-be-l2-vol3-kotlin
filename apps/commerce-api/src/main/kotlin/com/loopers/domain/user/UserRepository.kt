package com.loopers.domain.user

interface UserRepository {
    fun save(user: UserModel): UserModel
    fun findByUserId(userId: String): UserModel?
    fun existsByUserId(userId: String): Boolean
}
