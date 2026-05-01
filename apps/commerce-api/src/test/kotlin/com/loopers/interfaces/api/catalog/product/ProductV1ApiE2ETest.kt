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
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val productStockJpaRepository: ProductStockJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    companion object {
        private const val BASE_URL = "/api/v1/products"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
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

    // ─── GET /api/v1/products ───

    @DisplayName("GET /api/v1/products")
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
            val response = testRestTemplate.exchange(BASE_URL, HttpMethod.GET, HttpEntity.EMPTY, responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data).hasSize(2) },
            )
        }

        @DisplayName("상품이 없으면, 200 과 빈 목록을 반환한다.")
        @Test
        fun returnsEmptyList_whenNoProductsExist() {
            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<List<Map<String, Any?>>>>() {}
            val response = testRestTemplate.exchange(BASE_URL, HttpMethod.GET, HttpEntity.EMPTY, responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data).isEmpty() },
            )
        }

        @DisplayName("brandId 로 필터링하면, 해당 브랜드의 상품만 반환한다.")
        @Test
        fun returnsFilteredProductList_whenBrandIdIsProvided() {
            // arrange
            val brand1 = setupBrand(name = "Nike")
            val brand2 = setupBrand(name = "Adidas")
            setupProduct(brandId = brand1.id, name = "Nike Shoes")
            setupProduct(brandId = brand2.id, name = "Adidas Shoes")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<List<Map<String, Any?>>>>() {}
            val response = testRestTemplate.exchange(
                "$BASE_URL?brandId=${brand1.id}", HttpMethod.GET, HttpEntity.EMPTY, responseType
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data).hasSize(1) },
                { assertThat(response.body?.data?.get(0)?.get("name")).isEqualTo("Nike Shoes") },
            )
        }
    }

    // ─── GET /api/v1/products/{productId} ───

    @DisplayName("GET /api/v1/products/{productId}")
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
                "$BASE_URL/${product.id}", HttpMethod.GET, HttpEntity.EMPTY, responseType
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data?.get("name")).isEqualTo("Air Max") },
                { assertThat(response.body?.data?.get("price")).isEqualTo(150000) },
                { assertThat(response.body?.data?.get("brandName")).isEqualTo("Nike") },
            )
        }

        @DisplayName("존재하지 않는 상품 ID 로 조회하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
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
