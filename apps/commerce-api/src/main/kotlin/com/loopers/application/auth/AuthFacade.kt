package com.loopers.application.auth

import com.loopers.domain.user.Email
import com.loopers.domain.user.Password
import com.loopers.domain.user.User
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 인증 관련 유스케이스 Facade (Application Layer)
 *
 * 회원가입, 인증, 비밀번호 변경 등의 흐름을 조율한다.
 * Value Object([Email], [Password])를 통한 입력 검증과
 * [PasswordEncoder]를 통한 암호화를 담당하며,
 * 도메인 로직은 [UserService]에 위임한다.
 */
@Service
class AuthFacade(
    private val userService: UserService,
    private val passwordEncoder: PasswordEncoder,
) {

    /**
     * 회원가입
     *
     * 1. Email/Password Value Object 로 입력값 검증
     * 2. 비밀번호 암호화
     * 3. UserService 에 사용자 생성 위임
     *
     * @param userId 사용자 로그인 ID
     * @param rawPassword 원문 비밀번호
     * @param name 사용자 이름
     * @param birthDate 생년월일
     * @param email 이메일
     * @return 생성된 사용자 도메인 엔티티
     * @throws CoreException 유효하지 않은 이메일/비밀번호 (BAD_REQUEST) 또는 중복 ID (CONFLICT)
     */
    fun signup(userId: String, rawPassword: String, name: String, birthDate: LocalDate, email: String): User {
        // Value Object 를 통한 입력 검증
        val validatedEmail = Email(email)
        val password = Password.create(rawPassword, birthDate)

        // 비밀번호 암호화
        val encryptedPassword = passwordEncoder.encode(password.value)

        // 도메인 서비스에 생성 위임 (검증된 email.value 전달)
        return userService.createUser(userId, encryptedPassword, name, birthDate, validatedEmail.value)
    }

    /**
     * 사용자 인증
     *
     * @param userId 사용자 로그인 ID
     * @param rawPassword 원문 비밀번호
     * @return 인증된 사용자 도메인 엔티티
     * @throws CoreException 유효하지 않은 인증정보인 경우 (UNAUTHORIZED)
     */
    @Transactional(readOnly = true)
    fun authenticate(userId: String, rawPassword: String): User {
        val user = userService.findByUserId(userId)

        val isValid = if (user != null) {
            passwordEncoder.matches(rawPassword, user.encryptedPassword)
        } else {
            // timing attack 방지: 사용자 미존재 시에도 BCrypt 연산을 수행하여
            // 존재 여부에 따른 응답 시간 차이를 제거한다.
            passwordEncoder.matches(rawPassword, "\\\$2a\\\$10\\\$dummyHashForTimingAttackPrevention")
            false
        }

        if (!isValid)
            throw CoreException(
                errorType = ErrorType.UNAUTHORIZED,
                customMessage = "유효하지 않은 인증정보입니다.")

        return user!!
    }

    /**
     * 비밀번호 변경
     *
     * 1. 기존 비밀번호 확인
     * 2. 신규 비밀번호 유효성 검증 (Password VO)
     * 3. 현재 비밀번호와 동일 여부 확인
     * 4. 암호화 후 저장
     *
     * @param userId 사용자 로그인 ID
     * @param oldPassword 기존 비밀번호
     * @param newPassword 신규 비밀번호
     * @throws CoreException 기존 비밀번호 불일치 (UNAUTHORIZED),
     *   유효하지 않은 신규 비밀번호 (BAD_REQUEST),
     *   현재와 동일한 비밀번호 (BAD_REQUEST)
     */
    @Transactional
    fun changePassword(userId: String, oldPassword: String, newPassword: String) {
        val user = userService.getUserByUserId(userId)

        // 기존 비밀번호 확인
        if (!passwordEncoder.matches(oldPassword, user.encryptedPassword))
            throw CoreException(
                errorType = ErrorType.UNAUTHORIZED,
                customMessage = "기존 비밀번호가 일치하지 않습니다.")

        // 신규 비밀번호 유효성 검증 (Password VO 를 통해 길이/포맷/생년월일 검사)
        Password.create(newPassword, user.birthDate)

        // 현재 비밀번호와 동일 여부 확인
        if (passwordEncoder.matches(newPassword, user.encryptedPassword))
            throw CoreException(
                errorType = ErrorType.BAD_REQUEST,
                customMessage = "현재 비밀번호와 동일한 비밀번호는 사용할 수 없습니다.")

        // 신규 비밀번호 암호화 및 도메인 엔티티 업데이트
        user.updatePassword(passwordEncoder.encode(newPassword))
        userService.save(user)
    }
}
