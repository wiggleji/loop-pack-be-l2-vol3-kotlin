package com.loopers.domain.ranking

/**
 * 랭킹 점수 계산의 입력. 어떤 종류의 이벤트가, 어떤 상품에 대해, 얼마만큼 발생했는지를 표현한다.
 *
 * Consumer 가 Kafka payload 를 파싱한 뒤 이 도메인 객체로 변환해 ScoreCalculator 에 전달한다.
 * (스코어 계산 로직은 Kafka payload 스키마와 분리되어야 추후 이벤트 스키마가 바뀌어도 영향이 없다)
 */
sealed class RankingEvent {
    abstract val productId: Long

    data class Viewed(override val productId: Long) : RankingEvent()

    data class Liked(override val productId: Long) : RankingEvent()

    data class Unliked(override val productId: Long) : RankingEvent()

    /**
     * @param amount 결제 금액 (price * quantity)
     */
    data class Ordered(
        override val productId: Long,
        val amount: Long,
    ) : RankingEvent()
}
