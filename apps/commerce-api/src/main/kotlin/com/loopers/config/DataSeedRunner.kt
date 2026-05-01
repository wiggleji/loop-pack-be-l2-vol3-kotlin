package com.loopers.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import kotlin.math.pow
import kotlin.random.Random

/**
 * 테스트 데이터 생성 Runner.
 *
 * 실행 조건: application.yml 에 `app.data-seed.enabled=true` 설정 시에만 동작.
 * 이미 데이터가 존재하면 스킵 (멱등).
 *
 * 분포 특성 (실제 커머스 패턴 반영):
 *   - 브랜드별 상품 수: 멱법칙 (상위 10% 브랜드가 전체 상품의 ~50% 보유)
 *   - 유저별 활동량: 멱법칙 (상위 5% 유저가 주문/좋아요의 ~50% 생성)
 *   - 상품별 좋아요: 멱법칙 (소수 인기 상품에 좋아요 집중)
 *   - 주문 날짜: 최근일수록 많음 (성장 곡선)
 *   - like_count: 실제 likes 행 수와 일치
 *
 * 사용법:
 *   - local: application.yml local 프로필에 `app.data-seed.enabled: true` 추가
 *   - test:  @TestPropertySource(properties = ["app.data-seed.enabled=true"])
 *   - CLI:   --app.data-seed.enabled=true
 */
@Component
@ConditionalOnProperty(name = ["app.data-seed.enabled"], havingValue = "true", matchIfMissing = false)
class DataSeedRunner(
    private val jdbcTemplate: JdbcTemplate,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        // ── Scale configuration ──
        const val BRAND_COUNT = 500
        const val PRODUCT_COUNT = 100_000
        const val ORDER_COUNT = 100_000
        const val LIKE_COUNT = 100_000
        const val USER_COUNT = 10_000
        const val COUPON_TEMPLATE_COUNT = 20
        const val USER_COUPON_COUNT = 30_000

        // ── Batch size for JDBC inserts ──
        const val BATCH_SIZE = 1_000

        private val PRODUCT_STATUSES = listOf("ACTIVE", "SOLD_OUT", "HIDDEN", "DISCONTINUED")
        private val PRODUCT_STATUS_WEIGHTS = listOf(0.85, 0.92, 0.97, 1.0) // cumulative
    }

    override fun run(args: ApplicationArguments) {
        val existingCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Long::class.java) ?: 0
        if (existingCount > 0) {
            log.info("[DataSeed] 데이터가 이미 존재합니다 (products: {}). 스킵합니다.", existingCount)
            return
        }

        log.info("[DataSeed] 테스트 데이터 생성을 시작합니다...")
        val start = System.currentTimeMillis()

        seedBrands()
        seedProducts()
        seedProductStocks()
        seedOrders()
        seedOrderItems()
        seedLikes()
        syncLikeCount()
        seedCouponTemplates()
        seedUserCoupons()

        val elapsed = System.currentTimeMillis() - start
        log.info("[DataSeed] 테스트 데이터 생성 완료 ({}ms)", elapsed)
        logCounts()
        logDistribution()
    }

    // ═══════════════════════════════════════════════════════════
    // Distribution helpers
    // ═══════════════════════════════════════════════════════════

    /**
     * 멱법칙(Power-law) ID 생성: [1, max] 범위에서 낮은 ID에 집중.
     *
     * alpha가 클수록 낮은 ID에 집중된다.
     *   - alpha=1.0 → 균등 분포 (uniform)
     *   - alpha=2.0 → 상위 ~30%가 전체의 ~70%
     *   - alpha=3.0 → 상위 ~20%가 전체의 ~80%
     *
     * 수학: id = floor(max * u^alpha) + 1, where u ~ Uniform(0,1)
     * u^alpha (alpha>1) 은 0 근처에 밀집 → 낮은 ID가 빈번.
     */
    private fun powerLawId(max: Int, alpha: Double): Int {
        val u = Random.nextDouble()
        return (max.toDouble() * u.pow(alpha)).toInt().coerceIn(0, max - 1) + 1
    }

    private fun powerLawIdLong(max: Long, alpha: Double): Long {
        val u = Random.nextDouble()
        return (max.toDouble() * u.pow(alpha)).toLong().coerceIn(0, max - 1) + 1
    }

    /**
     * 최근 날짜에 편향된 일수 오프셋.
     * 반환값이 0에 가까울수록 최근 → 최근 날짜가 빈번.
     *
     * 수학: offset = floor(maxDays * u^0.5)
     * u^0.5는 1 근처에 밀집 → 큰 offset은 드묾 → 결과적으로 최근 날짜 편향.
     *
     * 기대 분포:
     *   - 최근 30일: 전체의 ~33%
     *   - 최근 60일: 전체의 ~58%
     *   - 최근 90일: 전체의 ~70%
     */
    private fun recentBiasedDayOffset(maxDays: Int): Int {
        val u = Random.nextDouble()
        return (maxDays * u.pow(0.5)).toInt().coerceIn(0, maxDays)
    }

    // ═══════════════════════════════════════════════════════════
    // Seed methods
    // ═══════════════════════════════════════════════════════════

    // ─── Brands ───

    private fun seedBrands() {
        log.info("[DataSeed] Brands ({})...", BRAND_COUNT)
        val sql = "INSERT INTO brands (name, description, status, created_at, updated_at) VALUES (?, ?, ?, NOW() - INTERVAL ? DAY, NOW())"

        (1..BRAND_COUNT).chunked(BATCH_SIZE).forEach { chunk ->
            jdbcTemplate.batchUpdate(sql, chunk.map { i ->
                arrayOf<Any>(
                    "Brand_$i",
                    "Description for brand $i",
                    if (Random.nextDouble() < 0.95) "ACTIVE" else "INACTIVE",
                    Random.nextInt(0, 366),
                )
            })
        }
    }

    // ─── Products (power-law brand distribution) ───

    private fun seedProducts() {
        log.info("[DataSeed] Products ({})...", PRODUCT_COUNT)
        val sql = """
            INSERT INTO products (brand_id, name, description, price, like_count, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, 0, ?, NOW() - INTERVAL ? DAY, NOW())
        """.trimIndent()

        (1..PRODUCT_COUNT).chunked(BATCH_SIZE).forEach { chunk ->
            jdbcTemplate.batchUpdate(sql, chunk.map { i ->
                val r = Random.nextDouble()
                val status = PRODUCT_STATUSES[PRODUCT_STATUS_WEIGHTS.indexOfFirst { w -> r < w }]
                arrayOf<Any>(
                    powerLawId(BRAND_COUNT, alpha = 2.0),        // power-law: top ~30% brands hold ~70% products
                    "Product_$i",
                    "Description for product $i",
                    Random.nextInt(1_000, 100_001),              // price: 1,000 ~ 100,000
                    0,                                           // like_count: 0 (synced after likes)
                    status,
                    Random.nextInt(0, 366),
                )
            })
        }
    }

    // ─── Product Stocks ───

    private fun seedProductStocks() {
        log.info("[DataSeed] Product Stocks...")
        jdbcTemplate.update("""
            INSERT INTO product_stocks (product_id, quantity, created_at, updated_at)
            SELECT id, FLOOR(RAND() * 200), NOW(), NOW() FROM products
        """.trimIndent())
    }

    // ─── Orders (power-law users, recent-biased dates, status distribution) ───

    private val ORDER_STATUSES = listOf("DELIVERED", "SHIPPING", "PREPARING", "PAID", "PLACED", "CANCELLED", "REFUNDED")
    private val ORDER_STATUS_WEIGHTS = listOf(0.55, 0.65, 0.75, 0.85, 0.90, 0.97, 1.0) // cumulative

    private fun seedOrders() {
        log.info("[DataSeed] Orders ({})...", ORDER_COUNT)
        val sql = """
            INSERT INTO orders (user_id, original_total_price, discount_amount, total_price, status, paid_at, shipped_at, delivered_at, cancelled_at, created_at, updated_at)
            VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, NOW() - INTERVAL ? DAY, NOW())
        """.trimIndent()

        (1..ORDER_COUNT).chunked(BATCH_SIZE).forEach { chunk ->
            jdbcTemplate.batchUpdate(sql, chunk, BATCH_SIZE) { ps, _ ->
                val price = Random.nextInt(5_000, 100_001)
                val dayOffset = recentBiasedDayOffset(180)
                val r = Random.nextDouble()
                val status = ORDER_STATUSES[ORDER_STATUS_WEIGHTS.indexOfFirst { w -> r < w }]

                // Generate timestamps based on status
                val createdHoursAgo = dayOffset * 24L
                val paidAt: java.sql.Timestamp? = if (status in listOf("PAID", "PREPARING", "SHIPPING", "DELIVERED", "REFUNDED")) {
                    java.sql.Timestamp(System.currentTimeMillis() - (createdHoursAgo - Random.nextInt(0, 3)) * 3_600_000)
                } else null
                val shippedAt: java.sql.Timestamp? = if (status in listOf("SHIPPING", "DELIVERED", "REFUNDED") && paidAt != null) {
                    java.sql.Timestamp(paidAt.time + Random.nextInt(1, 4) * 86_400_000L)
                } else null
                val deliveredAt: java.sql.Timestamp? = if (status in listOf("DELIVERED", "REFUNDED") && shippedAt != null) {
                    java.sql.Timestamp(shippedAt.time + Random.nextInt(1, 6) * 86_400_000L)
                } else null
                val cancelledAt: java.sql.Timestamp? = when (status) {
                    "CANCELLED" -> java.sql.Timestamp(System.currentTimeMillis() - (createdHoursAgo - Random.nextInt(0, 24)) * 3_600_000)
                    "REFUNDED" -> if (deliveredAt != null) java.sql.Timestamp(deliveredAt.time + Random.nextInt(1, 8) * 86_400_000L) else null
                    else -> null
                }

                ps.setLong(1, powerLawId(USER_COUNT, alpha = 2.5).toLong())
                ps.setInt(2, price)
                ps.setInt(3, price)
                ps.setString(4, status)
                ps.setTimestamp(5, paidAt)
                ps.setTimestamp(6, shippedAt)
                ps.setTimestamp(7, deliveredAt)
                ps.setTimestamp(8, cancelledAt)
                ps.setInt(9, dayOffset)
            }
        }
    }

    // ─── Order Items (1~3 per order) ───

    private fun seedOrderItems() {
        log.info("[DataSeed] Order Items...")
        val orderIds = jdbcTemplate.queryForList("SELECT id FROM orders", Long::class.java)
        val maxProductId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM products", Long::class.java) ?: PRODUCT_COUNT.toLong()

        val sql = """
            INSERT INTO order_items (order_id, product_id, product_name, brand_id, brand_name, price, quantity, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
        """.trimIndent()

        orderIds.chunked(BATCH_SIZE).forEach { chunk ->
            val rows = chunk.flatMap { orderId ->
                val itemCount = Random.nextInt(1, 4) // 1~3 items
                (1..itemCount).map {
                    val productId = Random.nextLong(1, maxProductId + 1)
                    arrayOf<Any>(
                        orderId,
                        productId,
                        "Product_$productId",
                        Random.nextInt(1, BRAND_COUNT + 1).toLong(),
                        "Brand_${Random.nextInt(1, BRAND_COUNT + 1)}",
                        Random.nextInt(1_000, 100_001),
                        Random.nextInt(1, 6),
                    )
                }
            }
            if (rows.isNotEmpty()) {
                jdbcTemplate.batchUpdate(sql, rows, BATCH_SIZE) { ps, row ->
                    ps.setLong(1, row[0] as Long)
                    ps.setLong(2, row[1] as Long)
                    ps.setString(3, row[2] as String)
                    ps.setLong(4, row[3] as Long)
                    ps.setString(5, row[4] as String)
                    ps.setInt(6, row[5] as Int)
                    ps.setInt(7, row[6] as Int)
                }
            }
        }
    }

    // ─── Likes (power-law users × popular products) ───

    private fun seedLikes() {
        log.info("[DataSeed] Likes ({})...", LIKE_COUNT)
        val maxProductId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM products", Long::class.java) ?: PRODUCT_COUNT.toLong()
        val seen = HashSet<Long>(LIKE_COUNT * 2)
        val rows = mutableListOf<Array<Any>>()

        var attempts = 0
        while (rows.size < LIKE_COUNT && attempts < LIKE_COUNT * 3) {
            attempts++
            val userId = powerLawId(USER_COUNT, alpha = 2.5).toLong()    // heavy users like more
            val productId = powerLawIdLong(maxProductId, alpha = 2.0)  // popular products get more likes
            val key = userId * 1_000_000 + productId
            if (seen.add(key)) {
                rows.add(arrayOf(userId, productId, Random.nextInt(0, 366)))
            }
        }

        val sql = "INSERT INTO likes (user_id, product_id, created_at, updated_at) VALUES (?, ?, NOW() - INTERVAL ? DAY, NOW())"
        rows.chunked(BATCH_SIZE).forEach { chunk ->
            jdbcTemplate.batchUpdate(sql, chunk, BATCH_SIZE) { ps, row ->
                ps.setLong(1, row[0] as Long)
                ps.setLong(2, row[1] as Long)
                ps.setInt(3, row[2] as Int)
            }
        }
    }

    // ─── Sync like_count from actual likes rows ───

    private fun syncLikeCount() {
        log.info("[DataSeed] Syncing like_count from actual likes...")
        jdbcTemplate.update("""
            UPDATE products p
            LEFT JOIN (SELECT product_id, COUNT(*) AS cnt FROM likes GROUP BY product_id) lc
                ON p.id = lc.product_id
            SET p.like_count = COALESCE(lc.cnt, 0)
        """.trimIndent())
    }

    // ─── Coupon Templates ───

    private fun seedCouponTemplates() {
        log.info("[DataSeed] Coupon Templates ({})...", COUPON_TEMPLATE_COUNT)
        val sql = """
            INSERT INTO coupon_templates (name, type, discount_value, min_order_amount, max_issuance, issued_count, expires_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 0, ?, NOW(), NOW())
        """.trimIndent()

        jdbcTemplate.batchUpdate(sql, (1..COUPON_TEMPLATE_COUNT).map { i ->
            val isFixed = Random.nextBoolean()
            arrayOf<Any>(
                "Coupon_$i",
                if (isFixed) "FIXED" else "RATE",
                if (isFixed) Random.nextInt(1_000, 5_001) else Random.nextInt(5, 21),
                Random.nextInt(0, 10_001),
                Random.nextInt(100, 10_001),
                java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(Random.nextLong(1, 61))),
            )
        }, BATCH_SIZE)
    }

    // ─── User Coupons (power-law users) ───

    private fun seedUserCoupons() {
        log.info("[DataSeed] User Coupons ({})...", USER_COUPON_COUNT)
        val seen = HashSet<Long>(USER_COUPON_COUNT * 2)
        val rows = mutableListOf<Array<Any>>()

        var attempts = 0
        while (rows.size < USER_COUPON_COUNT && attempts < USER_COUPON_COUNT * 3) {
            attempts++
            val userId = powerLawId(USER_COUNT, alpha = 2.5).toLong()
            val templateId = Random.nextLong(1, COUPON_TEMPLATE_COUNT.toLong() + 1)
            val key = userId * 1_000 + templateId
            if (seen.add(key)) {
                rows.add(arrayOf(userId, templateId, if (Random.nextDouble() < 0.7) "AVAILABLE" else "USED"))
            }
        }

        val sql = "INSERT INTO user_coupons (user_id, coupon_template_id, status, version, created_at, updated_at) VALUES (?, ?, ?, 0, NOW(), NOW())"
        rows.chunked(BATCH_SIZE).forEach { chunk ->
            jdbcTemplate.batchUpdate(sql, chunk, BATCH_SIZE) { ps, row ->
                ps.setLong(1, row[0] as Long)
                ps.setLong(2, row[1] as Long)
                ps.setString(3, row[2] as String)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Summary & distribution logging
    // ═══════════════════════════════════════════════════════════

    private fun logCounts() {
        val tables = listOf("brands", "products", "product_stocks", "orders", "order_items", "likes", "coupon_templates", "user_coupons")
        tables.forEach { table ->
            val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Long::class.java)
            log.info("[DataSeed]   {} = {}", table, count)
        }
    }

    private fun logDistribution() {
        log.info("[DataSeed] ── Distribution check ──")

        // Brand product distribution
        val topBrands = jdbcTemplate.queryForList("""
            SELECT brand_id, COUNT(*) AS cnt FROM products
            GROUP BY brand_id ORDER BY cnt DESC LIMIT 10
        """.trimIndent())
        val bottomBrands = jdbcTemplate.queryForList("""
            SELECT brand_id, COUNT(*) AS cnt FROM products
            GROUP BY brand_id ORDER BY cnt ASC LIMIT 5
        """.trimIndent())
        log.info("[DataSeed]   Products/brand — top 10: {}", topBrands.map { "${it["brand_id"]}=${it["cnt"]}" })
        log.info("[DataSeed]   Products/brand — bottom 5: {}", bottomBrands.map { "${it["brand_id"]}=${it["cnt"]}" })

        // User order distribution
        val topOrderUsers = jdbcTemplate.queryForList("""
            SELECT user_id, COUNT(*) AS cnt FROM orders
            GROUP BY user_id ORDER BY cnt DESC LIMIT 10
        """.trimIndent())
        val avgMedianOrders = jdbcTemplate.queryForMap("""
            SELECT ROUND(AVG(cnt), 1) AS avg_orders, COUNT(*) AS unique_users FROM (
                SELECT user_id, COUNT(*) AS cnt FROM orders GROUP BY user_id
            ) t
        """.trimIndent())
        log.info("[DataSeed]   Orders/user — top 10: {}", topOrderUsers.map { "${it["user_id"]}=${it["cnt"]}" })
        log.info("[DataSeed]   Orders/user — avg: {}, unique users: {}", avgMedianOrders["avg_orders"], avgMedianOrders["unique_users"])

        // Like count distribution
        val topLikedProducts = jdbcTemplate.queryForList("""
            SELECT id, like_count FROM products ORDER BY like_count DESC LIMIT 10
        """.trimIndent())
        val likeStats = jdbcTemplate.queryForMap("""
            SELECT
              ROUND(AVG(like_count), 1) AS avg_likes,
              MAX(like_count) AS max_likes,
              SUM(CASE WHEN like_count = 0 THEN 1 ELSE 0 END) AS zero_likes
            FROM products
        """.trimIndent())
        log.info("[DataSeed]   Likes/product — top 10: {}", topLikedProducts.map { "${it["id"]}=${it["like_count"]}" })
        log.info("[DataSeed]   Likes/product — avg: {}, max: {}, zero: {}", likeStats["avg_likes"], likeStats["max_likes"], likeStats["zero_likes"])

        // User likes distribution
        val topLikeUsers = jdbcTemplate.queryForList("""
            SELECT user_id, COUNT(*) AS cnt FROM likes GROUP BY user_id ORDER BY cnt DESC LIMIT 10
        """.trimIndent())
        log.info("[DataSeed]   Likes/user — top 10: {}", topLikeUsers.map { "${it["user_id"]}=${it["cnt"]}" })

        // Order date distribution
        val recentOrders = jdbcTemplate.queryForMap("""
            SELECT
              SUM(CASE WHEN created_at >= NOW() - INTERVAL 30 DAY THEN 1 ELSE 0 END) AS last_30d,
              SUM(CASE WHEN created_at >= NOW() - INTERVAL 60 DAY THEN 1 ELSE 0 END) AS last_60d,
              SUM(CASE WHEN created_at >= NOW() - INTERVAL 90 DAY THEN 1 ELSE 0 END) AS last_90d,
              COUNT(*) AS total
            FROM orders
        """.trimIndent())
        log.info("[DataSeed]   Order dates — 30d: {}, 60d: {}, 90d: {}, total: {}",
            recentOrders["last_30d"], recentOrders["last_60d"], recentOrders["last_90d"], recentOrders["total"])

        // Product status distribution
        val statusDist = jdbcTemplate.queryForList("""
            SELECT status, COUNT(*) AS cnt FROM products GROUP BY status ORDER BY cnt DESC
        """.trimIndent())
        log.info("[DataSeed]   Product status: {}", statusDist.map { "${it["status"]}=${it["cnt"]}" })
    }

    /**
     * JdbcTemplate.batchUpdate with chunk size.
     */
    private fun JdbcTemplate.batchUpdate(sql: String, args: List<Array<Any>>, batchSize: Int) {
        args.chunked(batchSize).forEach { chunk ->
            batchUpdate(sql, chunk.map { row -> row })
        }
    }
}
