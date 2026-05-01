package com.loopers.interfaces.api.catalog.brand

import com.loopers.infrastructure.catalog.brand.BrandEntity
import com.loopers.infrastructure.catalog.brand.BrandJpaRepository
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
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val BASE_URL = "/api/v1/brands"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun setupBrand(name: String = "Nike", description: String = "Just Do It"): BrandEntity =
        brandJpaRepository.save(BrandEntity(name = name, description = description))

    // ─── GET /api/v1/brands/{brandId} ───

    @DisplayName("GET /api/v1/brands/{brandId}")
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
                "$BASE_URL/${brand.id}", HttpMethod.GET, HttpEntity.EMPTY, responseType
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data?.get("id")).isEqualTo(brand.id.toInt()) },
                { assertThat(response.body?.data?.get("name")).isEqualTo("Nike") },
                { assertThat(response.body?.data?.get("description")).isEqualTo("Just Do It") },
            )
        }

        @DisplayName("존재하지 않는 브랜드 ID 로 조회하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun throwsNotFound_whenBrandDoesNotExist() {
            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "$BASE_URL/9999", HttpMethod.GET, HttpEntity.EMPTY, responseType
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }
    }
}
