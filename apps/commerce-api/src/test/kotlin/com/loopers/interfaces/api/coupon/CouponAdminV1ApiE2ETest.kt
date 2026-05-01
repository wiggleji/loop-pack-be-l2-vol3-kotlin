package com.loopers.interfaces.api.coupon

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
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAdminV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    // ─── POST /api/v1/admin/coupons ───

    @DisplayName("POST /api/v1/admin/coupons")
    @Nested
    inner class CreateTemplate {

        @DisplayName("유효한 쿠폰 템플릿을 생성하면, 200 응답을 반환한다.")
        @Test
        fun returnsSuccess() {
            val request = mapOf(
                "name" to "10% 할인 쿠폰",
                "type" to "RATE",
                "discountValue" to 10,
                "minOrderAmount" to 10000,
                "maxIssuance" to 100,
                "expiresAt" to LocalDate.now().plusDays(30).toString(),
            )

            val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/admin/coupons",
                HttpMethod.POST,
                HttpEntity(request),
                responseType,
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data?.get("name")).isEqualTo("10% 할인 쿠폰") },
                { assertThat(response.body?.data?.get("type")).isEqualTo("RATE") },
            )
        }

        @DisplayName("이름이 빈 값이면, 400 BAD_REQUEST를 반환한다.")
        @Test
        fun throwsBadRequest_whenNameBlank() {
            val request = mapOf(
                "name" to "",
                "type" to "FIXED",
                "discountValue" to 3000,
                "expiresAt" to LocalDate.now().plusDays(30).toString(),
            )

            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/admin/coupons",
                HttpMethod.POST,
                HttpEntity(request),
                responseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    // ─── GET /api/v1/admin/coupons ───

    @DisplayName("GET /api/v1/admin/coupons")
    @Nested
    inner class GetTemplates {

        @DisplayName("쿠폰 템플릿 목록을 조회하면, 200 응답을 반환한다.")
        @Test
        fun returnsSuccess() {
            // arrange — create 2 templates
            val request = mapOf(
                "name" to "쿠폰A",
                "type" to "FIXED",
                "discountValue" to 1000,
                "expiresAt" to LocalDate.now().plusDays(30).toString(),
            )
            testRestTemplate.postForEntity("/api/v1/admin/coupons", request, Any::class.java)
            testRestTemplate.postForEntity(
                "/api/v1/admin/coupons",
                request.toMutableMap().apply { this["name"] = "쿠폰B" },
                Any::class.java,
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<List<Map<String, Any?>>>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/admin/coupons",
                HttpMethod.GET,
                null,
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data).hasSize(2) },
            )
        }
    }

    // ─── DELETE /api/v1/admin/coupons/{id} ───

    @DisplayName("DELETE /api/v1/admin/coupons/{id}")
    @Nested
    inner class DeleteTemplate {

        @DisplayName("존재하는 쿠폰 템플릿을 삭제하면, 200 응답을 반환한다.")
        @Test
        fun returnsSuccess() {
            // arrange
            val createRequest = mapOf(
                "name" to "삭제할 쿠폰",
                "type" to "FIXED",
                "discountValue" to 1000,
                "expiresAt" to LocalDate.now().plusDays(30).toString(),
            )
            val createResponseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
            val createResponse = testRestTemplate.exchange(
                "/api/v1/admin/coupons",
                HttpMethod.POST,
                HttpEntity(createRequest),
                createResponseType,
            )
            val couponId = (createResponse.body?.data?.get("id") as Number).toLong()

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/admin/coupons/$couponId",
                HttpMethod.DELETE,
                null,
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)

            // verify deleted (should not appear in list)
            val listResponseType = object : ParameterizedTypeReference<ApiResponse<List<Map<String, Any?>>>>() {}
            val listResponse = testRestTemplate.exchange("/api/v1/admin/coupons", HttpMethod.GET, null, listResponseType)
            assertThat(listResponse.body?.data).isEmpty()
        }
    }
}
