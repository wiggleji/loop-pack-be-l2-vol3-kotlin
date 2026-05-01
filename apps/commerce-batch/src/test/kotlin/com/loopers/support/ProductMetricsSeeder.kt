package com.loopers.support

import com.loopers.batch.job.ranking.RankingScorePolicy
import com.loopers.infrastructure.persistence.metrics.ProductMetricsEntity
import com.loopers.infrastructure.persistence.metrics.ProductMetricsJpaRepository
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.ZonedDateTime
import kotlin.random.Random

/**
 * 테스트/벤치마크용 product_metrics seed 헬퍼.
 *
 * 결정적 분포 (`seed` 고정) 로 N 개 row 를 적재해 동일한 입력에 대한 출력 검증을 가능하게 한다.
 *
 * 대용량(>= 50k) 에서는 JPA `saveAll` 이 Hibernate batch_size 미설정 + show-sql=true 환경에서
 * 비현실적으로 느려지므로 JdbcTemplate.batchUpdate 로 fallback 한다.
 */
@Component
class ProductMetricsSeeder(
    private val repository: ProductMetricsJpaRepository,
    private val jdbcTemplate: JdbcTemplate,
) {

    /**
     * @param count 생성할 product 수
     * @param seed  Random seed (고정 시 재현 가능)
     * @return seed 된 product 의 (productId, expectedScore) — 검증용
     */
    fun seedRandom(count: Int, seed: Long = 42L): List<Pair<Long, Double>> {
        repository.deleteAllInBatch()
        val rng = Random(seed)
        val rows = (1..count).map { idx ->
            val productId = idx.toLong()
            val viewCount = rng.nextLong(0, 10_000)
            val likeCount = rng.nextLong(0, 1_000)
            val salesCount = rng.nextLong(0, 100)
            val salesAmount = salesCount * rng.nextLong(1_000, 50_000)
            Row(productId, viewCount, likeCount, salesCount, salesAmount)
        }

        if (count >= JDBC_BATCH_THRESHOLD) {
            insertViaJdbc(rows)
        } else {
            repository.saveAll(rows.map { it.toEntity() })
        }

        return rows.map { it.productId to RankingScorePolicy.score(it.viewCount, it.likeCount, it.salesCount) }
    }

    private fun insertViaJdbc(rows: List<Row>) {
        val now = Timestamp.from(ZonedDateTime.now().toInstant())
        val sql = """
            INSERT INTO product_metrics
                (product_id, view_count, like_count, sales_count, sales_amount, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 0, ?, ?)
        """.trimIndent()

        rows.chunked(JDBC_BATCH_CHUNK).forEach { chunk ->
            jdbcTemplate.batchUpdate(sql, object : BatchPreparedStatementSetter {
                override fun setValues(ps: PreparedStatement, i: Int) {
                    val row = chunk[i]
                    ps.setLong(1, row.productId)
                    ps.setLong(2, row.viewCount)
                    ps.setLong(3, row.likeCount)
                    ps.setLong(4, row.salesCount)
                    ps.setLong(5, row.salesAmount)
                    ps.setTimestamp(6, now)
                    ps.setTimestamp(7, now)
                }

                override fun getBatchSize(): Int = chunk.size
            })
        }
    }

    private data class Row(
        val productId: Long,
        val viewCount: Long,
        val likeCount: Long,
        val salesCount: Long,
        val salesAmount: Long,
    ) {
        fun toEntity() = ProductMetricsEntity(
            productId = productId,
            viewCount = viewCount,
            likeCount = likeCount,
            salesCount = salesCount,
            salesAmount = salesAmount,
        )
    }

    companion object {
        private const val JDBC_BATCH_THRESHOLD = 50_000
        private const val JDBC_BATCH_CHUNK = 1_000
    }
}
