package com.loopers.interfaces.api.coupon

import com.loopers.domain.coupon.CouponType
import com.loopers.infrastructure.coupon.CouponTemplateEntity
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository
import com.loopers.infrastructure.coupon.UserCouponEntity
import com.loopers.infrastructure.coupon.UserCouponJpaRepository
import com.loopers.infrastructure.user.UserEntity
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
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userJpaRepository: UserJpaRepository,
    private val couponTemplateJpaRepository: CouponTemplateJpaRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val LOGIN_ID_HEADER = "X-Loopers-LoginId"
        private const val LOGIN_PW_HEADER = "X-Loopers-LoginPw"
        private const val DEFAULT_LOGIN_ID = "testUser"
        private const val DEFAULT_PASSWORD = "testPassword"
    }

    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

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

    private fun setupTemplate(
        name: String = "10% 할인 쿠폰",
        type: CouponType = CouponType.RATE,
        discountValue: Int = 10,
        maxIssuance: Int? = null,
    ): CouponTemplateEntity = couponTemplateJpaRepository.save(
        CouponTemplateEntity(
            name = name,
            type = type,
            discountValue = discountValue,
            minOrderAmount = 0,
            maxIssuance = maxIssuance,
            expiresAt = LocalDate.now().plusDays(30),
        )
    )

    // ─── POST /api/v1/coupons/issue (비동기 발급 요청) ───

    @DisplayName("POST /api/v1/coupons/issue")
    @Nested
    inner class IssueCoupon {

        @DisplayName("인증된 사용자가 쿠폰 발급을 요청하면, 202 ACCEPTED + REQUESTED 상태를 반환한다.")
        @Test
        fun returnsAccepted() {
            setupUser()
            val template = setupTemplate()

            val request = mapOf("couponTemplateId" to template.id)
            val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/coupons/issue",
                HttpMethod.POST,
                HttpEntity(request, authHeaders()),
                responseType,
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data?.get("status")).isEqualTo("REQUESTED") },
            )
        }

        @DisplayName("같은 쿠폰을 다시 요청하면, 202 ACCEPTED + 기존 요청 상태를 반환한다. (멱등성)")
        @Test
        fun returnsExistingRequest_whenAlreadyRequested() {
            setupUser()
            val template = setupTemplate()

            val request = mapOf("couponTemplateId" to template.id)
            val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}

            // 첫 번째 요청
            testRestTemplate.exchange(
                "/api/v1/coupons/issue",
                HttpMethod.POST,
                HttpEntity(request, authHeaders()),
                responseType,
            )

            // 두 번째 요청 — 멱등성: 기존 요청 반환
            val response = testRestTemplate.exchange(
                "/api/v1/coupons/issue",
                HttpMethod.POST,
                HttpEntity(request, authHeaders()),
                responseType,
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED) },
                { assertThat(response.body?.data?.get("status")).isEqualTo("REQUESTED") },
            )
        }

        @DisplayName("존재하지 않는 템플릿이면, 404 NOT_FOUND를 반환한다.")
        @Test
        fun throwsNotFound_whenTemplateNotExists() {
            setupUser()

            val request = mapOf("couponTemplateId" to 999L)
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/coupons/issue",
                HttpMethod.POST,
                HttpEntity(request, authHeaders()),
                responseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("인증에 실패하면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        fun throwsUnauthorized_whenAuthFails() {
            setupUser()
            val template = setupTemplate()

            val request = mapOf("couponTemplateId" to template.id)
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/coupons/issue",
                HttpMethod.POST,
                HttpEntity(request, authHeaders(password = "wrongPassword")),
                responseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    // ─── GET /api/v1/coupons/issue/{couponTemplateId}/status ───

    @DisplayName("GET /api/v1/coupons/issue/{couponTemplateId}/status")
    @Nested
    inner class GetIssueStatus {

        @DisplayName("발급 요청 후 상태를 조회하면, 200 OK + 현재 상태를 반환한다.")
        @Test
        fun returnsIssueStatus() {
            setupUser()
            val template = setupTemplate()

            // 먼저 발급 요청
            val issueRequest = mapOf("couponTemplateId" to template.id)
            testRestTemplate.exchange(
                "/api/v1/coupons/issue",
                HttpMethod.POST,
                HttpEntity(issueRequest, authHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any?>>() {},
            )

            // 상태 조회
            val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/coupons/issue/${template.id}/status",
                HttpMethod.GET,
                HttpEntity<Any>(authHeaders()),
                responseType,
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.get("status")).isEqualTo("REQUESTED") },
            )
        }
    }

    // ─── GET /api/v1/coupons/me ───

    @DisplayName("GET /api/v1/coupons/me")
    @Nested
    inner class GetUserCoupons {

        @DisplayName("보유 쿠폰이 있으면, 200 과 쿠폰 목록을 반환한다.")
        @Test
        fun returnsUserCoupons() {
            val user = setupUser()
            val template = setupTemplate()
            userCouponJpaRepository.save(UserCouponEntity(userId = user.id, couponTemplateId = template.id))

            val responseType = object : ParameterizedTypeReference<ApiResponse<List<Map<String, Any?>>>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/coupons/me",
                HttpMethod.GET,
                HttpEntity<Any>(authHeaders()),
                responseType,
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data).hasSize(1) },
                { assertThat(response.body?.data?.first()?.get("status")).isEqualTo("AVAILABLE") },
            )
        }

        @DisplayName("보유 쿠폰이 없으면, 200 과 빈 목록을 반환한다.")
        @Test
        fun returnsEmptyList() {
            setupUser()

            val responseType = object : ParameterizedTypeReference<ApiResponse<List<Map<String, Any?>>>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/coupons/me",
                HttpMethod.GET,
                HttpEntity<Any>(authHeaders()),
                responseType,
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data).isEmpty() },
            )
        }
    }
}
