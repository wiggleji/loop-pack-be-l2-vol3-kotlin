package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class EmailUnitTest {

    @Test
    fun `Email() should create with valid email format`() {
        // Arrange & Act
        val email = assertDoesNotThrow { Email("test@email.com") }

        // Assert
        assertThat(email.value).isEqualTo("test@email.com")
    }

    @Test
    fun `Email() should accept email with special characters before @`() {
        // Arrange & Act & Assert
        assertDoesNotThrow { Email("test+tag@email.com") }
        assertDoesNotThrow { Email("test.name@email.com") }
        assertDoesNotThrow { Email("test_name@email.com") }
    }

    @Test
    fun `Email() throws CoreException(BAD_REQUEST) when email has no @ symbol`() {
        // Arrange & Act
        val exception = assertThrows<CoreException> { Email("invalid-email-format") }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("이메일")
    }

    @Test
    fun `Email() throws CoreException(BAD_REQUEST) when email has no domain`() {
        // Arrange & Act
        val exception = assertThrows<CoreException> { Email("test@") }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @Test
    fun `Email() throws CoreException(BAD_REQUEST) when email has no local part`() {
        // Arrange & Act
        val exception = assertThrows<CoreException> { Email("@domain.com") }

        // Assert
        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }
}
