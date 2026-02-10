package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    // ID Regex : 영문과 숫자만 허용
    private val userIdRegex = "^[A-Za-z0-9]+\$".toRegex()
    // 비밀번호 Regex : 영문 대소문자, 숫자, 특수문자
    private val passwordRegex = "^[A-Za-z0-9!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]+$".toRegex()
    // 이메일 Regex : `{}@{}`
    private val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex()

    /**
     * 사용자 생성
     * @param userId: ID
     * @param password: 비밀번호
     * @param name: 이름
     * @param birthDate: 생년월일
     * @param email: 이메일
     * @return 사용자 Entity
     * @throws CoreException: 이미 존재하는 계정
     */
    @Transactional
    fun createUser(userId: String, password: String, name: String, birthDate: LocalDate, email: String): UserModel {
        // 입력값 유효성 검증
        validateUserId(userId)
        validateEmail(email)
        validateBirthDate(birthDate)
        validatePassword(password, birthDate)

        // 사용자 중복 체크
        if (userRepository.existsByUserId(userId))
            throw CoreException(errorType = ErrorType.CONFLICT, customMessage = "[$userId] 해당 ID에 해당하는 계정이 존재합니다.")

        // 비밀번호 암호화 적용
        val encryptedPassword = passwordEncoder.encode(password)

        // 사용자 생성
        val userModel = UserModel(
            userId = userId,
            encryptedPassword = encryptedPassword,
            name = name,
            birthDate = birthDate,
            email = email
        )

        return userRepository.save(userModel)
    }

    /**
     * 사용자 ID 로 사용자 조회
     * @param userId: ID
     * @return 사용자 Entity
     * @throws CoreException: 존재하지 않는 계정
     */
    @Transactional(readOnly = true)
    fun getUserByUserId(userId: String): UserModel {
        return userRepository.findByUserId(userId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[$userId] 해당 ID에 해당하는 계정이 존재하지 않습니다.")
    }

    /**
     * 사용자 비밀번호 수정
     * 유효성 검증 -> 기존 비밀번호 비교로 진행
     * @param userId: ID
     * @param oldPassword: 기존 비밀번호
     * @param newPassword: 신규 비밀번호
     * @throws CoreException: 기존 비밀번호 불일치 / 현재와 동일한 비밀번호 / 유효하지 않은 비밀번호
     */
    @Transactional
    fun changePassword(userId: String, oldPassword: String, newPassword: String) {
        // 사용자 정보 조회
        val user = getUserByUserId(userId = userId)

        // 기존 비밀번호 확인
        if (!passwordEncoder.matches(oldPassword, user.encryptedPassword))
            throw CoreException(
                errorType = ErrorType.UNAUTHORIZED,
                customMessage = "기존 비밀번호가 일치하지 않습니다.")

        // 신규 비밀번호 유효성 검증
        validatePassword(newPassword, user.birthDate)

        // 현재 비밀번호와 신규 비밀번호 동일 여부 확인
        if (passwordEncoder.matches(newPassword, user.encryptedPassword))
            throw CoreException(
                errorType = ErrorType.BAD_REQUEST,
                customMessage = "현재 비밀번호와 동일한 비밀번호는 사용할 수 없습니다.")

        // 신규 비밀번호 암호화 및 저장
        user.updatePassword(passwordEncoder.encode(newPassword))
        userRepository.save(user)
    }

    /**
     * 사용자 인증
     * @param userId: ID
     * @param password: 비밀번호
     * @return 사용자 Entity
     * @throws CoreException: 유효하지 않은 인증정보인 경우
     */
    @Transactional(readOnly = true)
    fun authenticate(userId: String, password: String): UserModel {
        // 사용자 정보 조회
        val user = userRepository.findByUserId(userId)

        val userValid = if (user != null) {
            passwordEncoder.matches(password, user.encryptedPassword)
        } else {
            // timing attack 방지를 위한 fault matches
            // 사용자 존재 여부를 알지 못하게 동일한 response time 보장
            passwordEncoder.matches(password, "\\\$2a\\\$10\\\$dummyHashForTimingAttackPrevention")
            false
        }

        // 사용자 비밀번호 확인
        if (!userValid)
            throw CoreException(
                errorType = ErrorType.UNAUTHORIZED,
                customMessage = "유효하지 않은 인증정보입니다.")

        return user!!
    }

    /**
     * 이메일 유효성 검증
     * 1. `{}@{}` 포맷 확인
     * @param email: 이메일
     * @return 이메일 유효여부
     */
    private fun validateEmail(email: String): Boolean {
        if (!email.matches(emailRegex)) {
            throw CoreException(
                errorType = ErrorType.BAD_REQUEST,
                customMessage = "유효하지 않은 이메일 포맷입니다.")
        }
        return true
    }

    /**
     * 사용자 ID 유효성 검증
     * 1. 영문과 숫자만 허용
     * @param userId: ID
     * @return ID 유효여부
     */
    private fun validateUserId(userId: String): Boolean {
        if (!userId.matches(userIdRegex)) {
            throw CoreException(
                errorType = ErrorType.BAD_REQUEST,
                customMessage = "유효하지 않은 ID 포맷입니다. 영문과 숫자만 허용됩니다.")
        }
        return true
    }

    /**
     * 생년월일 유효성 검증
     * 1. 생년월일은 현재시점을 넘어갈 수 없다.
     * @param birthDate: 생년월일
     * @return 생년월일 유효여부
     */
    private fun validateBirthDate(birthDate: LocalDate): Boolean {
        if (birthDate.isAfter(LocalDate.now()))
            throw CoreException(ErrorType.BAD_REQUEST, "생년월일은 현재보다 미래일 수 없습니다.")
        return true
    }

    /**
     * 비밀번호 유효성 검증
     * 1. 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다.
     * 2. 생년월일은 비밀번호 내에 포함될 수 없습니다.
     * @param password: 비밀번호
     * @param birthDate: 생년월일
     * @return 비밀번호 유효여부
     */
    private fun validatePassword(password: String, birthDate: LocalDate): Boolean {
        if (password.length < 8 || password.length > 16) {
            throw CoreException(
                errorType = ErrorType.BAD_REQUEST,
                customMessage = "비밀번호 길이는 8~16자리로 설정가능합니다."
            )
        }
        if (!password.matches(passwordRegex)) {
            throw CoreException(
                errorType = ErrorType.BAD_REQUEST,
                customMessage = "비밀번호는 영문 대소문자, 숫자, 특수문자만 가능합니다."
            )
        }

        val birthDatePatterns =  listOf(
            birthDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
            birthDate.format(DateTimeFormatter.ofPattern("yyMMdd")),
            birthDate.format(DateTimeFormatter.ofPattern("MMdd"))
        )
        for (pattern in birthDatePatterns) {
            if (password.contains(pattern)) {
                throw CoreException(
                    errorType = ErrorType.BAD_REQUEST,
                    customMessage = "생년월일은 비밀번호 내에 포함될 수 없습니다."
                )
            }
        }
        return true
    }
}
