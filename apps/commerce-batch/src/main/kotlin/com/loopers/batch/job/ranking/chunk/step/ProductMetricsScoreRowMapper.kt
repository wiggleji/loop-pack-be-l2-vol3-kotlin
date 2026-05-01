package com.loopers.batch.job.ranking.chunk.step

import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet

/** native query 결과를 [ProductMetricsScoreRow] 로 매핑한다. */
class ProductMetricsScoreRowMapper : RowMapper<ProductMetricsScoreRow> {
    override fun mapRow(rs: ResultSet, rowNum: Int): ProductMetricsScoreRow {
        return ProductMetricsScoreRow(
            productId = rs.getLong("product_id"),
            rank = rs.getInt("rank_no"),
            score = rs.getDouble("score"),
            viewCount = rs.getLong("view_count"),
            likeCount = rs.getLong("like_count"),
            salesCount = rs.getLong("sales_count"),
            salesAmount = rs.getLong("sales_amount"),
        )
    }
}
