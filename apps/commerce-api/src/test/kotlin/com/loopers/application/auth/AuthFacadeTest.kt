package com.loopers.application.auth

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

@SpringBootTest
@ActiveProfiles("test")
class AuthFacadeTest @Autowired constructor(
    private val authFacade: AuthFacade,
    private val passwordEncoder: PasswordEncoder,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    // ─── signup ───

    @Test
    fun `signup() should create user with valid data`() {
        // Act
        val user = authFacade.signup("testId", "testPassword", "testName", LocalDate.now(), "test@email.com")

        // Assert
        assertThat(user.id).isGreaterThan(0)
        assertThat(user.userId).isEqualTo("testId")
        assertThat(user.name).isEqualTo("testName")
        assertThat(user.email).isEqualTo("test@email.com")
    }

    @Test
    fun `signup() should encrypt password`() {
        // Act
        val user = authFacade.signup("testId", "testPassword", "testName", LocalDate.now(), "test@email.com")

        // Assert
        assertThat(user.encryptedPassword).isNotEqualTo("testPassword")
        assertThat(user.encryptedPassword).startsWith("$2a$")
        assertThat(passwordEncoder.matches("testPassword", user.encryptedPassword)).isTrue()
    }

    @Test
    fun `signup() throws CoreException(BAD_REQUEST) when email format is invalid`() {
        // Act & Assert
        val exception = assertThrows<CoreException> {
            authFacade.signup("testUser", "password123!", "testName", LocalDate.now(), "invalid-email")
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("이메일")
    }

    @Test
    fun `signup() throws CoreException(BAD_REQUEST) when password is too short`() {
        // Act & Assert
        val exception = assertThrows<CoreException> {
            authFacade.signup("testUser", "pass123", "testName", LocalDate.now(), "test@email.com")
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("8~16자")
    }

    @Test
    fun `signup() throws CoreException(BAD_REQUEST) when password contains birthDate`() {
        // Act & Assert
        val exception = assertThrows<CoreException> {
            authFacade.signup("testUser", "pass19900101", "testName", LocalDate.of(1990, 1, 1), "test@email.com")
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("생년월일")
    }

    // ─── authenticate ───

    @Test
    fun `authenticate() returns User with correct password`() {
        // Arrange
        authFacade.signup("testId", "testPassword", "testName", LocalDate.now(), "test@email.com")

        // Act
        val authenticatedUser = authFacade.authenticate("testId", "testPassword")

        // Assert
        assertThat(authenticatedUser).isNotNull
        assertThat(authenticatedUser.userId).isEqualTo("testId")
    }

    @Test
    fun `authenticate() throws CoreException(UNAUTHORIZED) when userId does not exist`() {
        // Act & Assert
        val exception = assertThrows<CoreException> {
            authFacade.authenticate("nonExistentUser", "anyPassword")
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        assertThat(exception.message).contains("인증정보")
    }

    @Test
    fun `authenticate() throws CoreException(UNAUTHORIZED) when password is incorrect`() {
        // Arrange
        authFacade.signup("testUser", "correctPass123!", "testName", LocalDate.now(), "test@email.com")

        // Act & Assert
        val exception = assertThrows<CoreException> {
            authFacade.authenticate("testUser", "wrongPassword!")
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        assertThat(exception.message).contains("인증정보")
    }

    // ─── changePassword ───

    @Test
    fun `changePassword() should change password and authenticate with new password`() {
        // Arrange
        authFacade.signup("testId", "testPassword", "testName", LocalDate.now(), "test@email.com")

        // Act
        authFacade.changePassword("testId", "testPassword", "newPassword123!")

        // Assert
        val authenticatedUser = authFacade.authenticate("testId", "newPassword123!")
        assertThat(authenticatedUser.userId).isEqualTo("testId")
    }

    @Test
    fun `changePassword() throws CoreException(UNAUTHORIZED) when old password is incorrect`() {
        // Arrange
        authFacade.signup("testUser", "oldPassword123!", "testName", LocalDate.now(), "test@email.com")

        // Act & Assert
        val exception = assertThrows<CoreException> {
            authFacade.changePassword("testUser", "wrongOldPassword!", "newPassword456!")
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        assertThat(exception.message).contains("일치하지 않습니다")
    }

    @Test
    fun `changePassword() throws CoreException(BAD_REQUEST) when new password is same as current password`() {
        // Arrange
        authFacade.signup("testUser", "currentPass123!", "testName", LocalDate.now(), "test@email.com")

        // Act & Assert
        val exception = assertThrows<CoreException> {
            authFacade.changePassword("testUser", "currentPass123!", "currentPass123!")
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("현재 비밀번호와")
    }

    @Test
    fun `changePassword() throws CoreException(BAD_REQUEST) when new password is too short`() {
        // Arrange
        authFacade.signup("testUser", "oldPassword123!", "testName", LocalDate.now(), "test@email.com")

        // Act & Assert
        val exception = assertThrows<CoreException> {
            authFacade.changePassword("testUser", "oldPassword123!", "short12")
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("8~16자")
    }

    // ─── 경계값 테스트 ───

    @Test
    fun `signup() should accept password with exactly 8 characters`() {
        // Act
        val user = authFacade.signup("testUser", "pass1234", "testName", LocalDate.now(), "test@email.com")

        // Assert
        assertThat(user).isNotNull
        assertThat(passwordEncoder.matches("pass1234", user.encryptedPassword)).isTrue()
    }

    @Test
    fun `signup() should accept password with exactly 16 characters`() {
        // Act
        val user = authFacade.signup("testUser", "password12345678", "testName", LocalDate.now(), "test@email.com")

        // Assert
        assertThat(user).isNotNull
        assertThat(passwordEncoder.matches("password12345678", user.encryptedPassword)).isTrue()
    }
}
