package com.loopers.domain.user

/**
 * 사용자 Repository 인터페이스 (Domain Layer)
 *
 * 도메인 계층에서 정의하며, 도메인 엔티티([User])를 기준으로 동작한다.
 * 구현체는 Infrastructure 계층의 [com.loopers.infrastructure.user.UserRepositoryImpl]이 담당한다.
 */
interface UserRepository {
    fun save(user: User): User
    fun findByUserId(userId: String): User?
    fun existsByUserId(userId: String): Boolean
}
