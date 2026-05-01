package com.loopers.domain.user

import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

@SpringBootTest
@ActiveProfiles("test")
class UserRepositoryTest @Autowired constructor(
    private val userRepository: UserRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun findByUserId_returnsUserByUserId() {
        // Arrange
        val user = User(
            userId = "testId",
            encryptedPassword = "testPassword",
            name = "testName",
            birthDate = LocalDate.now(),
            email = "test@email.com",
        )

        // Act
        val saved = userRepository.save(user)
        val fetch = userRepository.findByUserId(saved.userId)

        // Assert
        assertThat(fetch).isNotNull
        assertThat(fetch?.userId).isEqualTo(saved.userId)
        assertThat(fetch?.email).isEqualTo(saved.email)
    }

    @Test
    fun findByUserId_returnsNullWhenUserNotFound() {
        // Act
        val notFound = userRepository.findByUserId("user_not_found")

        // Assert
        assertThat(notFound).isNull()
    }

    @Test
    fun existsByUserId_returnsTrueIfUserIdExists() {
        // Arrange
        userRepository.save(
            User(
                userId = "testId",
                encryptedPassword = "testPassword",
                name = "testName",
                birthDate = LocalDate.now(),
                email = "test@email.com",
            )
        )

        // Act
        val existsByUserId = userRepository.existsByUserId(userId = "testId")

        // Assert
        assertThat(existsByUserId).isTrue()
    }

    @Test
    fun existsByUserId_returnsFalseIfUserIdDoesNotExist() {
        // Act
        val existsByUserId = userRepository.existsByUserId(userId = "nonExistentUser")

        // Assert
        assertThat(existsByUserId).isFalse()
    }
}
