package com.loopers.application.ranking

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 랭킹 점수 계산에 사용되는 가중치.
 *
 * `ranking.weights.*` 프로퍼티로 주입되며, 운영 중 가중치 튜닝을 위해 외부화했다.
 * 가중치 변경의 효력은 "다음 윈도우(다음 일자 키)부터" 발생한다 — 과거 누적 점수에는 소급 적용되지 않는다.
 *
 * 합계가 정확히 1.0 일 필요는 없으나, 지표 간 상대적 중요도를 직관적으로 파악할 수 있도록
 * 합 1.0 기준으로 설정하는 것을 권장한다.
 */
@ConfigurationProperties(prefix = "ranking.weights")
data class RankingWeights(
    /** 조회 이벤트 가중치 (default 0.1) */
    val view: Double = 0.1,
    /** 좋아요 이벤트 가중치 (default 0.2) */
    val like: Double = 0.2,
    /** 주문 이벤트 가중치 (default 0.7) */
    val order: Double = 0.7,
)
