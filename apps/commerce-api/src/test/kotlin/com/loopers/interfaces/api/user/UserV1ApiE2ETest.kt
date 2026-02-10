package com.loopers.interfaces.api.user

import com.loopers.domain.user.UserModel
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.*
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userJpaRepository: UserJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT_SIGNUP = "/api/v1/users/signup"
        private const val ENDPOINT_ME = "/api/v1/users/me"
        private const val ENDPOINT_PASSWORD = "/api/v1/users/password"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    // ─── Helpers ───

    private fun loginHeaders(userId: String = "testUser", password: String = "testPassword"): HttpHeaders {
        return HttpHeaders().apply {
            this["X-Loopers-LoginId"] = userId
            this["X-Loopers-LoginPw"] = password
        }
    }

    private fun setupUser(
        userId: String = "testUser",
        password: String = "testPassword",
        name: String = "testName",
        birthDate: LocalDate = LocalDate.of(1990, 1, 1),
        email: String = "test@email.com",
    ): UserModel {
        return userJpaRepository.save(
            UserModel(
                userId = userId,
                encryptedPassword = passwordEncoder.encode(password),
                name = name,
                birthDate = birthDate,
                email = email,
            )
        )
    }

    // ─── POST /api/v1/users/signup ───

    @DisplayName("POST /api/v1/users/signup")
    @Nested
    inner class Signup {

        @DisplayName("유효한 정보로 회원가입하면, 200 과 사용자 정보를 반환한다.")
        @Test
        fun returnsUserInfo_whenValidDataIsProvided() {
            // arrange
            val body = mapOf(
                "userId" to "newUser",
                "password" to "password123!",
                "name" to "testName",
                "birthDate" to "1990-01-01",
                "email" to "test@email.com",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
            val response = testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(body), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data?.get("userId")).isEqualTo("newUser") },
                { assertThat(response.body?.data?.get("name")).isEqualTo("testName") },
                { assertThat(response.body?.data?.get("email")).isEqualTo("test@email.com") },
                { assertThat(response.body?.data?.containsKey("password")).isFalse() },
                { assertThat(response.body?.data?.containsKey("encryptedPassword")).isFalse() },
            )
        }

        @DisplayName("이미 존재하는 userId 로 회원가입하면, 409 CONFLICT 응답을 받는다.")
        @Test
        fun throwsConflict_whenUserIdAlreadyExists() {
            // arrange
            setupUser(userId = "existingUser")
            val body = mapOf(
                "userId" to "existingUser",
                "password" to "password123!",
                "name" to "testName",
                "birthDate" to "1990-01-01",
                "email" to "test@email.com",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(body), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("특수문자를 포함한 userId 로 회원가입하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun throwsBadRequest_whenUserIdContainsSpecialCharacters() {
            // arrange
            val body = mapOf(
                "userId" to "user!@#",
                "password" to "password123!",
                "name" to "testName",
                "birthDate" to "1990-01-01",
                "email" to "test@email.com",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(body), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("유효하지 않은 이메일 형식으로 회원가입하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun throwsBadRequest_whenEmailFormatIsInvalid() {
            // arrange
            val body = mapOf(
                "userId" to "newUser",
                "password" to "password123!",
                "name" to "testName",
                "birthDate" to "1990-01-01",
                "email" to "invalid-email",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(body), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("8자 미만의 비밀번호로 회원가입하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun throwsBadRequest_whenPasswordIsTooShort() {
            // arrange
            val body = mapOf(
                "userId" to "newUser",
                "password" to "pass123", // 7 chars
                "name" to "testName",
                "birthDate" to "1990-01-01",
                "email" to "test@email.com",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(body), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("생년월일을 포함한 비밀번호로 회원가입하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun throwsBadRequest_whenPasswordContainsBirthDate() {
            // arrange
            val body = mapOf(
                "userId" to "newUser",
                "password" to "pass19900101", // contains birthDate
                "name" to "testName",
                "birthDate" to "1990-01-01",
                "email" to "test@email.com",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(body), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("유효하지 않은 생년월일 형식으로 회원가입하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun throwsBadRequest_whenBirthDateFormatIsInvalid() {
            // arrange
            val body = mapOf(
                "userId" to "newUser",
                "password" to "password123!",
                "name" to "testName",
                "birthDate" to "not-a-date", // invalid format
                "email" to "test@email.com",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(body), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("필수 값이 없을 경우, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun throwsBadRequest_whenRequiredFieldIsMissing() {
            // arrange
            val body = mapOf(
                "userId" to "newUser",
                "password" to "password123!",
                // "name" 누락
                "birthDate" to "1990-01-01",
                "email" to "test@email.com",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(body), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }
    }

    // ─── GET /api/v1/users/me ───

    @DisplayName("GET /api/v1/users/me")
    @Nested
    inner class GetMyInfo {

        @DisplayName("유효한 로그인 정보로 내 정보를 조회하면, 200 과 마스킹된 이름을 반환한다.")
        @Test
        fun returnsUserInfoWithMaskedName_whenValidCredentialsProvided() {
            // arrange
            setupUser(userId = "testUser", password = "testPassword", name = "testName")
            val headers = loginHeaders("testUser", "testPassword")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
            val response = testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, HttpEntity<Any>(Unit, headers), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data?.get("userId")).isEqualTo("testUser") },
                { assertThat(response.body?.data?.get("name")).isEqualTo("testNam*") }, // masked
                { assertThat(response.body?.data?.get("email")).isEqualTo("test@email.com") },
                { assertThat(response.body?.data?.containsKey("encryptedPassword")).isFalse() },
            )
        }

        @DisplayName("존재하지 않는 userId 로 내 정보를 조회하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun throwsUnauthorized_whenUserIdDoesNotExist() {
            // arrange
            val headers = loginHeaders("nonExistent", "testPassword")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, HttpEntity<Any>(Unit, headers), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("틀린 비밀번호로 내 정보를 조회하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun throwsUnauthorized_whenPasswordIsWrong() {
            // arrange
            setupUser(userId = "testUser", password = "testPassword")
            val headers = loginHeaders("testUser", "wrongPassword")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, HttpEntity<Any>(Unit, headers), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }
    }

    // ─── PUT /api/v1/users/password ───

    @DisplayName("PUT /api/v1/users/password")
    @Nested
    inner class ChangePassword {

        @DisplayName("유효한 새 비밀번호로 비밀번호를 수정하면, 200 응답을 받는다.")
        @Test
        fun returnsSuccess_whenValidNewPasswordProvided() {
            // arrange
            setupUser(userId = "testUser", password = "testPassword")
            val headers = loginHeaders("testUser", "testPassword")
            val body = mapOf(
                "oldPassword" to "testPassword",
                "newPassword" to "newPassword123!",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(ENDPOINT_PASSWORD, HttpMethod.PUT, HttpEntity(body, headers), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
            )
        }

        @DisplayName("현재 비밀번호와 동일한 새 비밀번호로 수정하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            setupUser(userId = "testUser", password = "testPassword")
            val headers = loginHeaders("testUser", "testPassword")
            val body = mapOf(
                "oldPassword" to "testPassword",
                "newPassword" to "testPassword", // same as current
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(ENDPOINT_PASSWORD, HttpMethod.PUT, HttpEntity(body, headers), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("생년월일을 포함한 새 비밀번호로 수정하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun throwsBadRequest_whenNewPasswordContainsBirthDate() {
            // arrange
            setupUser(userId = "testUser", password = "testPassword", birthDate = LocalDate.of(1990, 1, 1))
            val headers = loginHeaders("testUser", "testPassword")
            val body = mapOf(
                "oldPassword" to "testPassword",
                "newPassword" to "pass19900101", // contains birthDate
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(ENDPOINT_PASSWORD, HttpMethod.PUT, HttpEntity(body, headers), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }
    }
}
