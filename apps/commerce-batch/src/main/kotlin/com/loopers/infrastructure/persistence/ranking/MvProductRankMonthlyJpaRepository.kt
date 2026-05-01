package com.loopers.infrastructure.persistence.ranking

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MvProductRankMonthlyJpaRepository : JpaRepository<MvProductRankMonthlyEntity, Long> {

    @Modifying
    @Query("DELETE FROM MvProductRankMonthlyEntity e WHERE e.periodKey = :periodKey")
    fun deleteAllByPeriodKey(@Param("periodKey") periodKey: String): Int

    fun countByPeriodKey(periodKey: String): Long
}
