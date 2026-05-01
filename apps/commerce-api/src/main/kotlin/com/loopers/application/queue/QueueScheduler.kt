package com.loopers.application.queue

import com.loopers.domain.queue.QueueService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 대기열 스케줄러 — 100ms 주기로 대기열에서 N명을 꺼내 입장 토큰을 발급한다.
 *
 * 처리량 설계 기준:
 *   DB 커넥션 풀: 50
 *   주문 1건 평균 처리 시간: 200ms
 *   이론적 최대 TPS: 50 / 0.2 = 250 TPS
 *   안전 마진 70%: 175 TPS
 *   배치 크기: 175 / 10 = ~18명 per 100ms (Thundering Herd 완화)
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = ["queue.scheduler.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class QueueScheduler(
    private val queueService: QueueService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val BATCH_SIZE = 18L
    }

    @Scheduled(fixedRate = 100)
    fun processQueue() {
        val userIds = queueService.popNextBatch(BATCH_SIZE)
        if (userIds.isEmpty()) return

        for (userId in userIds) {
            try {
                queueService.issueToken(userId)
            } catch (e: Exception) {
                log.error("Token issuance failed - userId={}, error={}", userId, e.message, e)
            }
        }

        log.debug("Queue batch processed - count={}", userIds.size)
    }
}
