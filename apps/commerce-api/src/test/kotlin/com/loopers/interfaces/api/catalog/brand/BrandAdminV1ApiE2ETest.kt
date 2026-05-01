package com.loopers.interfaces.api.catalog.brand

import com.loopers.infrastructure.catalog.brand.BrandEntity
import com.loopers.infrastructure.catalog.brand.BrandJpaRepository
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandAdminV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    companion object {
        private const val BASE_URL = "/api-admin/v1/brands"
        private const val ADMIN_HEADER = "X-Loopers-Ldap"
        private const val ADMIN_VALUE = "loopers.admin"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    private fun adminHeaders(): HttpHeaders = HttpHeaders().apply {
        this[ADMIN_HEADER] = ADMIN_VALUE
    }

    private fun wrongAdminHeaders(): HttpHeaders = HttpHeaders().apply {
        this[ADMIN_HEADER] = "wrong.value"
    }

    private fun setupBrand(name: String = "Nike", description: String = "Just Do It"): BrandEntity =
        brandJpaRepository.save(BrandEntity(name = name, description = description))

    // ─── GET /api-admin/v1/brands ───

    @DisplayName("GET /api-admin/v1/brands")
    @Nested
    inner class GetBrands {

        @DisplayName("브랜드 목록을 조회하면, 200 과 브랜드 목록을 반환한다.")
        @Test
        fun returnsBrandList_whenBrandsExist() {
            // arrange
            setupBrand(name = "Nike")
            setupBrand(name = "Adidas")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<List<Map<String, Any?>>>>() {}
            val response = testRestTemplate.exchange(BASE_URL, HttpMethod.GET, HttpEntity<Any>(adminHeaders()), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data).hasSize(2) },
            )
        }

        @DisplayName("브랜드가 없으면, 200 과 빈 목록을 반환한다.")
        @Test
        fun returnsEmptyList_whenNoBrandsExist() {
            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<List<Map<String, Any?>>>>() {}
            val response = testRestTemplate.exchange(BASE_URL, HttpMethod.GET, HttpEntity<Any>(adminHeaders()), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data).isEmpty() },
            )
        }
    }

    // ─── GET /api-admin/v1/brands/{brandId} ───

    @DisplayName("GET /api-admin/v1/brands/{brandId}")
    @Nested
    inner class GetBrand {

        @DisplayName("존재하는 브랜드 ID 로 조회하면, 200 과 브랜드 정보를 반환한다.")
        @Test
        fun returnsBrandInfo_whenBrandExists() {
            // arrange
            val brand = setupBrand(name = "Nike", description = "Just Do It")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
            val response = testRestTemplate.exchange(
                "$BASE_URL/${brand.id}", HttpMethod.GET, HttpEntity<Any>(adminHeaders()), responseType
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.get("name")).isEqualTo("Nike") },
            )
        }

        @DisplayName("존재하지 않는 브랜드 ID 로 조회하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun throwsNotFound_whenBrandDoesNotExist() {
            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "$BASE_URL/9999", HttpMethod.GET, HttpEntity<Any>(adminHeaders()), responseType
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }
    }

    // ─── POST /api-admin/v1/brands ───

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    inner class CreateBrand {

        @DisplayName("어드민 헤더와 유효한 정보로 브랜드를 생성하면, 200 과 브랜드 정보를 반환한다.")
        @Test
        fun returnsBrandInfo_whenValidDataAndAdminHeader() {
            // arrange
            val body = mapOf("name" to "New Brand", "description" to "Brand description")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
            val response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST, HttpEntity(body, adminHeaders()), responseType
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data?.get("name")).isEqualTo("New Brand") },
            )
        }

        @DisplayName("잘못된 어드민 헤더 값으로 브랜드를 생성하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun throwsUnauthorized_whenAdminHeaderValueIsWrong() {
            // arrange
            val body = mapOf("name" to "New Brand", "description" to "Brand description")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST, HttpEntity(body, wrongAdminHeaders()), responseType
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    // ─── PUT /api-admin/v1/brands/{brandId} ───

    @DisplayName("PUT /api-admin/v1/brands/{brandId}")
    @Nested
    inner class UpdateBrand {

        @DisplayName("어드민 헤더와 유효한 정보로 브랜드를 수정하면, 200 과 수정된 브랜드 정보를 반환한다.")
        @Test
        fun returnsUpdatedBrandInfo_whenValidDataAndAdminHeader() {
            // arrange
            val brand = setupBrand(name = "Nike")
            val body = mapOf("name" to "Nike Updated", "description" to "Updated desc")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
            val response = testRestTemplate.exchange(
                "$BASE_URL/${brand.id}", HttpMethod.PUT, HttpEntity(body, adminHeaders()), responseType
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.get("name")).isEqualTo("Nike Updated") },
            )
        }

        @DisplayName("잘못된 어드민 헤더 값으로 브랜드를 수정하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun throwsUnauthorized_whenAdminHeaderValueIsWrong() {
            // arrange
            val brand = setupBrand()
            val body = mapOf("name" to "Nike Updated", "description" to "Updated desc")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "$BASE_URL/${brand.id}", HttpMethod.PUT, HttpEntity(body, wrongAdminHeaders()), responseType
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("존재하지 않는 브랜드 ID 로 수정하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun throwsNotFound_whenBrandDoesNotExist() {
            // arrange
            val body = mapOf("name" to "Nike Updated", "description" to "Updated desc")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "$BASE_URL/9999", HttpMethod.PUT, HttpEntity(body, adminHeaders()), responseType
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    // ─── DELETE /api-admin/v1/brands/{brandId} ───

    @DisplayName("DELETE /api-admin/v1/brands/{brandId}")
    @Nested
    inner class DeleteBrand {

        @DisplayName("어드민 헤더로 존재하는 브랜드를 삭제하면, 200 응답을 받는다.")
        @Test
        fun returnsSuccess_whenBrandExistsAndAdminHeader() {
            // arrange
            val brand = setupBrand()

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "$BASE_URL/${brand.id}", HttpMethod.DELETE, HttpEntity<Any>(adminHeaders()), responseType
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
            )
        }

        @DisplayName("잘못된 어드민 헤더 값으로 브랜드를 삭제하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun throwsUnauthorized_whenAdminHeaderValueIsWrong() {
            // arrange
            val brand = setupBrand()

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "$BASE_URL/${brand.id}", HttpMethod.DELETE, HttpEntity<Any>(wrongAdminHeaders()), responseType
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("존재하지 않는 브랜드 ID 로 삭제하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun throwsNotFound_whenBrandDoesNotExist() {
            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "$BASE_URL/9999", HttpMethod.DELETE, HttpEntity<Any>(adminHeaders()), responseType
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }
}
