package com.loopers.config.redis

enum class CachePolicy {
    FALLBACK,   // 핵심 기능: Redis 장애 → null 반환 → 호출자가 DB 조회
    FAIL_FAST,  // 부가 기능: Redis 장애 → CacheException throw → DB 보호
}
