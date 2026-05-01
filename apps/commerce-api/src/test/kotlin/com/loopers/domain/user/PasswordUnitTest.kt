package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class PasswordUnitTest {

    private val defaultBirthDate = LocalDate.of(1990, 1, 1)

    // ─── 길이 검증 ───

    @Test
    fun `create() throws CoreException(BAD_REQUEST) when password is too short`() {
        // Arrange
        val shortPassword = "pass123" // 7 chars

        // Act
        val exception = assertThrows<CoreException> {
            Password.create(shortPassword, defaultBirthDate)
        }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("8~16자")
    }

    @Test
    fun `create() throws CoreException(BAD_REQUEST) when password is too long`() {
        // Arrange
        val longPassword = "password123456789!" // 18 chars

        // Act
        val exception = assertThrows<CoreException> {
            Password.create(longPassword, defaultBirthDate)
        }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("8~16자")
    }

    @Test
    fun `create() should accept password with exactly 8 characters`() {
        // Arrange & Act
        val password = assertDoesNotThrow {
            Password.create("pass1234", defaultBirthDate)
        }

        // Assert
        assertThat(password.value).isEqualTo("pass1234")
    }

    @Test
    fun `create() should accept password with exactly 16 characters`() {
        // Arrange & Act
        val password = assertDoesNotThrow {
            Password.create("password12345678", defaultBirthDate)
        }

        // Assert
        assertThat(password.value).isEqualTo("password12345678")
    }

    // ─── 포맷 검증 ───

    @Test
    fun `create() throws CoreException(BAD_REQUEST) when password contains invalid characters`() {
        // Arrange
        val passwordWithKorean = "비밀번호test123"

        // Act
        val exception = assertThrows<CoreException> {
            Password.create(passwordWithKorean, defaultBirthDate)
        }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("영문 대소문자")
    }

    @Test
    fun `create() should accept password with special characters`() {
        // Arrange & Act & Assert
        assertDoesNotThrow { Password.create("pass!@#\$", defaultBirthDate) }
        assertDoesNotThrow { Password.create("pass^&*(", defaultBirthDate) }
    }

    // ─── 생년월일 패턴 검증 ───

    @Test
    fun `create() throws CoreException(BAD_REQUEST) when password contains birthDate in yyyyMMdd format`() {
        // Arrange
        val birthDate = LocalDate.of(1990, 1, 1)

        // Act
        val exception = assertThrows<CoreException> {
            Password.create("pass19900101", birthDate)
        }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("생년월일")
    }

    @Test
    fun `create() throws CoreException(BAD_REQUEST) when password contains birthDate in yyMMdd format`() {
        // Arrange
        val birthDate = LocalDate.of(1990, 1, 1)

        // Act
        val exception = assertThrows<CoreException> {
            Password.create("pass900101abc", birthDate)
        }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("생년월일")
    }

    @Test
    fun `create() throws CoreException(BAD_REQUEST) when password contains birthDate in MMdd format`() {
        // Arrange
        val birthDate = LocalDate.of(1990, 1, 1)

        // Act
        val exception = assertThrows<CoreException> {
            Password.create("password0101", birthDate)
        }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("생년월일")
    }

    @Test
    fun `create() should accept password that does not contain birthDate`() {
        // Arrange
        val birthDate = LocalDate.of(1990, 1, 1)

        // Act & Assert
        assertDoesNotThrow {
            Password.create("safePassword!", birthDate)
        }
    }
}
