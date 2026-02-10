package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDate

class UserServiceUnitTest {

    // Setup
    private val mockRepository = mockk<UserRepository>()
    private val mockPasswordEncoder = mockk<PasswordEncoder>()
    private val spyPasswordEncoder = spyk(UserPasswordEncoder()) // Use real instance for spy

    private val userService = UserService(mockRepository, mockPasswordEncoder)
    private val userServiceWithSpy = UserService(mockRepository, spyPasswordEncoder)

    @Test
    fun `createUser() throws CoreException(CONFLICT) when userId already exists`() {
        // Arrange
        val userId = "duplicateUser"
        every { mockRepository.existsByUserId(userId) } returns true

        // Act
        val exception = assertThrows<CoreException> {
            userService.createUser(userId, "password123!", "User2", LocalDate.now(),
                "duplicateUser@example.com")
        }

        // Assert
        verify(exactly = 0) { mockRepository.save(any()) }
        assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
    }

    // ─── createUser — userId 유효성 ───

    @Test
    fun `createUser() throws CoreException(BAD_REQUEST) when userId contains special characters`() {
        // Arrange
        every { mockRepository.existsByUserId(any()) } returns false

        // Act
        val exception = assertThrows<CoreException> {
            userService.createUser("user!@#", "password123!", "testName", LocalDate.of(1990, 1, 1),
                "test@example.com")
        }

        // Assert
        verify(exactly = 0) { mockRepository.save(any()) }
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("영문")
    }

    @Test
    fun `createUser() throws CoreException(BAD_REQUEST) when userId contains Korean characters`() {
        // Arrange
        every { mockRepository.existsByUserId(any()) } returns false

        // Act
        val exception = assertThrows<CoreException> {
            userService.createUser("테스트유저", "password123!", "testName", LocalDate.of(1990, 1, 1),
                "test@example.com")
        }

        // Assert
        verify(exactly = 0) { mockRepository.save(any()) }
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("영문")
    }

    @Test
    fun `createUser() should accept userId with only alphanumeric characters`() {
        // Arrange
        every { mockRepository.existsByUserId(any()) } returns false
        every { mockPasswordEncoder.encode(any()) } returns "hashedPassword"
        every { mockRepository.save(any()) } returns createMockUser(userId = "testUser123")

        // Act
        val user = userService.createUser(
            "testUser123", "password123!", "testName", LocalDate.of(1990, 1, 1),
            "test@example.com"
        )

        // Assert
        assertThat(user.userId).isEqualTo("testUser123")
    }

    // ─── createUser — birthDate 유효성 ───

    @Test
    fun `createUser() throws CoreException(BAD_REQUEST) when birthDate is in the future`() {
        // Arrange
        val futureBirthDate = LocalDate.now().plusDays(1)
        every { mockRepository.existsByUserId(any()) } returns false

        // Act
        val exception = assertThrows<CoreException> {
            userService.createUser("testUser", "password123!", "홍길동", futureBirthDate,
                "test@example.com")
        }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("생년월일")
        assertThat(exception.message).contains("미래")
        verify { mockRepository.save(any()) wasNot Called }
    }

    @Test
    fun `createUser() should accept birthDate as today`() {
        // Arrange
        val today = LocalDate.now()
        every { mockRepository.existsByUserId(any()) } returns false
        every { mockPasswordEncoder.encode(any()) } returns "hashedPassword"
        every { mockRepository.save(any()) } returns createMockUser(userId = "testId", encryptedPassword = "hashedPassword", birthDate = today)

        // Act
        val user = userService.createUser("testUser", "password123!", "홍길동", today,
            "test@example.com")

        // Assert
        assertThat(user).isNotNull
        assertThat(user.birthDate).isEqualTo(today)
    }

    @Test
    fun `createUser() should accept birthDate in the past`() {
        // Arrange
        val birthDate = LocalDate.of(2026, 1, 1)
        every { mockRepository.existsByUserId(any()) } returns false
        every { mockPasswordEncoder.encode(any()) } returns "hashedPassword"
        every { mockRepository.save(any()) } returns createMockUser(userId = "testId", encryptedPassword = "hashedPassword", birthDate = birthDate)

        // Act
        val user = userService.createUser("testUser", "password123!", "홍길동", birthDate,
            "test@example.com")

        // Assert
        assertThat(user).isNotNull
        assertThat(user.birthDate).isEqualTo(birthDate)
    }

    // ─── createUser — password 유효성 ───

    @Test
    fun `createUser() throws CoreException(BAD_REQUEST) when password is too short`() {
        // Arrange
        val shortPassword = "pass123" // 7 chars (< 8)
        every { mockRepository.existsByUserId(any()) } returns false

        // Act
        val exception = assertThrows<CoreException> {
            userService.createUser("testUser", shortPassword, "홍길동", LocalDate.now(),
                "test@example.com")
        }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("8~16자")
    }

    @Test
    fun `createUser() throws CoreException when password is too long`() {
        // Arrange
        val shortPassword = "password123456789!" // 18 chars (> 16)
        every { mockRepository.existsByUserId(any()) } returns false

        // Act
        val exception = assertThrows<CoreException> {
            userService.createUser("testUser", shortPassword, "홍길동", LocalDate.now(),
                "test@example.com")
        }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("8~16자")
    }

    @Test
    fun `createUser() accept with exact 8 characters`() {
        // Arrange
        val shortPassword = "pass1234" // 8 chars
        every { mockRepository.existsByUserId(any()) } returns false
        every { mockPasswordEncoder.encode(any()) } returns "hashedPassword"
        every { mockRepository.save(any()) } returns createMockUser(userId = "testId", encryptedPassword = "hashedPassword")

        // Act
        val user = userService.createUser(
            "testUser", shortPassword, "홍길동", LocalDate.now(),
            "test@example.com"
        )

        // Assert
        assertThat(user).isNotNull
    }

    @Test
    fun `createUser() accept with exact 16 characters`() {
        // Arrange
        val password = "password12345678" // 16 chars
        every { mockRepository.existsByUserId(any()) } returns false
        every { mockPasswordEncoder.encode(any()) } returns "hashedPassword"
        every { mockRepository.save(any()) } returns createMockUser(userId = "testId", encryptedPassword = "hashedPassword")

        // Act
        val user = userService.createUser(
            "testUser", password, "testName", LocalDate.now(),
            "test@example.com"
        )

        // Assert
        assertThat(user).isNotNull
    }

    @Test
    fun `createUser() throws CoreException when password contains birthDate in yyyyMMdd format`() {
        // Arrange
        val birthDate = LocalDate.of(1990, 1, 1)
        val password = "pass19900101" // contains 19900101
        every { mockRepository.existsByUserId(any()) } returns false

        // Act
        val exception = assertThrows<CoreException> {
            userService.createUser("testUser", password, "testName", birthDate,
                "test@example.com")
        }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("생년월일")
    }

    @Test
    fun `createUser() throws CoreException when password contains birthDate in yyMMdd format`() {
        // Arrange
        val birthDate = LocalDate.of(1990, 1, 1)
        val password = "pass900101abc" // contains 900101
        every { mockRepository.existsByUserId(any()) } returns false

        // Act
        val exception = assertThrows<CoreException> {
            userService.createUser("testUser", password, "testName", birthDate,
                "test@example.com")
        }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("생년월일")
    }

    @Test
    fun `createUser() throws CoreException when password contains birthDate in MMdd format`() {
        // Arrange
        val birthDate = LocalDate.of(1990, 1, 1)
        val password = "password0101" // contains 0101
        every { mockRepository.existsByUserId(any()) } returns false

        // Act
        val exception = assertThrows<CoreException> {
            userService.createUser("testUser", password, "testName", birthDate,
                "test@example.com")
        }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("생년월일")
    }

    @Test
    fun `createUser() should accept password that does not contain birthDate`() {
        // Arrange
        val birthDate = LocalDate.of(1990, 1, 1)
        val password = "safePassword!" // does not contain any birthDate format
        every { mockRepository.existsByUserId(any()) } returns false
        every { mockPasswordEncoder.encode(any()) } returns "hashedPassword"
        every { mockRepository.save(any()) } returns createMockUser(userId = "testId", encryptedPassword = "hashedPassword")

        // Act
        val user = userService.createUser("testUser", password, "testName", birthDate,
            "test@example.com")

        // Assert
        assertThat(user).isNotNull
    }

    @Test
    fun `createUser() throws CoreException when email format is invalid`() {
        // Arrange
        val invalidEmail = "invalid-email-format" // no @
        every { mockRepository.existsByUserId(any()) } returns false

        // Act
        val exception = assertThrows<CoreException> {
            userService.createUser("testUser", "password123!", "testName", LocalDate.now(),
                invalidEmail)
        }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("이메일")
    }

    @Test
    fun `createUser() should accept valid email format`() {
        // Arrange
        val validEmail = "test@email.com"
        every { mockRepository.existsByUserId(any()) } returns false
        every { mockPasswordEncoder.encode(any()) } returns "hashedPassword"
        every { mockRepository.save(any()) } returns createMockUser(userId = "testId", encryptedPassword = "hashedPassword")

        // Act
        val user = userService.createUser("testUser", "password123!", "testName",
            LocalDate.now(), validEmail)

        // Assert
        assertThat(user.email).isEqualTo(validEmail)
    }

    @Test
    fun `getUserByUserId() throws CoreException when User is not found`() {
        // Arrange
        every { mockRepository.findByUserId("nonExistentUser") } returns null

        // Act

        // Assert
        assertThrows<CoreException> {
            userService.getUserByUserId("nonExistentUser")
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @Test
    fun `authenticate() throws exception with wrong password`() {
        // Arrange
        val userId = "testId"
        val existingUser = createMockUser(userId = userId, encryptedPassword = "hashedPassword")
        every { mockRepository.findByUserId(userId) } returns existingUser
        every { spyPasswordEncoder.matches("wrongPassword", "hashedPassword") } returns false

        // Act

        // Assert
        assertThrows<CoreException> {
            userServiceWithSpy.authenticate(userId, "wrongPassword")
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }
        verify { spyPasswordEncoder.matches("wrongPassword", "hashedPassword") }
    }

    @Test
    fun `authenticate() throws exception with non-existing User`() {
        // Arrange
        every { mockRepository.findByUserId("nonExistUser") } returns null
        every { mockPasswordEncoder.matches("wrongPassword", "\\\$2a\\\$10\\\$dummyHashForTimingAttackPrevention") } returns false

        // Act

        // Assert
        assertThrows<CoreException> {
            userService.authenticate("nonExistUser", "wrongPassword")
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }
    }

    @Test
    fun `authenticate() should call matches() even when user does not exist to prevent timing attack`() {
        // Arrange
        every { mockRepository.findByUserId("nonExistUser") } returns null

        // Act
        assertThrows<CoreException> {
            userServiceWithSpy.authenticate("nonExistUser", "anyPassword")
        }

        // Assert - verify matches() was called even though user doesn't exist
        verify(exactly = 1) { spyPasswordEncoder.matches("anyPassword", any()) }
    }

    // ─── changePassword ───

    @Test
    fun `changePassword() should update password successfully`() {
        // Arrange
        val userId = "testUser"
        val existingUser = createMockUser(userId = userId, encryptedPassword = "hashedOldPassword")
        every { mockRepository.findByUserId(userId) } returns existingUser
        every { mockPasswordEncoder.matches("oldPassword", "hashedOldPassword") } returns true   // 기존 비밀번호 확인
        every { mockPasswordEncoder.matches("newPassword123!", "hashedOldPassword") } returns false // 현재와 다름
        every { mockPasswordEncoder.encode("newPassword123!") } returns "hashedNewPassword"
        every { mockRepository.save(any()) } returns existingUser

        // Act
        userService.changePassword(userId, "oldPassword", "newPassword123!")

        // Assert
        verify { mockPasswordEncoder.encode("newPassword123!") }
        verify { mockRepository.save(any()) }
    }

    @Test
    fun `changePassword() throws CoreException when old password is wrong`() {
        // Arrange
        val userId = "testUser"
        val existingUser = createMockUser(userId = userId, encryptedPassword = "hashedPassword")
        every { mockRepository.findByUserId(userId) } returns existingUser
        every { mockPasswordEncoder.matches("wrongOldPassword", "hashedPassword") } returns false

        // Act & Assert
        assertThrows<CoreException> {
            userService.changePassword(userId, "wrongOldPassword", "newPassword123!")
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
            assertThat(it.message).contains("기존 비밀번호")
        }
        verify(exactly = 0) { mockPasswordEncoder.encode(any()) }
    }

    @Test
    fun `changePassword() throws CoreException when new password is too short`() {
        // Arrange
        val userId = "testUser"
        val existingUser = createMockUser(userId = userId, encryptedPassword = "hashedPassword")
        every { mockRepository.findByUserId(userId) } returns existingUser
        every { mockPasswordEncoder.matches("testPassword", "hashedPassword") } returns true

        // Act & Assert
        assertThrows<CoreException> {
            userService.changePassword(userId, "testPassword", "pass123") // 7 chars
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(it.message).contains("8~16자")
        }
    }

    @Test
    fun `changePassword() throws CoreException when new password contains birthDate`() {
        // Arrange
        val userId = "testUser"
        val birthDate = LocalDate.of(1990, 1, 1)
        val existingUser = createMockUser(userId = userId, encryptedPassword = "hashedPassword", birthDate = birthDate)
        every { mockRepository.findByUserId(userId) } returns existingUser
        every { mockPasswordEncoder.matches("testPassword", "hashedPassword") } returns true

        // Act & Assert
        assertThrows<CoreException> {
            userService.changePassword(userId, "testPassword", "pass19900101")
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(it.message).contains("생년월일")
        }
    }

    @Test
    fun `changePassword() throws CoreException when new password is same as current`() {
        // Arrange
        val userId = "testUser"
        val existingUser = createMockUser(userId = userId, encryptedPassword = "hashedPassword")
        every { mockRepository.findByUserId(userId) } returns existingUser
        every { mockPasswordEncoder.matches("testPassword", "hashedPassword") } returns true // 기존 확인 + 동일 확인 모두 true

        // Act & Assert
        assertThrows<CoreException> {
            userService.changePassword(userId, "testPassword", "testPassword")
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(it.message).contains("현재 비밀번호와 동일")
        }
    }

    private fun createMockUser(
        userId: String = "testUser",
        encryptedPassword: String = "hashedPassword",
        name: String = "testName",
        birthDate: LocalDate = LocalDate.of(2026, 1, 1),
        email: String = "test@email.com"
    ): UserModel {
        return UserModel(
            userId = userId,
            encryptedPassword = encryptedPassword,
            name = name,
            birthDate = birthDate,
            email = email
        )
    }
}
