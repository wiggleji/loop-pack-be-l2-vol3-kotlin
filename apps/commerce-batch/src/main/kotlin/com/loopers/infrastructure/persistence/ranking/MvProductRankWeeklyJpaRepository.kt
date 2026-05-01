package com.loopers.infrastructure.persistence.ranking

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MvProductRankWeeklyJpaRepository : JpaRepository<MvProductRankWeeklyEntity, Long> {

    /** 특정 주차의 적재 데이터를 모두 삭제 (idempotent 보장 — 재실행 시 사전 cleanup) */
    @Modifying
    @Query("DELETE FROM MvProductRankWeeklyEntity e WHERE e.periodKey = :periodKey")
    fun deleteAllByPeriodKey(@Param("periodKey") periodKey: String): Int

    fun countByPeriodKey(periodKey: String): Long
}
