package com.loopers.infrastructure.metrics

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version

@Entity
@Table(
    name = "product_metrics",
    indexes = [
        Index(name = "idx_product_metrics_product_id", columnList = "product_id", unique = true),
    ],
)
class ProductMetricsEntity(
    @Column(name = "product_id", nullable = false, unique = true)
    val productId: Long,

    @Column(nullable = false)
    var viewCount: Long = 0,

    @Column(nullable = false)
    var likeCount: Long = 0,

    @Column(nullable = false)
    var salesCount: Long = 0,

    @Column(nullable = false)
    var salesAmount: Long = 0,

    @Version
    val version: Long = 0,
) : BaseEntity() {

    fun incrementView() {
        viewCount++
    }

    fun incrementLike() {
        likeCount++
    }

    fun decrementLike() {
        if (likeCount > 0) likeCount--
    }

    fun addSales(quantity: Int, amount: Int) {
        salesCount += quantity
        salesAmount += amount
    }
}
