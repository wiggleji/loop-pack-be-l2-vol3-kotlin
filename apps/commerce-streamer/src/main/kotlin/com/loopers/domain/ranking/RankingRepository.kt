package com.loopers.domain.ranking

import java.time.Duration

/**
 * 랭킹 저장소 Port (DIP).
 *
 * Domain 계층은 이 인터페이스에만 의존하고, 실제 구현(Redis, 또는 향후 다른 저장소)은 Infrastructure 에서 주입된다.
 * 두 가지 쓰기 모드를 제공해 Phase A (단건) → Phase B (배치 델타) 로 점진적 최적화가 가능하다.
 */
interface RankingRepository {

    /**
     * 단일 productId 에 점수 델타를 누적한다 (Phase A).
     *
     * Redis 의 ZINCRBY 에 매핑된다. 호출 1회 = Redis 호출 1회이므로 트래픽이 많을 경우 [batchIncrement] 를 사용해야 한다.
     */
    fun incrementScore(key: String, productId: Long, delta: Double)

    /**
     * 여러 productId 의 델타를 한 번에 반영한다 (Phase B 를 위한 인터페이스).
     *
     * 구현체는 파이프라이닝 또는 Lua 등을 활용해 라운드트립을 최소화해야 한다.
     * 빈 맵이 들어오면 no-op.
     */
    fun batchIncrement(key: String, deltas: Map<Long, Double>)

    /**
     * 키 TTL 을 설정한다. 멱등(같은 TTL 을 여러 번 호출해도 안전).
     */
    fun expire(key: String, ttl: Duration)
}
