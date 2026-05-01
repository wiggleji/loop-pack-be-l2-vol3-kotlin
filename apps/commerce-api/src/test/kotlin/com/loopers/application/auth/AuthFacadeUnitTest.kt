package com.loopers.application.auth

import com.loopers.domain.user.User
import com.loopers.domain.user.UserPasswordEncoder
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDate

class AuthFacadeUnitTest {

    private val mockUserService = mockk<UserService>()
    private val mockPasswordEncoder = mockk<PasswordEncoder>()
    private val spyPasswordEncoder = spyk(UserPasswordEncoder())

    private val authFacade = AuthFacade(mockUserService, mockPasswordEncoder)
    private val authFacadeWithSpy = AuthFacade(mockUserService, spyPasswordEncoder)

    // ─── signup ───

    @Test
    fun `signup() should create user with valid data`() {
        // Arrange
        every { mockPasswordEncoder.encode(any()) } returns "hashedPassword"
        every { mockUserService.createUser(any(), any(), any(), any(), any()) } returns createUser(userId = "testUser")

        // Act
        val user = authFacade.signup("testUser", "password123!", "testName", LocalDate.of(1990, 1, 1), "test@email.com")

        // Assert
        assertThat(user.userId).isEqualTo("testUser")
        verify { mockPasswordEncoder.encode("password123!") }
        verify { mockUserService.createUser("testUser", "hashedPassword", "testName", LocalDate.of(1990, 1, 1), "test@email.com") }
    }

    @Test
    fun `signup() throws CoreException(BAD_REQUEST) when email format is invalid`() {
        // Act
        val exception = assertThrows<CoreException> {
            authFacade.signup("testUser", "password123!", "testName", LocalDate.of(1990, 1, 1), "invalid-email")
        }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("이메일")
    }

    @Test
    fun `signup() throws CoreException(BAD_REQUEST) when password is too short`() {
        // Act
        val exception = assertThrows<CoreException> {
            authFacade.signup("testUser", "pass123", "testName", LocalDate.of(1990, 1, 1), "test@email.com")
        }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("8~16자")
    }

    @Test
    fun `signup() throws CoreException(BAD_REQUEST) when password is too long`() {
        // Act
        val exception = assertThrows<CoreException> {
            authFacade.signup("testUser", "password123456789!", "testName", LocalDate.of(1990, 1, 1), "test@email.com")
        }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("8~16자")
    }

    @Test
    fun `signup() throws CoreException(BAD_REQUEST) when password contains birthDate`() {
        // Arrange
        val birthDate = LocalDate.of(1990, 1, 1)

        // Act
        val exception = assertThrows<CoreException> {
            authFacade.signup("testUser", "pass19900101", "testName", birthDate, "test@email.com")
        }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("생년월일")
    }

    @Test
    fun `signup() should accept valid email format`() {
        // Arrange
        every { mockPasswordEncoder.encode(any()) } returns "hashedPassword"
        every { mockUserService.createUser(any(), any(), any(), any(), any()) } returns createUser()

        // Act & Assert (no exception)
        authFacade.signup("testUser", "password123!", "testName", LocalDate.of(1990, 1, 1), "test@email.com")
    }

    @Test
    fun `signup() should accept password with exactly 8 characters`() {
        // Arrange
        every { mockPasswordEncoder.encode(any()) } returns "hashedPassword"
        every { mockUserService.createUser(any(), any(), any(), any(), any()) } returns createUser()

        // Act & Assert (no exception)
        authFacade.signup("testUser", "pass1234", "testName", LocalDate.of(1990, 1, 1), "test@email.com")
    }

    @Test
    fun `signup() should accept password with exactly 16 characters`() {
        // Arrange
        every { mockPasswordEncoder.encode(any()) } returns "hashedPassword"
        every { mockUserService.createUser(any(), any(), any(), any(), any()) } returns createUser()

        // Act & Assert (no exception)
        authFacade.signup("testUser", "password12345678", "testName", LocalDate.of(1990, 1, 1), "test@email.com")
    }

    // ─── authenticate ───

    @Test
    fun `authenticate() returns user with correct credentials`() {
        // Arrange
        val user = createUser(userId = "testUser", encryptedPassword = "hashedPassword")
        every { mockUserService.findByUserId("testUser") } returns user
        every { mockPasswordEncoder.matches("password123!", "hashedPassword") } returns true

        // Act
        val result = authFacade.authenticate("testUser", "password123!")

        // Assert
        assertThat(result.userId).isEqualTo("testUser")
    }

    @Test
    fun `authenticate() throws exception with wrong password`() {
        // Arrange
        val user = createUser(userId = "testId", encryptedPassword = "hashedPassword")
        every { mockUserService.findByUserId("testId") } returns user
        every { spyPasswordEncoder.matches("wrongPassword", "hashedPassword") } returns false

        // Act & Assert
        assertThrows<CoreException> {
            authFacadeWithSpy.authenticate("testId", "wrongPassword")
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }
        verify { spyPasswordEncoder.matches("wrongPassword", "hashedPassword") }
    }

    @Test
    fun `authenticate() throws exception with non-existing User`() {
        // Arrange
        every { mockUserService.findByUserId("nonExistUser") } returns null
        every { mockPasswordEncoder.matches("wrongPassword", "\\\$2a\\\$10\\\$dummyHashForTimingAttackPrevention") } returns false

        // Act & Assert
        assertThrows<CoreException> {
            authFacade.authenticate("nonExistUser", "wrongPassword")
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }
    }

    @Test
    fun `authenticate() should call matches() even when user does not exist to prevent timing attack`() {
        // Arrange
        every { mockUserService.findByUserId("nonExistUser") } returns null

        // Act
        assertThrows<CoreException> {
            authFacadeWithSpy.authenticate("nonExistUser", "anyPassword")
        }

        // Assert - verify matches() was called even though user doesn't exist
        verify(exactly = 1) { spyPasswordEncoder.matches("anyPassword", any()) }
    }

    // ─── changePassword ───

    @Test
    fun `changePassword() should update password successfully`() {
        // Arrange
        val user = createUser(userId = "testUser", encryptedPassword = "hashedOldPassword")
        every { mockUserService.getUserByUserId("testUser") } returns user
        every { mockPasswordEncoder.matches("oldPassword", "hashedOldPassword") } returns true
        every { mockPasswordEncoder.matches("newPassword123!", "hashedOldPassword") } returns false
        every { mockPasswordEncoder.encode("newPassword123!") } returns "hashedNewPassword"
        every { mockUserService.save(any()) } returns user

        // Act
        authFacade.changePassword("testUser", "oldPassword", "newPassword123!")

        // Assert
        verify { mockPasswordEncoder.encode("newPassword123!") }
        verify { mockUserService.save(any()) }
    }

    @Test
    fun `changePassword() throws CoreException when old password is wrong`() {
        // Arrange
        val user = createUser(userId = "testUser", encryptedPassword = "hashedPassword")
        every { mockUserService.getUserByUserId("testUser") } returns user
        every { mockPasswordEncoder.matches("wrongOldPassword", "hashedPassword") } returns false

        // Act & Assert
        assertThrows<CoreException> {
            authFacade.changePassword("testUser", "wrongOldPassword", "newPassword123!")
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
            assertThat(it.message).contains("기존 비밀번호")
        }
        verify(exactly = 0) { mockPasswordEncoder.encode(any()) }
    }

    @Test
    fun `changePassword() throws CoreException when new password is too short`() {
        // Arrange
        val user = createUser(userId = "testUser", encryptedPassword = "hashedPassword")
        every { mockUserService.getUserByUserId("testUser") } returns user
        every { mockPasswordEncoder.matches("testPassword", "hashedPassword") } returns true

        // Act & Assert
        assertThrows<CoreException> {
            authFacade.changePassword("testUser", "testPassword", "pass123") // 7 chars
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(it.message).contains("8~16자")
        }
    }

    @Test
    fun `changePassword() throws CoreException when new password contains birthDate`() {
        // Arrange
        val birthDate = LocalDate.of(1990, 1, 1)
        val user = createUser(userId = "testUser", encryptedPassword = "hashedPassword", birthDate = birthDate)
        every { mockUserService.getUserByUserId("testUser") } returns user
        every { mockPasswordEncoder.matches("testPassword", "hashedPassword") } returns true

        // Act & Assert
        assertThrows<CoreException> {
            authFacade.changePassword("testUser", "testPassword", "pass19900101")
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(it.message).contains("생년월일")
        }
    }

    @Test
    fun `changePassword() throws CoreException when new password is same as current`() {
        // Arrange
        val user = createUser(userId = "testUser", encryptedPassword = "hashedPassword")
        every { mockUserService.getUserByUserId("testUser") } returns user
        every { mockPasswordEncoder.matches("testPassword", "hashedPassword") } returns true // both old and same check

        // Act & Assert
        assertThrows<CoreException> {
            authFacade.changePassword("testUser", "testPassword", "testPassword")
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(it.message).contains("현재 비밀번호와 동일")
        }
    }

    private fun createUser(
        userId: String = "testUser",
        encryptedPassword: String = "hashedPassword",
        name: String = "testName",
        birthDate: LocalDate = LocalDate.of(2026, 1, 1),
        email: String = "test@email.com",
    ): User {
        return User(
            userId = userId,
            encryptedPassword = encryptedPassword,
            name = name,
            birthDate = birthDate,
            email = email,
        )
    }
}
