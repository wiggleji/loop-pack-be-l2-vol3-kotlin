package com.loopers.interfaces.api.like

import com.loopers.infrastructure.catalog.brand.BrandEntity
import com.loopers.infrastructure.catalog.brand.BrandJpaRepository
import com.loopers.infrastructure.catalog.product.ProductEntity
import com.loopers.infrastructure.catalog.product.ProductJpaRepository
import com.loopers.infrastructure.catalog.product.ProductStockEntity
import com.loopers.infrastructure.catalog.product.ProductStockJpaRepository
import com.loopers.infrastructure.like.LikeEntity
import com.loopers.infrastructure.like.LikeJpaRepository
import com.loopers.infrastructure.user.UserEntity
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
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
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userJpaRepository: UserJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val productStockJpaRepository: ProductStockJpaRepository,
    private val likeJpaRepository: LikeJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    companion object {
        private const val LOGIN_ID_HEADER = "X-Loopers-LoginId"
        private const val LOGIN_PW_HEADER = "X-Loopers-LoginPw"
        private const val DEFAULT_LOGIN_ID = "testUser"
        private const val DEFAULT_PASSWORD = "testPassword"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    // ─── Helpers ───

    private fun authHeaders(
        loginId: String = DEFAULT_LOGIN_ID,
        password: String = DEFAULT_PASSWORD,
    ): HttpHeaders = HttpHeaders().apply {
        this[LOGIN_ID_HEADER] = loginId
        this[LOGIN_PW_HEADER] = password
    }

    private fun setupUser(
        userId: String = DEFAULT_LOGIN_ID,
        password: String = DEFAULT_PASSWORD,
    ): UserEntity = userJpaRepository.save(
        UserEntity(
            userId = userId,
            encryptedPassword = passwordEncoder.encode(password),
            name = "테스트유저",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "test@example.com",
        )
    )

    private fun setupBrand(name: String = "Test Brand"): BrandEntity =
        brandJpaRepository.save(BrandEntity(name = name, description = "desc"))

    private fun setupProduct(
        brandId: Long,
        name: String = "Test Product",
        stock: Int = 10,
        likeCount: Int = 0,
    ): ProductEntity {
        val product = productJpaRepository.save(
            ProductEntity(brandId = brandId, name = name, description = "desc", price = 10000, likeCount = likeCount)
        )
        productStockJpaRepository.save(ProductStockEntity(productId = product.id, quantity = stock))
        return product
    }

    // ─── POST /api/v1/products/{productId}/likes ───

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    inner class AddLike {

        @DisplayName("인증된 사용자가 존재하는 상품에 좋아요를 누르면, 200 응답을 반환한다.")
        @Test
        fun returnsSuccess_whenProductExistsAndUserAuthenticated() {
            // arrange
            val user = setupUser()
            val brand = setupBrand()
            val product = setupProduct(brandId = brand.id)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/products/${product.id}/likes",
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders()),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
            )
        }

        @DisplayName("존재하지 않는 상품에 좋아요를 누르면, 404 NOT_FOUND 응답을 반환한다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            // arrange
            setupUser()

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/products/9999/likes",
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders()),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("이미 좋아요한 상품에 다시 좋아요를 누르면, 409 CONFLICT 응답을 반환한다.")
        @Test
        fun throwsConflict_whenAlreadyLiked() {
            // arrange
            val user = setupUser()
            val brand = setupBrand()
            val product = setupProduct(brandId = brand.id)
            likeJpaRepository.save(LikeEntity(userId = user.id, productId = product.id))

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/products/${product.id}/likes",
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders()),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("인증에 실패하면, 401 UNAUTHORIZED 응답을 반환한다.")
        @Test
        fun throwsUnauthorized_whenAuthFails() {
            // arrange
            setupUser()
            val brand = setupBrand()
            val product = setupProduct(brandId = brand.id)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/products/${product.id}/likes",
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders(password = "wrongPassword")),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    // ─── DELETE /api/v1/products/{productId}/likes ───

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    inner class RemoveLike {

        @DisplayName("좋아요한 상품에 좋아요 취소를 하면, 200 응답을 반환한다.")
        @Test
        fun returnsSuccess_whenLikeExists() {
            // arrange
            val user = setupUser()
            val brand = setupBrand()
            val product = setupProduct(brandId = brand.id, likeCount = 1)
            likeJpaRepository.save(LikeEntity(userId = user.id, productId = product.id))

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/products/${product.id}/likes",
                HttpMethod.DELETE,
                HttpEntity<Any>(authHeaders()),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
            )
        }

        @DisplayName("좋아요하지 않은 상품에 좋아요 취소를 하면, 404 NOT_FOUND 응답을 반환한다.")
        @Test
        fun throwsNotFound_whenLikeDoesNotExist() {
            // arrange
            setupUser()
            val brand = setupBrand()
            val product = setupProduct(brandId = brand.id)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/products/${product.id}/likes",
                HttpMethod.DELETE,
                HttpEntity<Any>(authHeaders()),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("인증에 실패하면, 401 UNAUTHORIZED 응답을 반환한다.")
        @Test
        fun throwsUnauthorized_whenAuthFails() {
            // arrange
            setupUser()
            val brand = setupBrand()
            val product = setupProduct(brandId = brand.id)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/products/${product.id}/likes",
                HttpMethod.DELETE,
                HttpEntity<Any>(authHeaders(password = "wrongPassword")),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    // ─── GET /api/v1/users/{userId}/likes ───

    @DisplayName("GET /api/v1/users/{userId}/likes")
    @Nested
    inner class GetLikedProducts {

        @DisplayName("좋아요한 상품이 있으면, 200 과 좋아요 상품 목록을 반환한다.")
        @Test
        fun returnsLikedProducts_whenLikesExist() {
            // arrange
            val user = setupUser()
            val brand = setupBrand()
            val product = setupProduct(brandId = brand.id, name = "Liked Product", likeCount = 1)
            likeJpaRepository.save(LikeEntity(userId = user.id, productId = product.id))

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<List<Map<String, Any?>>>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/users/${user.id}/likes",
                HttpMethod.GET,
                HttpEntity<Any>(authHeaders()),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data).hasSize(1) },
                { assertThat(response.body?.data?.first()?.get("productName")).isEqualTo("Liked Product") },
            )
        }

        @DisplayName("좋아요한 상품이 없으면, 200 과 빈 목록을 반환한다.")
        @Test
        fun returnsEmptyList_whenNoLikesExist() {
            // arrange
            val user = setupUser()

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<List<Map<String, Any?>>>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/users/${user.id}/likes",
                HttpMethod.GET,
                HttpEntity<Any>(authHeaders()),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data).isEmpty() },
            )
        }

        @DisplayName("다른 사용자의 좋아요 목록을 조회하면, 401 UNAUTHORIZED 응답을 반환한다.")
        @Test
        fun throwsUnauthorized_whenAccessingOtherUsersLikes() {
            // arrange
            setupUser(userId = "userA", password = "passA")
            val userB = setupUser(userId = "userB", password = "passB")

            // act — userA 인증으로 userB의 좋아요 목록 조회 시도
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/users/${userB.id}/likes",
                HttpMethod.GET,
                HttpEntity<Any>(authHeaders(loginId = "userA", password = "passA")),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("인증에 실패하면, 401 UNAUTHORIZED 응답을 반환한다.")
        @Test
        fun throwsUnauthorized_whenAuthFails() {
            // arrange
            val user = setupUser()

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/users/${user.id}/likes",
                HttpMethod.GET,
                HttpEntity<Any>(authHeaders(password = "wrongPassword")),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }
}
