package com.loopers.infrastructure.ranking

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface MvProductRankWeeklyJpaRepository : JpaRepository<MvProductRankWeeklyEntity, Long> {

    /** rank ASC 정렬로 페이지 단위 조회 (Pageable 의 sort 는 사용하지 않고 메서드명으로 고정) */
    fun findByPeriodKeyOrderByRankAsc(periodKey: String, pageable: Pageable): List<MvProductRankWeeklyEntity>

    fun countByPeriodKey(periodKey: String): Long

    fun findByPeriodKeyAndProductId(periodKey: String, productId: Long): MvProductRankWeeklyEntity?
}
