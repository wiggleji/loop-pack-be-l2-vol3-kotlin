package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingEvent
import com.loopers.domain.ranking.ScoreCalculator
import org.springframework.stereotype.Component
import kotlin.math.ln1p

/**
 * 가중치 합산(Weighted Sum) 기반 ScoreCalculator 기본 구현.
 *
 * - View / Like : 단일 이벤트당 weight 만큼의 점수
 * - Order       : `weight * ln(amount + 1)` 로 로그 정규화
 *                 → 고가 상품 1건이 점수를 독식하는 것을 방지 (Log Saturation)
 * - Unliked     : Like 의 음수 — 좋아요 취소 시 점수 회수
 *
 * Why log scaling on order?
 *  - 조회/좋아요는 카운트 단위(보통 1~수천)지만 주문 amount 는 단위가 다르다 (수천~수백만 원).
 *  - 단순 곱셈 시 고가 상품 1건이 조회 수만 건을 압도해 랭킹이 기형적으로 왜곡된다.
 *  - 로그 변환으로 큰 값일수록 증가폭을 둔화시켜 지표 간 스케일을 맞춘다.
 */
@Component
class WeightedSumScoreCalculator(
    private val weights: RankingWeights,
) : ScoreCalculator {

    override fun scoreFor(event: RankingEvent): Double = when (event) {
        is RankingEvent.Viewed -> weights.view
        is RankingEvent.Liked -> weights.like
        is RankingEvent.Unliked -> -weights.like
        is RankingEvent.Ordered -> weights.order * ln1p(event.amount.toDouble())
    }
}
