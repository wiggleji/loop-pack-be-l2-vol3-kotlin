package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate


class UserModelTest {

    @Nested
    @DisplayName("UserModel 초기화 시")
    inner class Initialize {
        @Test
        @DisplayName("ID/PW/이름/생년월일/이메일로 UserModel 을 정상적으로 생성한다.")
        fun initializeUserModel_whenIdPasswordNameBirthDateEmail() {
            // Arrange
            val userId = "testId"
            val password = "testPassword"
            val name = "testUserName"
            val birthDate = LocalDate.now()
            val email = "testEmail@example.com"

            // Act
            val userModel: UserModel = UserModel(
                userId = userId,
                encryptedPassword = password,
                name = name,
                birthDate = birthDate,
                email = email
            )

            // Assert
            assertEquals(userId, userModel.userId)
            assertEquals(name, userModel.name)
            assertEquals(email, userModel.email)
        }
        
        @Test
        fun throwsBadRequestException_whenPrameterIsBlank() {
            // Arrange
            val userId = " "
            val password = "testPassword"
            val name = "testUserName"
            val birthDate = LocalDate.now()
            val email = "testEmail@example.com"

            // Act
            val noId = assertThrows<CoreException> {
                UserModel(
                    userId = userId,
                    encryptedPassword = password,
                    name = name,
                    birthDate = birthDate,
                    email = email
                )
            }
            
            // Assert
            assertThat(noId.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Nested
    @DisplayName("updatePassword() 메서드 실행 시")
    inner class UpdatePassword {

        @Test
        @DisplayName("빈 문자열을 전달하면 CoreException(BAD_REQUEST)을 발생시킨다")
        fun throwsBadRequestException_whenNewPasswordIsBlank() {
            // Arrange
            val userModel = UserModel(
                userId = "testId",
                encryptedPassword = "oldEncryptedPassword",
                name = "testName",
                birthDate = LocalDate.now(),
                email = "test@example.com"
            )

            // Act & Assert - blank string
            val blankException = assertThrows<CoreException> {
                userModel.updatePassword("")
            }
            assertThat(blankException.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(blankException.message).contains("암호")

            // Act & Assert - whitespace string
            val whitespaceException = assertThrows<CoreException> {
                userModel.updatePassword("   ")
            }
            assertThat(whitespaceException.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(whitespaceException.message).contains("암호")
        }

        @Test
        @DisplayName("유효한 암호화된 비밀번호를 전달하면 정상적으로 업데이트한다")
        fun updatesPassword_whenNewPasswordIsValid() {
            // Arrange
            val userModel = UserModel(
                userId = "testId",
                encryptedPassword = "oldEncryptedPassword",
                name = "testName",
                birthDate = LocalDate.now(),
                email = "test@example.com"
            )
            val newEncryptedPassword = "newEncryptedPassword"

            // Act
            userModel.updatePassword(newEncryptedPassword)

            // Assert
            assertThat(userModel.encryptedPassword).isEqualTo(newEncryptedPassword)
        }
    }
}
