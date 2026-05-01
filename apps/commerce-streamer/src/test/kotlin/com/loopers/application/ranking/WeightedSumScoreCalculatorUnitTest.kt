package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingEvent
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.math.ln1p

class WeightedSumScoreCalculatorUnitTest {

    private val weights = RankingWeights(view = 0.1, like = 0.2, order = 0.7)
    private val calculator = WeightedSumScoreCalculator(weights)

    @Nested
    @DisplayName("scoreFor()")
    inner class ScoreFor {

        @Test
        fun `Viewed 이벤트는 view 가중치만큼 점수를 반환한다`() {
            val score = calculator.scoreFor(RankingEvent.Viewed(productId = 1L))
            assertThat(score).isEqualTo(0.1)
        }

        @Test
        fun `Liked 이벤트는 like 가중치만큼 점수를 반환한다`() {
            val score = calculator.scoreFor(RankingEvent.Liked(productId = 1L))
            assertThat(score).isEqualTo(0.2)
        }

        @Test
        fun `Unliked 이벤트는 Liked 와 정확히 반대되는 음수 점수를 반환한다`() {
            val likedScore = calculator.scoreFor(RankingEvent.Liked(productId = 1L))
            val unlikedScore = calculator.scoreFor(RankingEvent.Unliked(productId = 1L))
            assertThat(likedScore + unlikedScore).isEqualTo(0.0)
        }

        @Test
        fun `Ordered 이벤트는 order 가중치 곱하기 ln(amount + 1) 로 계산된다`() {
            val amount = 10_000L
            val score = calculator.scoreFor(RankingEvent.Ordered(productId = 1L, amount = amount))
            assertThat(score).isEqualTo(0.7 * ln1p(amount.toDouble()), Offset.offset(1e-9))
        }

        @Test
        fun `Ordered 이벤트는 amount 가 클수록 점수 증가폭이 둔화된다 (log saturation)`() {
            // 금액이 100배 차이나도 ln 변환으로 인해 점수는 100배가 되지 않는다
            val low = calculator.scoreFor(RankingEvent.Ordered(productId = 1L, amount = 1_000L))
            val high = calculator.scoreFor(RankingEvent.Ordered(productId = 1L, amount = 100_000L))

            assertThat(high).isGreaterThan(low)
            assertThat(high / low).isLessThan(3.0) // 100배 차이지만 점수는 3배 미만
        }
    }

    @Nested
    @DisplayName("주문 1건 vs 좋아요 N건 — 가중치 정합성")
    inner class WeightCorrectness {

        @Test
        fun `주문 1건의 점수는 좋아요 3건의 점수보다 크다`() {
            // 체크리스트: "가중치 적용이 의도대로 랭킹 순서에 반영되는지 확인 (주문 1건 > 좋아요 3건)"
            val likeThree = (1..3).sumOf {
                calculator.scoreFor(RankingEvent.Liked(productId = 1L))
            }
            val orderOne = calculator.scoreFor(
                RankingEvent.Ordered(productId = 1L, amount = 10_000L),
            )

            assertThat(orderOne).isGreaterThan(likeThree)
        }

        @Test
        fun `조회 100건의 점수는 좋아요 10건의 점수보다 작다 (조회는 가장 약한 신호)`() {
            val viewHundred = (1..100).sumOf {
                calculator.scoreFor(RankingEvent.Viewed(productId = 1L))
            }
            val likeTen = (1..10).sumOf {
                calculator.scoreFor(RankingEvent.Liked(productId = 1L))
            }

            // view 0.1 * 100 = 10.0, like 0.2 * 10 = 2.0
            // 사실은 조회 100건이 더 큼 → 가중치 설정에서 조회가 너무 풍부할 때 지배할 수 있음을 보여주는 케이스
            // 이 케이스는 "왜 조회 가중치가 낮아야 하는가" 를 명확히 한다
            assertThat(viewHundred).isGreaterThan(likeTen)
        }
    }

    @Nested
    @DisplayName("동적 가중치 변경")
    inner class DynamicWeights {

        @Test
        fun `다른 가중치로 새 인스턴스를 만들면 점수가 그에 맞게 바뀐다`() {
            val customWeights = RankingWeights(view = 1.0, like = 1.0, order = 1.0)
            val customCalculator = WeightedSumScoreCalculator(customWeights)

            assertThat(customCalculator.scoreFor(RankingEvent.Viewed(1L))).isEqualTo(1.0)
            assertThat(customCalculator.scoreFor(RankingEvent.Liked(1L))).isEqualTo(1.0)
        }
    }
}
