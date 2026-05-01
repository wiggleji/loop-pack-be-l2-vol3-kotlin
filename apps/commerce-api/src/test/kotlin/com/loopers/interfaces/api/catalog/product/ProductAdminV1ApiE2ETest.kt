package com.loopers.interfaces.api.catalog.product

import com.loopers.infrastructure.catalog.brand.BrandEntity
import com.loopers.infrastructure.catalog.brand.BrandJpaRepository
import com.loopers.infrastructure.catalog.product.ProductEntity
import com.loopers.infrastructure.catalog.product.ProductJpaRepository
import com.loopers.infrastructure.catalog.product.ProductStockEntity
import com.loopers.infrastructure.catalog.product.ProductStockJpaRepository
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
class ProductAdminV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val productStockJpaRepository: ProductStockJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    companion object {
        private const val BASE_URL = "/api-admin/v1/products"
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

    private fun setupBrand(name: String = "Nike"): BrandEntity =
        brandJpaRepository.save(BrandEntity(name = name, description = "desc"))

    private fun setupProduct(brandId: Long, name: String = "Test Product", price: Int = 10000, stock: Int = 100): ProductEntity {
        val product = productJpaRepository.save(
            ProductEntity(brandId = brandId, name = name, description = "desc", price = price)
        )
        productStockJpaRepository.save(ProductStockEntity(productId = product.id, quantity = stock))
        return product
    }

    // ─── GET /api-admin/v1/products ───

    @DisplayName("GET /api-admin/v1/products")
    @Nested
    inner class GetProducts {

        @DisplayName("상품 목록을 조회하면, 200 과 상품 목록을 반환한다.")
        @Test
        fun returnsProductList_whenProductsExist() {
            // arrange
            val brand = setupBrand()
            setupProduct(brandId = brand.id, name = "Shoes")
            setupProduct(brandId = brand.id, name = "Bag")

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
    }

    // ─── GET /api-admin/v1/products/{productId} ───

    @DisplayName("GET /api-admin/v1/products/{productId}")
    @Nested
    inner class GetProduct {

        @DisplayName("존재하는 상품 ID 로 조회하면, 200 과 상품 상세 정보를 반환한다.")
        @Test
        fun returnsProductDetail_whenProductExists() {
            // arrange
            val brand = setupBrand(name = "Nike")
            val product = setupProduct(brandId = brand.id, name = "Air Max", price = 150000)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
            val response = testRestTemplate.exchange(
                "$BASE_URL/${product.id}", HttpMethod.GET, HttpEntity<Any>(adminHeaders()), responseType
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.get("name")).isEqualTo("Air Max") },
                { assertThat(response.body?.data?.get("brandName")).isEqualTo("Nike") },
            )
        }

        @DisplayName("존재하지 않는 상품 ID 로 조회하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
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

    // ─── POST /api-admin/v1/products ───

    @DisplayName("POST /api-admin/v1/products")
    @Nested
    inner class CreateProduct {

        @DisplayName("어드민 헤더와 유효한 정보로 상품을 생성하면, 200 과 상품 정보를 반환한다.")
        @Test
        fun returnsProductInfo_whenValidDataAndAdminHeader() {
            // arrange
            val brand = setupBrand(name = "Nike")
            val body = mapOf(
                "brandId" to brand.id,
                "name" to "New Shoes",
                "description" to "desc",
                "price" to 50000,
                "stock" to 100,
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
            val response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST, HttpEntity(body, adminHeaders()), responseType
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data?.get("name")).isEqualTo("New Shoes") },
                { assertThat(response.body?.data?.get("brandName")).isEqualTo("Nike") },
            )
        }

        @DisplayName("잘못된 어드민 헤더 값으로 상품을 생성하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun throwsUnauthorized_whenAdminHeaderValueIsWrong() {
            // arrange
            val brand = setupBrand()
            val body = mapOf(
                "brandId" to brand.id,
                "name" to "New Shoes",
                "description" to "desc",
                "price" to 50000,
                "stock" to 100,
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST, HttpEntity(body, wrongAdminHeaders()), responseType
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("존재하지 않는 브랜드 ID 로 상품을 생성하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun throwsNotFound_whenBrandDoesNotExist() {
            // arrange
            val body = mapOf(
                "brandId" to 9999L,
                "name" to "New Shoes",
                "description" to "desc",
                "price" to 50000,
                "stock" to 100,
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST, HttpEntity(body, adminHeaders()), responseType
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    // ─── PUT /api-admin/v1/products/{productId} ───

    @DisplayName("PUT /api-admin/v1/products/{productId}")
    @Nested
    inner class UpdateProduct {

        @DisplayName("어드민 헤더와 유효한 정보로 상품을 수정하면, 200 과 수정된 상품 정보를 반환한다.")
        @Test
        fun returnsUpdatedProductInfo_whenValidDataAndAdminHeader() {
            // arrange
            val brand = setupBrand(name = "Nike")
            val product = setupProduct(brandId = brand.id, name = "Old Shoes", price = 50000)
            val body = mapOf(
                "name" to "New Shoes",
                "description" to "updated desc",
                "price" to 60000,
                "stock" to 50,
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
            val response = testRestTemplate.exchange(
                "$BASE_URL/${product.id}", HttpMethod.PUT, HttpEntity(body, adminHeaders()), responseType
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.get("name")).isEqualTo("New Shoes") },
                { assertThat(response.body?.data?.get("price")).isEqualTo(60000) },
            )
        }

        @DisplayName("잘못된 어드민 헤더 값으로 상품을 수정하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun throwsUnauthorized_whenAdminHeaderValueIsWrong() {
            // arrange
            val brand = setupBrand()
            val product = setupProduct(brandId = brand.id)
            val body = mapOf("name" to "New Shoes", "description" to "desc", "price" to 60000, "stock" to 50)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "$BASE_URL/${product.id}", HttpMethod.PUT, HttpEntity(body, wrongAdminHeaders()), responseType
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("존재하지 않는 상품 ID 로 수정하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            // arrange
            val body = mapOf("name" to "New Shoes", "description" to "desc", "price" to 60000, "stock" to 50)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "$BASE_URL/9999", HttpMethod.PUT, HttpEntity(body, adminHeaders()), responseType
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    // ─── DELETE /api-admin/v1/products/{productId} ───

    @DisplayName("DELETE /api-admin/v1/products/{productId}")
    @Nested
    inner class DeleteProduct {

        @DisplayName("어드민 헤더로 존재하는 상품을 삭제하면, 200 응답을 받는다.")
        @Test
        fun returnsSuccess_whenProductExistsAndAdminHeader() {
            // arrange
            val brand = setupBrand()
            val product = setupProduct(brandId = brand.id)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "$BASE_URL/${product.id}", HttpMethod.DELETE, HttpEntity<Any>(adminHeaders()), responseType
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
            )
        }

        @DisplayName("잘못된 어드민 헤더 값으로 상품을 삭제하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun throwsUnauthorized_whenAdminHeaderValueIsWrong() {
            // arrange
            val brand = setupBrand()
            val product = setupProduct(brandId = brand.id)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "$BASE_URL/${product.id}", HttpMethod.DELETE, HttpEntity<Any>(wrongAdminHeaders()), responseType
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("존재하지 않는 상품 ID 로 삭제하면, 200 응답을 반환한다 (idempotent).")
        @Test
        fun returnsSuccess_whenProductDoesNotExist() {
            // act - deleteProduct has no existence guard; idempotent delete
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any?>>() {}
            val response = testRestTemplate.exchange(
                "$BASE_URL/9999", HttpMethod.DELETE, HttpEntity<Any>(adminHeaders()), responseType
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        }
    }
}
