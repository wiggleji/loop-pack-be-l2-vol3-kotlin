package com.loopers.domain.user

import com.loopers.infrastructure.user.UserEntity
import com.loopers.infrastructure.user.UserJpaRepository
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
    private val userJpaRepository: UserJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun `createUser() should create user with valid data`() {
        // Arrange
        val encryptedPassword = passwordEncoder.encode("testPassword")

        // Act
        val user = userService.createUser("testId", encryptedPassword, "testName", LocalDate.now(), "test@email.com")

        // Assert
        assertThat(user.id).isGreaterThan(0)
        assertThat(user.userId).isEqualTo("testId")
        assertThat(user.name).isEqualTo("testName")
        assertThat(user.email).isEqualTo("test@email.com")
    }

    @Test
    fun `getUserByUserId() returns User`() {
        // Arrange
        val encryptedPassword = passwordEncoder.encode("testPassword")
        userService.createUser("testId", encryptedPassword, "testName", LocalDate.now(), "test@email.com")

        // Act
        val userFound = userService.getUserByUserId("testId")

        // Assert
        assertThat(userFound).isNotNull
        assertThat(userFound.userId).isEqualTo("testId")
    }

    @Test
    fun `findByUserId() returns null when user does not exist`() {
        // Act
        val result = userService.findByUserId("nonExistentUser")

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `save() persists user changes`() {
        // Arrange
        val encryptedPassword = passwordEncoder.encode("testPassword")
        val user = userService.createUser("testId", encryptedPassword, "testName", LocalDate.now(), "test@email.com")

        // Act
        val newEncryptedPassword = passwordEncoder.encode("newPassword")
        user.updatePassword(newEncryptedPassword)
        userService.save(user)

        // Assert
        val updated = userService.getUserByUserId("testId")
        assertThat(passwordEncoder.matches("newPassword", updated.encryptedPassword)).isTrue()
    }

    // ─── 예외 케이스 ───

    @Test
    fun `createUser() throws CoreException(CONFLICT) when userId already exists`() {
        // Arrange
        val encryptedPassword = passwordEncoder.encode("password123!")
        userService.createUser("duplicateUser", encryptedPassword, "firstName", LocalDate.of(1990, 1, 1), "test@email.com")

        // Act & Assert
        val exception = assertThrows<CoreException> {
            userService.createUser("duplicateUser", encryptedPassword, "secondName", LocalDate.of(1991, 2, 2), "other@email.com")
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
        assertThat(exception.message).contains("duplicateUser")
    }

    @Test
    fun `createUser() throws CoreException(BAD_REQUEST) when userId contains special characters`() {
        // Arrange & Act & Assert
        val exception = assertThrows<CoreException> {
            userService.createUser("user!@#", "hashedPassword", "testName", LocalDate.now(), "test@email.com")
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("ID")
    }

    @Test
    fun `createUser() throws CoreException(BAD_REQUEST) when birthDate is in the future`() {
        // Arrange
        val futureBirthDate = LocalDate.now().plusDays(1)

        // Act & Assert
        val exception = assertThrows<CoreException> {
            userService.createUser("testUser", "hashedPassword", "testName", futureBirthDate, "test@email.com")
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("생년월일")
        assertThat(exception.message).contains("미래")
    }

    @Test
    fun `getUserByUserId() throws CoreException(NOT_FOUND) when user does not exist`() {
        // Act & Assert
        val exception = assertThrows<CoreException> {
            userService.getUserByUserId("nonExistentUser")
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        assertThat(exception.message).contains("nonExistentUser")
    }
}
