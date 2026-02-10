package com.loopers.domain.user

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
class UserServiceTest @Autowired constructor(
    private val userService: UserService,
    private val databaseCleanUp: DatabaseCleanUp,
    private val passwordEncoder: PasswordEncoder
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun `createUser() should create user with valid data`() {
        // Arrange
        val userId = "testId"
        val password = "testPassword"
        val name = "testName"
        val birthDate = LocalDate.now()
        val email = "test@email.com"

        // Act
        val user = userService.createUser(userId, password, name, birthDate, email)

        // Assert
        assertThat(user.id).isGreaterThan(0)
        assertThat(user.userId).isEqualTo(userId)
        assertThat(user.name).isEqualTo(name)
        assertThat(user.email).isEqualTo(email)
        assertThat(user).extracting("encryptedPassword").isNotEqualTo(password)
    }

    @Test
    fun `createUser() should encrypt password when creating user`() {
        // Arrange
        val userId = "testId"
        val password = "testPassword"
        val name = "testName"
        val birthDate = LocalDate.now()
        val email = "test@email.com"

        // Act
        val user = userService.createUser(userId, password, name, birthDate, email)

        // Assert
        assertThat(user.encryptedPassword).isNotEqualTo(password)
        assertThat(user.encryptedPassword).startsWith("$2a$") // BCrypt format
        assertThat(passwordEncoder.matches(password, user.encryptedPassword)).isTrue()
    }

    @Test
    fun `getUserByUserId() returns User`() {
        // Arrange
        val userId = "testId"
        val password = "testPassword"
        userService.createUser(userId, password, "testName", LocalDate.now(), "test@email.com")

        // Act
        val userFound = userService.getUserByUserId(userId)
        
        // Assert
        assertThat(userFound).isNotNull
        assertThat(userFound.userId).isEqualTo(userId)
    }

    @Test
    fun `authenticate() returns User with correct password`() {
        // Arrange
        val userId = "testId"
        val password = "testPassword"
        userService.createUser(userId, password, "testName", LocalDate.now(), "test@email.com")

        // Act
        val authenticatedUser = userService.authenticate(userId, password)

        // Assert
        assertThat(authenticatedUser).isNotNull
        assertThat(authenticatedUser.userId).isEqualTo(userId)
    }

    @Test
    fun `changePassword() should change password and authenticate with new password`() {
        // Arrange
        val userId = "testId"
        val oldPassword = "testPassword"
        val newPassword = "newPassword123!"
        userService.createUser(userId, oldPassword, "testName", LocalDate.now(), "test@email.com")

        // Act
        userService.changePassword(userId, oldPassword, newPassword)

        // Assert - 새 비밀번호로 인증 가능한지 확인
        val authenticatedUser = userService.authenticate(userId, newPassword)
        assertThat(authenticatedUser.userId).isEqualTo(userId)
    }

    // ─── 예외 케이스: createUser() ───

    @Test
    fun `createUser() throws CoreException(CONFLICT) when userId already exists`() {
        // Arrange
        val userId = "duplicateUser"
        userService.createUser(userId, "password123!", "firstName", LocalDate.of(1990, 1, 1), "test@email.com")

        // Act & Assert
        val exception = assertThrows<CoreException> {
            userService.createUser(userId, "password456!", "secondName", LocalDate.of(1991, 2, 2), "other@email.com")
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
        assertThat(exception.message).contains(userId)
    }

    @Test
    fun `createUser() throws CoreException(BAD_REQUEST) when userId contains special characters`() {
        // Arrange
        val invalidUserId = "user!@#"

        // Act & Assert
        val exception = assertThrows<CoreException> {
            userService.createUser(invalidUserId, "password123!", "testName", LocalDate.now(), "test@email.com")
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("ID")
    }

    @Test
    fun `createUser() throws CoreException(BAD_REQUEST) when email format is invalid`() {
        // Arrange
        val invalidEmail = "not-an-email"

        // Act & Assert
        val exception = assertThrows<CoreException> {
            userService.createUser("testUser", "password123!", "testName", LocalDate.now(), invalidEmail)
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("이메일")
    }

    @Test
    fun `createUser() throws CoreException(BAD_REQUEST) when password is too short`() {
        // Arrange
        val shortPassword = "pass123" // 7 chars

        // Act & Assert
        val exception = assertThrows<CoreException> {
            userService.createUser("testUser", shortPassword, "testName", LocalDate.now(), "test@email.com")
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("8~16자")
    }

    @Test
    fun `createUser() throws CoreException(BAD_REQUEST) when password is too long`() {
        // Arrange
        val longPassword = "password123456789" // 17 chars

        // Act & Assert
        val exception = assertThrows<CoreException> {
            userService.createUser("testUser", longPassword, "testName", LocalDate.now(), "test@email.com")
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("8~16자")
    }

    @Test
    fun `createUser() throws CoreException(BAD_REQUEST) when password contains birthDate`() {
        // Arrange
        val birthDate = LocalDate.of(1990, 1, 1)
        val passwordWithBirthDate = "pass19900101" // contains yyyyMMdd

        // Act & Assert
        val exception = assertThrows<CoreException> {
            userService.createUser("testUser", passwordWithBirthDate, "testName", birthDate, "test@email.com")
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("생년월일")
    }

    @Test
    fun `createUser() throws CoreException(BAD_REQUEST) when birthDate is in the future`() {
        // Arrange
        val futureBirthDate = LocalDate.now().plusDays(1)

        // Act & Assert
        val exception = assertThrows<CoreException> {
            userService.createUser("testUser", "password123!", "testName", futureBirthDate, "test@email.com")
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("생년월일")
        assertThat(exception.message).contains("미래")
    }

    // ─── 예외 케이스: authenticate() ───

    @Test
    fun `authenticate() throws CoreException(UNAUTHORIZED) when userId does not exist`() {
        // Arrange
        val nonExistentUserId = "nonExistentUser"

        // Act & Assert
        val exception = assertThrows<CoreException> {
            userService.authenticate(nonExistentUserId, "anyPassword")
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        assertThat(exception.message).contains("인증정보")
    }

    @Test
    fun `authenticate() throws CoreException(UNAUTHORIZED) when password is incorrect`() {
        // Arrange
        val userId = "testUser"
        val correctPassword = "correctPass123!"
        val wrongPassword = "wrongPassword!"
        userService.createUser(userId, correctPassword, "testName", LocalDate.now(), "test@email.com")

        // Act & Assert
        val exception = assertThrows<CoreException> {
            userService.authenticate(userId, wrongPassword)
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        assertThat(exception.message).contains("인증정보")
    }

    // ─── 예외 케이스: getUserByUserId() ───

    @Test
    fun `getUserByUserId() throws CoreException(NOT_FOUND) when user does not exist`() {
        // Arrange
        val nonExistentUserId = "nonExistentUser"

        // Act & Assert
        val exception = assertThrows<CoreException> {
            userService.getUserByUserId(nonExistentUserId)
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        assertThat(exception.message).contains(nonExistentUserId)
    }

    // ─── 예외 케이스: changePassword() ───

    @Test
    fun `changePassword() throws CoreException(UNAUTHORIZED) when old password is incorrect`() {
        // Arrange
        val userId = "testUser"
        val correctOldPassword = "oldPassword123!"
        val wrongOldPassword = "wrongOldPassword!"
        val newPassword = "newPassword456!"
        userService.createUser(userId, correctOldPassword, "testName", LocalDate.now(), "test@email.com")

        // Act & Assert
        val exception = assertThrows<CoreException> {
            userService.changePassword(userId, wrongOldPassword, newPassword)
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        assertThat(exception.message).contains("일치하지 않습니다")
    }

    @Test
    fun `changePassword() throws CoreException(BAD_REQUEST) when new password is same as current password`() {
        // Arrange
        val userId = "testUser"
        val currentPassword = "currentPass123!"
        userService.createUser(userId, currentPassword, "testName", LocalDate.now(), "test@email.com")

        // Act & Assert
        val exception = assertThrows<CoreException> {
            userService.changePassword(userId, currentPassword, currentPassword)
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("현재 비밀번호와")
    }

    @Test
    fun `changePassword() throws CoreException(BAD_REQUEST) when new password is too short`() {
        // Arrange
        val userId = "testUser"
        val oldPassword = "oldPassword123!"
        val tooShortPassword = "short12" // 7 chars
        userService.createUser(userId, oldPassword, "testName", LocalDate.now(), "test@email.com")

        // Act & Assert
        val exception = assertThrows<CoreException> {
            userService.changePassword(userId, oldPassword, tooShortPassword)
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("8~16자")
    }

    // ─── 경계값 테스트 ───

    @Test
    fun `createUser() should accept password with exactly 8 characters`() {
        // Arrange
        val minPassword = "pass1234" // exactly 8 chars

        // Act
        val user = userService.createUser("testUser", minPassword, "testName", LocalDate.now(), "test@email.com")

        // Assert
        assertThat(user).isNotNull
        assertThat(passwordEncoder.matches(minPassword, user.encryptedPassword)).isTrue()
    }

    @Test
    fun `createUser() should accept password with exactly 16 characters`() {
        // Arrange
        val maxPassword = "password12345678" // exactly 16 chars

        // Act
        val user = userService.createUser("testUser", maxPassword, "testName", LocalDate.now(), "test@email.com")

        // Assert
        assertThat(user).isNotNull
        assertThat(passwordEncoder.matches(maxPassword, user.encryptedPassword)).isTrue()
    }
}
