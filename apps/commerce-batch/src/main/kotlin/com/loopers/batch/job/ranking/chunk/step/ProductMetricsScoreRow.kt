package com.loopers.batch.job.ranking.chunk.step

/**
 * Reader 가 native query 로 가져오는 단일 행.
 * SQL 에서 `ROW_NUMBER() OVER (ORDER BY score DESC)` 로 부여된 1-based rank 를 그대로 가진다.
 *
 * Why DB-side ROW_NUMBER instead of Processor counter?
 *  - 청크 재시도(skip/retry) 시 Processor 가 가진 in-memory counter 가 어긋날 수 있다.
 *  - SQL 자체가 결정적인 rank 를 부여하면, Reader → Processor → Writer 어디서 retry 가 일어나도
 *    같은 row 는 같은 rank 를 갖는다 → 멱등.
 */
data class ProductMetricsScoreRow(
    val productId: Long,
    val rank: Int,
    val score: Double,
    val viewCount: Long,
    val likeCount: Long,
    val salesCount: Long,
    val salesAmount: Long,
)
