package com.loopers.domain.ranking

/**
 * 랭킹 점수 계산 전략 인터페이스 (Strategy Pattern).
 *
 * 가중치/식이 비즈니스 요구에 따라 자주 바뀔 수 있으므로 구현체를 분리해 두었다.
 * Consumer/Updater 는 이 인터페이스에만 의존하고, 새로운 계산 방식이 필요하면 구현체만 갈아끼운다.
 */
fun interface ScoreCalculator {
    /**
     * 이벤트 1건이 ZSET 에 누적할 델타 점수를 계산한다.
     *
     * - 양수: 점수 가산 (조회/좋아요/주문)
     * - 음수: 점수 차감 (좋아요 취소)
     */
    fun scoreFor(event: RankingEvent): Double
}
