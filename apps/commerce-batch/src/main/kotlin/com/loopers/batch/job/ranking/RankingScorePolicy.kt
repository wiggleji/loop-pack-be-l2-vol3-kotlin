package com.loopers.batch.job.ranking

/**
 * Batch 측 가중치 합산 점수 정책.
 *
 * 실시간 (commerce-streamer) 의 [WeightedSumScoreCalculator] 와 동일한 가중치를 사용하되,
 * batch 입력은 **누적 카운트** 이므로 order 항목에 `ln(amount+1)` 을 적용하지 않고
 * 단순 `salesCount` 에 0.7 을 곱한다. (batch 는 이벤트 단위가 아니라 합계 단위)
 *
 * Why constants here?
 *  - native SQL 의 `ORDER BY` 식과 Kotlin 검산식이 어긋나면 기준이 갈라진다.
 *    한 곳에서 정의해두고 SQL 문자열 빌드와 단위 테스트가 같은 값을 참조하게 한다.
 *  - 추후 가중치 동적 변경(`@ConfigurationProperties`)으로 확장 가능하나, 본 라운드 범위 밖.
 */
object RankingScorePolicy {

    const val VIEW_WEIGHT = 0.1
    const val LIKE_WEIGHT = 0.2
    const val SALES_WEIGHT = 0.7

    /**
     * native SQL 에 인라인 가능한 점수 식.
     * 동일한 컬럼명을 가진 [product_metrics] 를 가정한다.
     */
    const val SCORE_EXPR =
        "($VIEW_WEIGHT * view_count + $LIKE_WEIGHT * like_count + $SALES_WEIGHT * sales_count)"

    fun score(viewCount: Long, likeCount: Long, salesCount: Long): Double =
        VIEW_WEIGHT * viewCount + LIKE_WEIGHT * likeCount + SALES_WEIGHT * salesCount
}
