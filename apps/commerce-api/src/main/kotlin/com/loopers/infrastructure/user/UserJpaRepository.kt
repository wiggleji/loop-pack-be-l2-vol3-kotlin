package com.loopers.infrastructure.user

import org.springframework.data.jpa.repository.JpaRepository

/**
 * 사용자 JPA Repository (Infrastructure Layer)
 *
 * Spring Data JPA 를 통해 [UserEntity]에 대한 기본 CRUD 및 커스텀 쿼리를 제공한다.
 */
interface UserJpaRepository : JpaRepository<UserEntity, Long> {
    fun findByUserId(userId: String): UserEntity?
    fun existsByUserId(userId: String): Boolean
}
