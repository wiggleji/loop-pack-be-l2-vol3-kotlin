package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class UserServiceUnitTest {

    private val mockRepository = mockk<UserRepository>()
    private val userService = UserService(mockRepository)

    // ─── createUser ───

    @Test
    fun `createUser() should create user with valid data`() {
        // Arrange
        every { mockRepository.existsByUserId(any()) } returns false
        every { mockRepository.save(any()) } returns createUser(userId = "testUser123")

        // Act
        val user = userService.createUser(
            "testUser123", "hashedPassword", "testName", LocalDate.of(1990, 1, 1), "test@example.com"
        )

        // Assert
        assertThat(user.userId).isEqualTo("testUser123")
    }

    @Test
    fun `createUser() throws CoreException(CONFLICT) when userId already exists`() {
        // Arrange
        every { mockRepository.existsByUserId("duplicateUser") } returns true

        // Act
        val exception = assertThrows<CoreException> {
            userService.createUser("duplicateUser", "hashedPassword", "User2", LocalDate.now(), "test@example.com")
        }

        // Assert
        verify(exactly = 0) { mockRepository.save(any()) }
        assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
    }

    // ─── userId 유효성 ───

    @Test
    fun `createUser() throws CoreException(BAD_REQUEST) when userId contains special characters`() {
        // Arrange
        every { mockRepository.existsByUserId(any()) } returns false

        // Act
        val exception = assertThrows<CoreException> {
            userService.createUser("user!@#", "hashedPassword", "testName", LocalDate.of(1990, 1, 1), "test@example.com")
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
            userService.createUser("테스트유저", "hashedPassword", "testName", LocalDate.of(1990, 1, 1), "test@example.com")
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
        every { mockRepository.save(any()) } returns createUser(userId = "testUser123")

        // Act
        val user = userService.createUser(
            "testUser123", "hashedPassword", "testName", LocalDate.of(1990, 1, 1), "test@example.com"
        )

        // Assert
        assertThat(user.userId).isEqualTo("testUser123")
    }

    // ─── birthDate 유효성 ───

    @Test
    fun `createUser() throws CoreException(BAD_REQUEST) when birthDate is in the future`() {
        // Arrange
        val futureBirthDate = LocalDate.now().plusDays(1)
        every { mockRepository.existsByUserId(any()) } returns false

        // Act
        val exception = assertThrows<CoreException> {
            userService.createUser("testUser", "hashedPassword", "홍길동", futureBirthDate, "test@example.com")
        }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("생년월일")
        assertThat(exception.message).contains("미래")
    }

    @Test
    fun `createUser() should accept birthDate as today`() {
        // Arrange
        val today = LocalDate.now()
        every { mockRepository.existsByUserId(any()) } returns false
        every { mockRepository.save(any()) } returns createUser(birthDate = today)

        // Act
        val user = userService.createUser("testUser", "hashedPassword", "홍길동", today, "test@example.com")

        // Assert
        assertThat(user).isNotNull
        assertThat(user.birthDate).isEqualTo(today)
    }

    @Test
    fun `createUser() should accept birthDate in the past`() {
        // Arrange
        val birthDate = LocalDate.of(2026, 1, 1)
        every { mockRepository.existsByUserId(any()) } returns false
        every { mockRepository.save(any()) } returns createUser(birthDate = birthDate)

        // Act
        val user = userService.createUser("testUser", "hashedPassword", "홍길동", birthDate, "test@example.com")

        // Assert
        assertThat(user).isNotNull
        assertThat(user.birthDate).isEqualTo(birthDate)
    }

    // ─── getUserByUserId ───

    @Test
    fun `getUserByUserId() throws CoreException when User is not found`() {
        // Arrange
        every { mockRepository.findByUserId("nonExistentUser") } returns null

        // Act & Assert
        assertThrows<CoreException> {
            userService.getUserByUserId("nonExistentUser")
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    // ─── findByUserId ───

    @Test
    fun `findByUserId() returns null when user does not exist`() {
        // Arrange
        every { mockRepository.findByUserId("nonExistent") } returns null

        // Act
        val result = userService.findByUserId("nonExistent")

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `findByUserId() returns user when exists`() {
        // Arrange
        val user = createUser(userId = "testUser")
        every { mockRepository.findByUserId("testUser") } returns user

        // Act
        val result = userService.findByUserId("testUser")

        // Assert
        assertThat(result).isNotNull
        assertThat(result?.userId).isEqualTo("testUser")
    }

    // ─── save ───

    @Test
    fun `save() delegates to repository`() {
        // Arrange
        val user = createUser()
        every { mockRepository.save(any()) } returns user

        // Act
        val result = userService.save(user)

        // Assert
        verify { mockRepository.save(user) }
        assertThat(result.userId).isEqualTo(user.userId)
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
