package com.loopers.infrastructure.catalog.product

import com.loopers.domain.catalog.product.ProductStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductJpaRepository : JpaRepository<ProductEntity, Long> {

    @Query("SELECT p FROM ProductEntity p WHERE p.deletedAt IS NULL AND p.status = :status AND (:brandId IS NULL OR p.brandId = :brandId) ORDER BY p.createdAt DESC")
    fun findAllByStatusOrderByCreatedAtDesc(brandId: Long?, status: ProductStatus, pageable: Pageable): List<ProductEntity>

    @Query("SELECT p FROM ProductEntity p WHERE p.deletedAt IS NULL AND p.status = :status AND (:brandId IS NULL OR p.brandId = :brandId) ORDER BY p.price ASC")
    fun findAllByStatusOrderByPriceAsc(brandId: Long?, status: ProductStatus, pageable: Pageable): List<ProductEntity>

    @Query("SELECT p FROM ProductEntity p WHERE p.deletedAt IS NULL AND p.status = :status AND (:brandId IS NULL OR p.brandId = :brandId) ORDER BY p.likeCount DESC")
    fun findAllByStatusOrderByLikeCountDesc(brandId: Long?, status: ProductStatus, pageable: Pageable): List<ProductEntity>

    @Query("SELECT p FROM ProductEntity p WHERE p.id IN :ids AND p.deletedAt IS NULL")
    fun findAllByIdIn(ids: List<Long>): List<ProductEntity>

    @Query("SELECT p FROM ProductEntity p WHERE p.deletedAt IS NULL AND p.brandId = :brandId")
    fun findAllByBrandId(brandId: Long): List<ProductEntity>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = "UPDATE products SET like_count = like_count + 1, updated_at = NOW() WHERE id = :id AND deleted_at IS NULL",
        nativeQuery = true,
    )
    fun incrementLikeCountAtomic(@Param("id") id: Long): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = "UPDATE products SET like_count = GREATEST(like_count - 1, 0), updated_at = NOW() WHERE id = :id AND deleted_at IS NULL",
        nativeQuery = true,
    )
    fun decrementLikeCountAtomic(@Param("id") id: Long): Int
}
