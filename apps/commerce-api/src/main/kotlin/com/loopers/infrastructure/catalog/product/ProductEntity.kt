package com.loopers.infrastructure.catalog.product

import com.loopers.domain.BaseEntity
import com.loopers.domain.catalog.product.Product
import com.loopers.domain.catalog.product.ProductStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "products",
    indexes = [
        // Single-column indexes — for no-brand-filter queries (ORDER BY + LIMIT) & cascade delete
        Index(name = "idx_products_brand_id", columnList = "brand_id"),
        Index(name = "idx_products_created_at", columnList = "created_at"),
        Index(name = "idx_products_price", columnList = "price"),
        Index(name = "idx_products_like_count", columnList = "like_count"),
        // Multi-column composite indexes — for brand-filter queries (equality → sort)
        Index(name = "idx_products_status_brand_created", columnList = "status, brand_id, created_at DESC"),
        Index(name = "idx_products_status_brand_price", columnList = "status, brand_id, price ASC"),
        Index(name = "idx_products_status_brand_likecount", columnList = "status, brand_id, like_count DESC"),
    ]
)
class ProductEntity(
    brandId: Long,
    name: String,
    description: String,
    price: Int,
    likeCount: Int = 0,
    status: ProductStatus = ProductStatus.ACTIVE,
) : BaseEntity() {

    @Column(name = "brand_id", nullable = false)
    var brandId: Long = brandId
        protected set

    @Column(nullable = false)
    var name: String = name
        protected set

    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String = description
        protected set

    @Column(nullable = false)
    var price: Int = price
        protected set

    @Column(name = "like_count", nullable = false)
    var likeCount: Int = likeCount
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ProductStatus = status
        protected set

    fun update(name: String, description: String, price: Int, status: ProductStatus) {
        this.name = name
        this.description = description
        this.price = price
        this.status = status
    }

    fun updateLikeCount(likeCount: Int) {
        this.likeCount = likeCount
    }

    fun updateStatus(status: ProductStatus) {
        this.status = status
    }

    fun toDomain(): Product = Product(
        id = this.id,
        brandId = this.brandId,
        name = this.name,
        description = this.description,
        price = this.price,
        likeCount = this.likeCount,
        status = this.status,
    )

    companion object {
        fun from(product: Product): ProductEntity = ProductEntity(
            brandId = product.brandId,
            name = product.name,
            description = product.description,
            price = product.price,
            likeCount = product.likeCount,
            status = product.status,
        )
    }
}
