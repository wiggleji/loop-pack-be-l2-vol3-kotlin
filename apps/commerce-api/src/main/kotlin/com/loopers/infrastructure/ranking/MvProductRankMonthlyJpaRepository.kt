package com.loopers.infrastructure.ranking

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface MvProductRankMonthlyJpaRepository : JpaRepository<MvProductRankMonthlyEntity, Long> {

    fun findByPeriodKeyOrderByRankAsc(periodKey: String, pageable: Pageable): List<MvProductRankMonthlyEntity>

    fun countByPeriodKey(periodKey: String): Long

    fun findByPeriodKeyAndProductId(periodKey: String, productId: Long): MvProductRankMonthlyEntity?
}
