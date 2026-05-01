package com.loopers.infrastructure.catalog.brand

import com.loopers.domain.catalog.brand.BrandStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface BrandJpaRepository : JpaRepository<BrandEntity, Long> {

    @Query("SELECT b FROM BrandEntity b WHERE b.deletedAt IS NULL AND b.status = :status")
    fun findAllByStatus(status: BrandStatus, pageable: Pageable): List<BrandEntity>

    @Query("SELECT b FROM BrandEntity b WHERE b.id IN :ids AND b.deletedAt IS NULL")
    fun findAllByIdIn(ids: List<Long>): List<BrandEntity>
}
