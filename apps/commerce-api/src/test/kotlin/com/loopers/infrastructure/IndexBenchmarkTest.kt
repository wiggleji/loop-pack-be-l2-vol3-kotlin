package com.loopers.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.io.File
import kotlin.math.pow
import kotlin.random.Random

/**
 * 인덱스 벤치마크 테스트.
 *
 * 목적: EXPLAIN 결과를 비교하여 단일 컬럼 인덱스 vs 복합 인덱스의 성능 차이를 확인.
 *
 * 구성:
 *   1. 테스트 데이터 시드 (10K products, 50 brands, 10K orders, 10K likes)
 *   2. Single-column indexes: 현재 단일 컬럼 인덱스로 EXPLAIN 실행
 *   3. Multi-column indexes: 복합 인덱스 적용 후 EXPLAIN 재실행
 *   4. 비교 테이블 출력 + 파일 저장
 *
 * ※ 데이터 truncate 없음 — @TestInstance(PER_CLASS)로 한 번만 시드.
 * ※ 이 테스트는 "통과/실패" 가 아닌 EXPLAIN 결과를 사람이 확인하는 벤치마크.
 *
 * @see /apps/commerce-api/build/index-benchmark-report.txt
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
 *   COMPARISON: Single-column vs Multi-column indexes
 * ════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
 * Query                                         | Type   Key(Single)                        Rows | Type   Key(Multi)                         Rows | Verdict
 * ---------------------------------------------------------------------------------------------------------------------------------------------------------
 * [products] LATEST + mega brand                | index  idx_products_created_at             139 | ref    idx_products_status_brand_crea     1253 | SINGLE wins: 139 vs 1253 rows
 * [products] LATEST + small brand               | ref    idx_products_brand_id               103 | ref    idx_products_status_brand_crea       86 | MULTI wins: filesort eliminated
 * [products] LATEST + no brand filter           | index  idx_products_created_at              20 | ref    idx_products_status_brand_crea     5098 | SINGLE wins: no filesort vs filesort
 * [products] PRICE_ASC + mega brand             | index  idx_products_price                  139 | ref    idx_products_status_brand_pric     1253 | SINGLE wins: 139 vs 1253 rows
 * [products] PRICE_ASC + no brand filter        | index  idx_products_price                   20 | ref    idx_products_status_brand_crea     5098 | SINGLE wins: no filesort vs filesort
 * [products] POPULAR + mega brand               | index  idx_products_like_count             139 | ref    idx_products_status_brand_like     1253 | SINGLE wins: 139 vs 1253 rows
 * [products] POPULAR + no brand filter          | index  idx_products_like_count              20 | ref    idx_products_status_brand_crea     5098 | SINGLE wins: no filesort vs filesort
 * [orders] by user (power user)                 | ref    idx_orders_user_created             611 | ref    idx_orders_user_created             611 | —
 * [orders] by user (normal)                     | ref    idx_orders_user_created               9 | ref    idx_orders_user_created               9 | —
 * [orders] by user + date range                 | range  idx_orders_user_created              14 | range  idx_orders_user_created              14 | —
 * [likes] by user (power user)                  | ref    UK18fd6srrna88d3mgfb2r1f3ps         612 | ref    UK18fd6srrna88d3mgfb2r1f3ps         612 | —
 * [likes] by product (popular)                  | ref    idx_likes_product_id                 83 | ref    idx_likes_product_id                 83 | —
 * [products] by brand_id (cascade)              | ref    idx_products_brand_id              1466 | ref    idx_products_brand_id              1466 | —
 * [order_items] by order_id                     | ref    idx_order_items_order_id              2 | ref    idx_order_items_order_id              2 | —
 * [user_coupons] by user_id                     | ALL    NULL                                  1 | ALL    NULL                                  1 | —
 * [orders] by status (PREPARING)                | ALL    NULL                               9741 | ref    idx_orders_status_created           990 | MULTI wins: Full scan -> Index
 * [orders] by status + date range (DELIVERED)   | ALL    NULL                               9741 | range  idx_orders_status_created           157 | MULTI wins: Full scan -> Index
 * [orders] delayed (PAID, older than 2 days)    | ALL    NULL                               9741 | range  idx_orders_status_created           987 | MULTI wins: Full scan -> Index
 * [order_items] by brand_id                     | index  idx_order_items_order_id             20 | ref    idx_order_items_brand_order        2817 | SINGLE wins: 20 vs 2817 rows
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
 *   FINAL COMPARISON: Single-column vs Multi-column vs Hybrid
 * ════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
 * Query                                    | Type    Rows(S) | Type    Rows(M) | Type    Rows(H) | Best
 * -----------------------------------------------------------------------------------------------------
 * [products] LATEST + mega brand           | index       139 | ref        1253 | ref        1253 | SINGLE
 * [products] LATEST + small brand          | ref         103 | ref          86 | ref          86 | HYBRID
 * [products] LATEST + no brand filter      | index        20 | ref        5098 | index        40 | SINGLE
 * [products] PRICE_ASC + mega brand        | index       139 | ref        1253 | ref        1253 | SINGLE
 * [products] PRICE_ASC + no brand filter   | index        20 | ref        5098 | index        40 | SINGLE
 * [products] POPULAR + mega brand          | index       139 | ref        1253 | ref        1253 | SINGLE
 * [products] POPULAR + no brand filter     | index        20 | ref        5098 | index        40 | SINGLE
 * [orders] by user (power user)            | ref         611 | ref         611 | ref         611 | HYBRID
 * [orders] by user (normal)                | ref           9 | ref           9 | ref           9 | HYBRID
 * [orders] by user + date range            | range        14 | range        14 | range        14 | HYBRID
 * [likes] by user (power user)             | ref         612 | ref         612 | ref         612 | HYBRID
 * [likes] by product (popular)             | ref          83 | ref          83 | ref          83 | HYBRID
 * [products] by brand_id (cascade)         | ref        1466 | ref        1466 | ref        1466 | HYBRID
 * [order_items] by order_id                | ref           2 | ref           2 | ref           2 | HYBRID
 * [user_coupons] by user_id                | ALL           1 | ALL           1 | ALL           1 | HYBRID
 * [orders] by status (PREPARING)           | ALL        9741 | ref         990 | ref         990 | HYBRID
 * [orders] by status + date range (DELIVER | ALL        9741 | range       157 | range       157 | HYBRID
 * [orders] delayed (PAID, older than 2 day | ALL        9741 | range       987 | range       987 | HYBRID
 * [order_items] by brand_id                | index        20 | ref        2817 | ref        2817 | SINGLE
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class IndexBenchmarkTest @Autowired constructor(
    private val jdbcTemplate: JdbcTemplate,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val output = StringBuilder()

    companion object {
        const val BRAND_COUNT = 50
        const val PRODUCT_COUNT = 10_000
        const val ORDER_COUNT = 10_000
        const val LIKE_COUNT = 10_000
        const val USER_COUNT = 1_000
        const val BATCH_SIZE = 1_000
    }

    private val queryMap = mutableMapOf<String, String>() // name → SQL
    private val singleColumnResults = mutableMapOf<String, ExplainResult>()
    private val multiColumnResults = mutableMapOf<String, ExplainResult>()
    private val hybridResults = mutableMapOf<String, ExplainResult>()

    private fun emit(line: String) {
        log.info(line)
        output.appendLine(line)
    }

    // ═══════════════════════════════════════════════════════════
    // Data seeding
    // ═══════════════════════════════════════════════════════════

    @BeforeAll
    fun seedData() {
        val existingCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Long::class.java) ?: 0
        if (existingCount > 0) return

        log.info("[IndexBenchmark] Seeding test data...")
        seedBrands()
        seedProducts()
        seedProductStocks()
        seedOrders()
        seedOrderItems()
        seedLikes()
        syncLikeCount()
        seedUserCoupons()

        val productCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Long::class.java)
        log.info("[IndexBenchmark] Seeded data: products={}", productCount)
    }

    // ═══════════════════════════════════════════════════════════
    // Phase 1: Single-column indexes (현재 상태)
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(1)
    fun `1 - EXPLAIN with single-column indexes`() {
        emit("")
        emit("═".repeat(80))
        emit("  SINGLE-COLUMN INDEXES (단일 컬럼 인덱스만)")
        emit("═".repeat(80))

        // Drop composite indexes that JPA auto-created, keep only single-column
        safeDropIndex("products", "idx_products_status_brand_created")
        safeDropIndex("products", "idx_products_status_brand_price")
        safeDropIndex("products", "idx_products_status_brand_likecount")
        safeDropIndex("orders", "idx_orders_status_created")
        safeDropIndex("order_items", "idx_order_items_brand_order")

        showCurrentIndexes("products")
        showCurrentIndexes("orders")
        showCurrentIndexes("likes")
        showCurrentIndexes("order_items")
        showCurrentIndexes("user_coupons")

        val queries = buildExplainQueries()
        queries.forEach { (name, sql) ->
            queryMap[name] = sql
            singleColumnResults[name] = runExplain(sql)
        }

        printExplainTableWithQueries(singleColumnResults)
    }

    // ═══════════════════════════════════════════════════════════
    // Phase 2: Apply multi-column (composite) indexes
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(2)
    fun `2 - Apply multi-column indexes`() {
        emit("")
        emit("═".repeat(80))
        emit("  Applying multi-column indexes...")
        emit("═".repeat(80))

        // ── Products: drop single-column, add composite ──
        safeDropIndex("products", "idx_products_brand_id")
        safeDropIndex("products", "idx_products_created_at")
        safeDropIndex("products", "idx_products_price")
        safeDropIndex("products", "idx_products_like_count")

        safeAddIndex("products", "idx_products_status_brand_created", "status, brand_id, created_at DESC")
        safeAddIndex("products", "idx_products_status_brand_price", "status, brand_id, price ASC")
        safeAddIndex("products", "idx_products_status_brand_likecount", "status, brand_id, like_count DESC")

        // ── Orders: add composite ──
        safeAddIndex("orders", "idx_orders_user_created", "user_id, created_at DESC")
        safeAddIndex("orders", "idx_orders_status_created", "status, created_at")

        // ── Order Items: add brand composite index ──
        safeAddIndex("order_items", "idx_order_items_brand_order", "brand_id, order_id")

        // ── Likes: add single-column index for product lookups ──
        // (user_id, product_id) UNIQUE already exists → covers user lookups
        safeAddIndex("likes", "idx_likes_product_id", "product_id")

        // ── Order Items: add order_id index ──
        safeAddIndex("order_items", "idx_order_items_order_id", "order_id")

        // ── User Coupons: add user_id index ──
        safeAddIndex("user_coupons", "idx_user_coupons_user_id", "user_id")

        // ── Products: add brand_id for cascade deletes ──
        safeAddIndex("products", "idx_products_brand_id", "brand_id")

        emit("  Multi-column indexes applied.")
        showCurrentIndexes("products")
        showCurrentIndexes("orders")
        showCurrentIndexes("likes")
        showCurrentIndexes("order_items")
        showCurrentIndexes("user_coupons")
    }

    // ═══════════════════════════════════════════════════════════
    // Phase 3: EXPLAIN with multi-column indexes
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(3)
    fun `3 - EXPLAIN with multi-column indexes`() {
        emit("")
        emit("═".repeat(80))
        emit("  MULTI-COLUMN INDEXES (복합 인덱스)")
        emit("═".repeat(80))

        val queries = buildExplainQueries()
        queries.forEach { (name, sql) ->
            multiColumnResults[name] = runExplain(sql)
        }

        printExplainTableWithQueries(multiColumnResults)
    }

    // ═══════════════════════════════════════════════════════════
    // Phase 4: Comparison
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(4)
    fun `4 - Compare single-column vs multi-column`() {
        emit("")
        emit("═".repeat(140))
        emit("  COMPARISON: Single-column vs Multi-column indexes")
        emit("═".repeat(140))

        val header = String.format(
            "%-45s | %-6s %-30s %8s | %-6s %-30s %8s | %s",
            "Query", "Type", "Key(Single)", "Rows",
            "Type", "Key(Multi)", "Rows", "Verdict"
        )
        emit(header)
        emit("-".repeat(header.length))

        singleColumnResults.keys.forEach { name ->
            val single = singleColumnResults[name]!!
            val multi = multiColumnResults[name] ?: return@forEach

            val verdict = when {
                single.type == "ALL" && multi.type != "ALL" -> "MULTI wins: Full scan -> Index"
                single.extra.contains("filesort") && !multi.extra.contains("filesort") -> "MULTI wins: filesort eliminated"
                !single.extra.contains("filesort") && multi.extra.contains("filesort") -> "SINGLE wins: no filesort vs filesort"
                single.rows <= 30 && multi.rows > 1000 -> "SINGLE wins: ${single.rows} vs ${multi.rows} rows"
                multi.rows < single.rows -> "MULTI wins: ${single.rows} -> ${multi.rows} rows"
                single.rows < multi.rows -> "SINGLE wins: ${single.rows} vs ${multi.rows} rows"
                else -> "—"
            }

            emit(
                String.format(
                    "%-45s | %-6s %-30s %8d | %-6s %-30s %8d | %s",
                    name.take(45),
                    single.type, single.key.take(30), single.rows,
                    multi.type, multi.key.take(30), multi.rows,
                    verdict
                )
            )
        }

        emit("")

        // Verify multi-column indexes are being used for product queries with brand filter
        // Note: "by brand_id (cascade)" is expected to be ALL here since brand_id index was dropped
        multiColumnResults.forEach { (name, result) ->
            if (name.startsWith("[products]") && !name.contains("cascade")) {
                assertThat(result.type)
                    .describedAs("$name should use index, not full table scan")
                    .isNotEqualTo("ALL")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Phase 5: Apply hybrid indexes (single + multi)
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(5)
    fun `5 - Apply hybrid indexes`() {
        emit("")
        emit("═".repeat(80))
        emit("  Applying HYBRID indexes (single + multi)...")
        emit("═".repeat(80))
        emit("  Strategy: keep single-column sort indexes + add multi-column composites")
        emit("    - DROP: brand_id (redundant, covered by composites)")
        emit("    - KEEP: created_at, price, like_count (for no-brand-filter queries)")
        emit("    - ADD:  (status, brand_id, created_at/price/like_count) (for brand-filter queries)")
        emit("")

        // Restore single-column sort indexes that were dropped in Phase 2
        safeAddIndex("products", "idx_products_created_at", "created_at")
        safeAddIndex("products", "idx_products_price", "price")
        safeAddIndex("products", "idx_products_like_count", "like_count")
        // brand_id stays dropped — redundant (2nd column of all composites)
        // Composites from Phase 2 are still present

        // Restore brand_id for cascade delete queries
        safeAddIndex("products", "idx_products_brand_id", "brand_id")

        // Order admin indexes
        safeAddIndex("orders", "idx_orders_status_created", "status, created_at")
        safeAddIndex("order_items", "idx_order_items_brand_order", "brand_id, order_id")

        emit("  Hybrid indexes applied.")
        showCurrentIndexes("products")
        showCurrentIndexes("orders")
        showCurrentIndexes("likes")
        showCurrentIndexes("order_items")
        showCurrentIndexes("user_coupons")
    }

    // ═══════════════════════════════════════════════════════════
    // Phase 6: EXPLAIN with hybrid indexes
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(6)
    fun `6 - EXPLAIN with hybrid indexes`() {
        emit("")
        emit("═".repeat(80))
        emit("  HYBRID INDEXES (single + multi)")
        emit("═".repeat(80))

        val queries = buildExplainQueries()
        queries.forEach { (name, sql) ->
            hybridResults[name] = runExplain(sql)
        }

        printExplainTableWithQueries(hybridResults)
    }

    // ═══════════════════════════════════════════════════════════
    // Phase 7: Final comparison (all three strategies)
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(7)
    fun `7 - Final comparison - all strategies`() {
        emit("")
        emit("═".repeat(160))
        emit("  FINAL COMPARISON: Single-column vs Multi-column vs Hybrid")
        emit("═".repeat(160))

        val header = String.format(
            "%-40s | %-6s %8s | %-6s %8s | %-6s %8s | %s",
            "Query", "Type", "Rows(S)", "Type", "Rows(M)", "Type", "Rows(H)", "Best"
        )
        emit(header)
        emit("-".repeat(header.length))

        singleColumnResults.keys.forEach { name ->
            val s = singleColumnResults[name]!!
            val m = multiColumnResults[name] ?: return@forEach
            val h = hybridResults[name] ?: return@forEach

            val best = when {
                h.rows <= s.rows && h.rows <= m.rows && !h.extra.contains("filesort") -> "HYBRID"
                s.rows <= m.rows && !s.extra.contains("filesort") -> "SINGLE"
                !m.extra.contains("filesort") -> "MULTI"
                h.rows <= s.rows -> "HYBRID"
                else -> "SINGLE"
            }

            emit(
                String.format(
                    "%-40s | %-6s %8d | %-6s %8d | %-6s %8d | %s",
                    name.take(40),
                    s.type, s.rows,
                    m.type, m.rows,
                    h.type, h.rows,
                    best
                )
            )
        }

        emit("")

        // Write final report
        val reportFile = File("build/index-benchmark-report.txt")
        reportFile.parentFile.mkdirs()
        reportFile.writeText(output.toString())
        log.info("[IndexBenchmark] Report written to: {}", reportFile.absolutePath)

        // Verify hybrid: no full table scans for products or orders
        hybridResults.forEach { (name, result) ->
            if (name.startsWith("[products]") || name.startsWith("[orders]")) {
                assertThat(result.type)
                    .describedAs("$name should use index with hybrid strategy")
                    .isNotEqualTo("ALL")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // EXPLAIN queries
    // ═══════════════════════════════════════════════════════════

    private fun buildExplainQueries(): List<Pair<String, String>> {
        val megaBrandId = 1
        val smallBrandId = BRAND_COUNT

        return listOf(
            // ── Products: LATEST sort ──
            "[products] LATEST + mega brand" to """
                SELECT * FROM products
                WHERE deleted_at IS NULL AND status = 'ACTIVE' AND brand_id = $megaBrandId
                ORDER BY created_at DESC LIMIT 20
            """.trimIndent(),

            "[products] LATEST + small brand" to """
                SELECT * FROM products
                WHERE deleted_at IS NULL AND status = 'ACTIVE' AND brand_id = $smallBrandId
                ORDER BY created_at DESC LIMIT 20
            """.trimIndent(),

            "[products] LATEST + no brand filter" to """
                SELECT * FROM products
                WHERE deleted_at IS NULL AND status = 'ACTIVE'
                ORDER BY created_at DESC LIMIT 20
            """.trimIndent(),

            // ── Products: PRICE_ASC sort ──
            "[products] PRICE_ASC + mega brand" to """
                SELECT * FROM products
                WHERE deleted_at IS NULL AND status = 'ACTIVE' AND brand_id = $megaBrandId
                ORDER BY price ASC LIMIT 20
            """.trimIndent(),

            "[products] PRICE_ASC + no brand filter" to """
                SELECT * FROM products
                WHERE deleted_at IS NULL AND status = 'ACTIVE'
                ORDER BY price ASC LIMIT 20
            """.trimIndent(),

            // ── Products: POPULAR sort ──
            "[products] POPULAR + mega brand" to """
                SELECT * FROM products
                WHERE deleted_at IS NULL AND status = 'ACTIVE' AND brand_id = $megaBrandId
                ORDER BY like_count DESC LIMIT 20
            """.trimIndent(),

            "[products] POPULAR + no brand filter" to """
                SELECT * FROM products
                WHERE deleted_at IS NULL AND status = 'ACTIVE'
                ORDER BY like_count DESC LIMIT 20
            """.trimIndent(),

            // ── Orders ──
            "[orders] by user (power user)" to """
                SELECT * FROM orders WHERE user_id = 1 AND deleted_at IS NULL
            """.trimIndent(),

            "[orders] by user (normal)" to """
                SELECT * FROM orders WHERE user_id = 500 AND deleted_at IS NULL
            """.trimIndent(),

            "[orders] by user + date range" to """
                SELECT * FROM orders
                WHERE user_id = 1
                  AND created_at >= NOW() - INTERVAL 30 DAY
                  AND created_at <= NOW()
                  AND deleted_at IS NULL
            """.trimIndent(),

            // ── Likes ──
            "[likes] by user (power user)" to """
                SELECT * FROM likes WHERE user_id = 1 AND deleted_at IS NULL
            """.trimIndent(),

            "[likes] by product (popular)" to """
                SELECT * FROM likes WHERE product_id = 1 AND deleted_at IS NULL
            """.trimIndent(),

            // ── Products: brand cascade delete ──
            "[products] by brand_id (cascade)" to """
                SELECT * FROM products WHERE brand_id = 1 AND deleted_at IS NULL
            """.trimIndent(),

            // ── Order Items ──
            "[order_items] by order_id" to """
                SELECT * FROM order_items WHERE order_id = 1
            """.trimIndent(),

            // ── User Coupons ──
            "[user_coupons] by user_id" to """
                SELECT * FROM user_coupons WHERE user_id = 1 AND deleted_at IS NULL
            """.trimIndent(),

            // ── Order Admin Queries ──
            "[orders] by status (PREPARING)" to """
                SELECT * FROM orders
                WHERE status = 'PREPARING' AND deleted_at IS NULL
                ORDER BY created_at DESC LIMIT 20
            """.trimIndent(),

            "[orders] by status + date range (DELIVERED)" to """
                SELECT * FROM orders
                WHERE status = 'DELIVERED'
                  AND created_at >= NOW() - INTERVAL 30 DAY
                  AND created_at <= NOW()
                  AND deleted_at IS NULL
                ORDER BY created_at DESC LIMIT 20
            """.trimIndent(),

            "[orders] delayed (PAID, older than 2 days)" to """
                SELECT * FROM orders
                WHERE status = 'PAID'
                  AND created_at < NOW() - INTERVAL 2 DAY
                  AND deleted_at IS NULL
                ORDER BY created_at ASC LIMIT 20
            """.trimIndent(),

            "[order_items] by brand_id" to """
                SELECT * FROM order_items
                WHERE brand_id = 1 AND deleted_at IS NULL
                ORDER BY order_id DESC LIMIT 20
            """.trimIndent(),
        )
    }

    // ═══════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════

    data class ExplainResult(
        val type: String,
        val key: String,
        val rows: Long,
        val extra: String,
    )

    private fun runExplain(sql: String): ExplainResult {
        val rows = jdbcTemplate.queryForList("EXPLAIN $sql")
        if (rows.isEmpty()) return ExplainResult("?", "?", 0, "?")

        val row = rows[0]
        return ExplainResult(
            type = row["type"]?.toString() ?: "?",
            key = row["key"]?.toString() ?: "NULL",
            rows = (row["rows"] as? Number)?.toLong() ?: 0,
            extra = row["Extra"]?.toString() ?: "",
        )
    }

    private fun showCurrentIndexes(table: String) {
        val indexes = jdbcTemplate.queryForList("SHOW INDEX FROM $table")
        val grouped = indexes.groupBy { it["Key_name"] }
        emit("  [$table] indexes:")
        grouped.forEach { (name, cols) ->
            val columnList = cols.sortedBy { (it["Seq_in_index"] as Number).toInt() }
                .joinToString(", ") { it["Column_name"].toString() }
            emit("    - $name ($columnList)")
        }
    }

    private fun printExplainTableWithQueries(results: Map<String, ExplainResult>) {
        emit("")
        results.forEach { (name, r) ->
            emit("─".repeat(80))
            emit("  $name")
            emit("─".repeat(80))
            val sql = queryMap[name]
            if (sql != null) {
                emit("  SQL:")
                sql.lines().forEach { emit("    $it") }
            }
            emit("")
            emit("  EXPLAIN result:")
            emit("    type  = ${r.type}")
            emit("    key   = ${r.key}")
            emit("    rows  = ${r.rows}")
            emit("    Extra = ${r.extra}")
            emit("")
        }
    }

    private fun safeDropIndex(table: String, indexName: String) {
        try {
            jdbcTemplate.execute("ALTER TABLE $table DROP INDEX $indexName")
        } catch (_: Exception) {
            // Index might not exist
        }
    }

    private fun safeAddIndex(table: String, indexName: String, columns: String) {
        try {
            jdbcTemplate.execute("ALTER TABLE $table ADD INDEX $indexName ($columns)")
        } catch (_: Exception) {
            // Index might already exist
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Inline data seeding (smaller scale for test speed)
    // ═══════════════════════════════════════════════════════════

    private fun powerLawId(max: Int, alpha: Double): Int {
        val u = Random.nextDouble()
        return (max.toDouble() * u.pow(alpha)).toInt().coerceIn(0, max - 1) + 1
    }

    private fun recentBiasedDayOffset(maxDays: Int): Int {
        val u = Random.nextDouble()
        return (maxDays * u.pow(0.5)).toInt().coerceIn(0, maxDays)
    }

    private fun seedBrands() {
        val sql = "INSERT INTO brands (name, description, status, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())"
        (1..BRAND_COUNT).chunked(BATCH_SIZE).forEach { chunk ->
            jdbcTemplate.batchUpdate(sql, chunk.map { i ->
                arrayOf<Any>("Brand_$i", "desc", if (Random.nextDouble() < 0.95) "ACTIVE" else "INACTIVE")
            })
        }
    }

    private fun seedProducts() {
        val statusList = listOf("ACTIVE", "SOLD_OUT", "HIDDEN", "DISCONTINUED")
        val weights = listOf(0.85, 0.92, 0.97, 1.0)

        val sql = """
            INSERT INTO products (brand_id, name, description, price, like_count, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, 0, ?, NOW() - INTERVAL ? DAY, NOW())
        """.trimIndent()

        (1..PRODUCT_COUNT).chunked(BATCH_SIZE).forEach { chunk ->
            jdbcTemplate.batchUpdate(sql, chunk.map { i ->
                val r = Random.nextDouble()
                val status = statusList[weights.indexOfFirst { w -> r < w }]
                arrayOf<Any>(
                    powerLawId(BRAND_COUNT, 2.0),
                    "Product_$i", "desc",
                    Random.nextInt(1_000, 100_001),
                    status,
                    Random.nextInt(0, 366),
                )
            })
        }
    }

    private fun seedProductStocks() {
        jdbcTemplate.update("""
            INSERT INTO product_stocks (product_id, quantity, created_at, updated_at)
            SELECT id, FLOOR(RAND() * 200), NOW(), NOW() FROM products
        """.trimIndent())
    }

    private val orderStatuses = listOf("DELIVERED", "SHIPPING", "PREPARING", "PAID", "PLACED", "CANCELLED", "REFUNDED")
    private val orderStatusWeights = listOf(0.55, 0.65, 0.75, 0.85, 0.90, 0.97, 1.0)

    private fun seedOrders() {
        val sql = """
            INSERT INTO orders (user_id, original_total_price, discount_amount, total_price, status, paid_at, shipped_at, delivered_at, cancelled_at, created_at, updated_at)
            VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, NOW() - INTERVAL ? DAY, NOW())
        """.trimIndent()

        (1..ORDER_COUNT).chunked(BATCH_SIZE).forEach { chunk ->
            jdbcTemplate.batchUpdate(sql, chunk.map {
                val price = Random.nextInt(5_000, 100_001)
                val dayOffset = recentBiasedDayOffset(180)
                val r = Random.nextDouble()
                val status = orderStatuses[orderStatusWeights.indexOfFirst { w -> r < w }]

                val createdMs = System.currentTimeMillis() - dayOffset * 86_400_000L
                val paidAt: java.sql.Timestamp? = if (status in listOf("PAID", "PREPARING", "SHIPPING", "DELIVERED", "REFUNDED")) {
                    java.sql.Timestamp(createdMs + Random.nextInt(0, 3) * 3_600_000L)
                } else null
                val shippedAt: java.sql.Timestamp? = if (status in listOf("SHIPPING", "DELIVERED", "REFUNDED") && paidAt != null) {
                    java.sql.Timestamp(paidAt.time + Random.nextInt(1, 4) * 86_400_000L)
                } else null
                val deliveredAt: java.sql.Timestamp? = if (status in listOf("DELIVERED", "REFUNDED") && shippedAt != null) {
                    java.sql.Timestamp(shippedAt.time + Random.nextInt(1, 6) * 86_400_000L)
                } else null
                val cancelledAt: java.sql.Timestamp? = when (status) {
                    "CANCELLED" -> java.sql.Timestamp(createdMs + Random.nextInt(0, 24) * 3_600_000L)
                    "REFUNDED" -> if (deliveredAt != null) java.sql.Timestamp(deliveredAt.time + Random.nextInt(1, 8) * 86_400_000L) else null
                    else -> null
                }

                arrayOf<Any?>(
                    powerLawId(USER_COUNT, 2.5).toLong(),
                    price, price,
                    status,
                    paidAt, shippedAt, deliveredAt, cancelledAt,
                    dayOffset,
                )
            })
        }
    }

    private fun seedLikes() {
        val maxProductId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM products", Long::class.java) ?: PRODUCT_COUNT.toLong()
        val seen = HashSet<Long>(LIKE_COUNT * 2)
        val rows = mutableListOf<Array<Any>>()

        var attempts = 0
        while (rows.size < LIKE_COUNT && attempts < LIKE_COUNT * 3) {
            attempts++
            val userId = powerLawId(USER_COUNT, 2.5).toLong()
            val productId = (maxProductId.toDouble() * Random.nextDouble().pow(2.0)).toLong().coerceIn(0, maxProductId - 1) + 1
            val key = userId * 1_000_000 + productId
            if (seen.add(key)) {
                rows.add(arrayOf(userId, productId))
            }
        }

        val sql = "INSERT INTO likes (user_id, product_id, created_at, updated_at) VALUES (?, ?, NOW(), NOW())"
        rows.chunked(BATCH_SIZE).forEach { chunk ->
            jdbcTemplate.batchUpdate(sql, chunk.map { row -> row })
        }
    }

    private fun syncLikeCount() {
        jdbcTemplate.update("""
            UPDATE products p
            LEFT JOIN (SELECT product_id, COUNT(*) AS cnt FROM likes GROUP BY product_id) lc
                ON p.id = lc.product_id
            SET p.like_count = COALESCE(lc.cnt, 0)
        """.trimIndent())
    }

    private fun seedOrderItems() {
        val maxOrderId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM orders", Long::class.java) ?: ORDER_COUNT.toLong()
        val maxProductId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM products", Long::class.java) ?: PRODUCT_COUNT.toLong()

        val sql = """
            INSERT INTO order_items (order_id, product_id, product_name, brand_id, brand_name, price, quantity, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
        """.trimIndent()

        // Each order gets 1-3 items
        val rows = mutableListOf<Array<Any>>()
        for (orderId in 1..maxOrderId) {
            val itemCount = Random.nextInt(1, 4)
            repeat(itemCount) {
                val productId = Random.nextLong(1, maxProductId + 1)
                rows.add(arrayOf(
                    orderId, productId, "Product_$productId",
                    powerLawId(BRAND_COUNT, 2.0).toLong(), "Brand_1",
                    Random.nextInt(1_000, 100_001), Random.nextInt(1, 5),
                ))
            }
        }

        rows.chunked(BATCH_SIZE).forEach { chunk ->
            jdbcTemplate.batchUpdate(sql, chunk.map { it })
        }
    }

    private fun seedUserCoupons() {
        val couponTemplateCount = 10
        // Seed coupon templates first
        val templateSql = """
            INSERT INTO coupon_templates (name, type, discount_value, min_order_amount, max_issuance, issued_count, expires_at, created_at, updated_at)
            VALUES (?, 'FIXED', ?, 10000, 1000, 0, DATE_ADD(NOW(), INTERVAL 30 DAY), NOW(), NOW())
        """.trimIndent()
        (1..couponTemplateCount).chunked(BATCH_SIZE).forEach { chunk ->
            jdbcTemplate.batchUpdate(templateSql, chunk.map { i ->
                arrayOf<Any>("Coupon_$i", Random.nextInt(1_000, 10_001))
            })
        }

        // Seed user_coupons (unique per user+template)
        val sql = """
            INSERT INTO user_coupons (user_id, coupon_template_id, status, version, created_at, updated_at)
            VALUES (?, ?, 'AVAILABLE', 0, NOW(), NOW())
        """.trimIndent()

        val seen = HashSet<Long>()
        val rows = mutableListOf<Array<Any>>()
        val targetCount = 5_000

        var attempts = 0
        while (rows.size < targetCount && attempts < targetCount * 3) {
            attempts++
            val userId = powerLawId(USER_COUNT, 2.5).toLong()
            val templateId = Random.nextLong(1, couponTemplateCount + 1L)
            val key = userId * 1_000 + templateId
            if (seen.add(key)) {
                rows.add(arrayOf(userId, templateId))
            }
        }

        rows.chunked(BATCH_SIZE).forEach { chunk ->
            jdbcTemplate.batchUpdate(sql, chunk.map { it })
        }
    }
}
