package com.loopers.infrastructure.test

import com.loopers.domain.catalog.brand.BrandStatus
import com.loopers.domain.catalog.product.ProductStatus
import com.loopers.infrastructure.catalog.brand.BrandEntity
import com.loopers.infrastructure.catalog.brand.BrandJpaRepository
import com.loopers.infrastructure.catalog.product.ProductEntity
import com.loopers.infrastructure.catalog.product.ProductJpaRepository
import com.loopers.infrastructure.catalog.product.ProductStockEntity
import com.loopers.infrastructure.catalog.product.ProductStockJpaRepository
import com.loopers.infrastructure.user.UserEntity
import com.loopers.infrastructure.user.UserJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * k6 부하 테스트를 위한 더미 데이터 시더.
 * 'local' 프로필에서만 동작하며, 1000명의 테스트 유저와 브랜드/상품/재고를 확보한다.
 *
 * k6 스크립트가 참조하는 데이터:
 *   - 유저: testuser-1 ~ testuser-5100 (password: TestPass1!)
 *   - 상품: productId=1, 재고 999,999
 */
@Component
@Profile("local")
class TestDataSeeder(
    private val userJpaRepository: UserJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val productStockJpaRepository: ProductStockJpaRepository,
    private val passwordEncoder: PasswordEncoder,
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val TEST_USER_PREFIX = "testuser-"
        private const val TEST_USER_COUNT = 5100 // k6 VU offset: queue-benchmark 1~1000, ttl 1001~1005, 2001~2005, throughput 3001~5100
        private const val RAW_PASSWORD = "TestPass1!"
    }

    @Transactional
    override fun run(vararg args: String?) {
        seedUsers()
        seedBrandAndProductAndStock()
    }

    private fun seedUsers() {
        val existingCount = userJpaRepository.findAll()
            .count { it.userId.startsWith(TEST_USER_PREFIX) }

        if (existingCount >= TEST_USER_COUNT) {
            log.info("Test users already exist (count: $existingCount). Skipping seeding.")
            return
        }

        log.info("Seeding $TEST_USER_COUNT test users for k6 benchmark...")
        val encoded = passwordEncoder.encode(RAW_PASSWORD)
        val users = (1..TEST_USER_COUNT).map { i ->
            UserEntity(
                userId = "$TEST_USER_PREFIX$i",
                encryptedPassword = encoded,
                name = "Tester $i",
                email = "test$i@example.com",
                birthDate = LocalDate.of(1990, 1, 1),
            )
        }
        userJpaRepository.saveAll(users)
        log.info("Successfully seeded $TEST_USER_COUNT users.")
    }

    private fun seedBrandAndProductAndStock() {
        // 1. 브랜드 확보
        val brand = brandJpaRepository.findAll().firstOrNull { it.name == "Benchmark Brand" }
            ?: run {
                log.info("Seeding 'Benchmark Brand' for k6 benchmark...")
                brandJpaRepository.save(
                    BrandEntity(
                        name = "Benchmark Brand",
                        description = "Brand for performance testing",
                        status = BrandStatus.ACTIVE,
                    )
                )
            }

        // 2. 상품 확보
        val product = productJpaRepository.findAll().firstOrNull { it.name == "Benchmark Product" }
            ?: run {
                log.info("Seeding 'Benchmark Product' for k6 benchmark...")
                productJpaRepository.save(
                    ProductEntity(
                        brandId = brand.id,
                        name = "Benchmark Product",
                        description = "Product for performance testing",
                        price = 10000,
                        status = ProductStatus.ACTIVE,
                    )
                )
            }

        // 3. 재고 확보
        val productId = product.id
        val stock = productStockJpaRepository.findByProductId(productId)
        if (stock != null) {
            stock.update(999999)
            log.info("Updated product ID $productId stock to 999999 for benchmark.")
        } else {
            log.info("Seeding stock for product ID $productId...")
            productStockJpaRepository.save(
                ProductStockEntity(
                    productId = productId,
                    quantity = 999999,
                )
            )
        }
    }
}
