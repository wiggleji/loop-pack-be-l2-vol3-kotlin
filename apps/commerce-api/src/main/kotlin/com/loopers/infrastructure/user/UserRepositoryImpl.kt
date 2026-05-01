package com.loopers.infrastructure.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import org.springframework.stereotype.Repository

/**
 * 사용자 Repository 구현체 (Infrastructure Layer)
 *
 * [UserRepository] 인터페이스를 구현하며,
 * [UserEntity](JPA) <-> [User](Domain) 간 매핑을 담당한다.
 */
@Repository
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {

    /**
     * 사용자 저장
     *
     * id > 0 인 경우 기존 엔티티를 조회하여 비밀번호를 업데이트하고,
     * 그렇지 않으면 새로운 JPA 엔티티를 생성하여 저장한다.
     */
    override fun save(user: User): User {
        val entity = if (user.id > 0L) {
            // 기존 영속 엔티티의 비밀번호를 갱신 (dirty checking 활용)
            userJpaRepository.getReferenceById(user.id).apply {
                updatePassword(user.encryptedPassword)
            }
        } else {
            UserEntity.from(user)
        }
        return userJpaRepository.save(entity).toDomain()
    }

    override fun findByUserId(userId: String): User? =
        userJpaRepository.findByUserId(userId)?.toDomain()

    override fun existsByUserId(userId: String): Boolean =
        userJpaRepository.existsByUserId(userId)
}
