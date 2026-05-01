package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 사용자 도메인 서비스
 *
 * 사용자 생성, 조회, 저장 등 순수 도메인 CRUD 로직을 담당한다.
 * 인증, 비밀번호 암호화/검증, 이메일/비밀번호 포맷 검증은
 * [com.loopers.application.auth.AuthFacade]에서 처리한다.
 */
@Service
class UserService(
    private val userRepository: UserRepository,
) {

    // ID Regex : 영문과 숫자만 허용
    private val userIdRegex = "^[A-Za-z0-9]+$".toRegex()

    /**
     * 사용자 생성
     *
     * userId/birthDate 유효성 검증 후 사용자를 생성한다.
     * 비밀번호는 이미 암호화된 상태로 전달받는다.
     *
     * @param userId 사용자 로그인 ID
     * @param encryptedPassword 암호화된 비밀번호
     * @param name 사용자 이름
     * @param birthDate 생년월일
     * @param email 이메일
     * @return 생성된 사용자 도메인 엔티티
     * @throws CoreException 유효하지 않은 ID (BAD_REQUEST) 또는 이미 존재하는 계정 (CONFLICT)
     */
    @Transactional
    fun createUser(userId: String, encryptedPassword: String, name: String, birthDate: LocalDate, email: String): User {
        validateUserId(userId)
        validateBirthDate(birthDate)

        // 사용자 중복 체크
        if (userRepository.existsByUserId(userId))
            throw CoreException(errorType = ErrorType.CONFLICT, customMessage = "[$userId] 해당 ID에 해당하는 계정이 존재합니다.")

        val user = User(
            userId = userId,
            encryptedPassword = encryptedPassword,
            name = name,
            birthDate = birthDate,
            email = email,
        )
        return userRepository.save(user)
    }

    /**
     * 사용자 ID 로 사용자 조회
     * @param userId 사용자 로그인 ID
     * @return 사용자 도메인 엔티티
     * @throws CoreException 존재하지 않는 계정 (NOT_FOUND)
     */
    @Transactional(readOnly = true)
    fun getUserByUserId(userId: String): User {
        return userRepository.findByUserId(userId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[$userId] 해당 ID에 해당하는 계정이 존재하지 않습니다.")
    }

    /**
     * 사용자 ID 로 사용자 조회 (nullable)
     *
     * 인증 시 사용자 미존재를 예외 대신 null 로 처리하기 위해 사용한다.
     *
     * @param userId 사용자 로그인 ID
     * @return 사용자 도메인 엔티티 또는 null
     */
    @Transactional(readOnly = true)
    fun findByUserId(userId: String): User? = userRepository.findByUserId(userId)

    /**
     * 사용자 도메인 엔티티 저장
     * @param user 저장할 사용자 도메인 엔티티
     * @return 저장된 사용자 도메인 엔티티
     */
    @Transactional
    fun save(user: User): User = userRepository.save(user)

    /**
     * 사용자 ID 유효성 검증
     * 영문과 숫자만 허용
     */
    private fun validateUserId(userId: String) {
        if (!userId.matches(userIdRegex)) {
            throw CoreException(
                errorType = ErrorType.BAD_REQUEST,
                customMessage = "유효하지 않은 ID 포맷입니다. 영문과 숫자만 허용됩니다.")
        }
    }

    /**
     * 생년월일 유효성 검증
     * 생년월일은 현재시점을 넘어갈 수 없다.
     */
    private fun validateBirthDate(birthDate: LocalDate) {
        if (birthDate.isAfter(LocalDate.now()))
            throw CoreException(ErrorType.BAD_REQUEST, "생년월일은 현재보다 미래일 수 없습니다.")
    }
}
