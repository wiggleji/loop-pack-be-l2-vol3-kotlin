package com.loopers.infrastructure.like

import com.loopers.domain.BaseEntity
import com.loopers.domain.like.Like
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "likes",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "product_id"])],
    indexes = [
        Index(name = "idx_likes_product_id", columnList = "product_id"),
    ]
)
class LikeEntity(
    userId: Long,
    productId: Long,
) : BaseEntity() {

    @Column(name = "user_id", nullable = false)
    val userId: Long = userId

    @Column(name = "product_id", nullable = false)
    val productId: Long = productId

    fun toDomain(): Like = Like(
        id = this.id,
        userId = this.userId,
        productId = this.productId,
    )

    companion object {
        fun from(like: Like): LikeEntity = LikeEntity(
            userId = like.userId,
            productId = like.productId,
        )
    }
}
