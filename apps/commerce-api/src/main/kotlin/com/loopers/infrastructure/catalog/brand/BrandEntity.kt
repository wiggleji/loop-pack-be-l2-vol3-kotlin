package com.loopers.infrastructure.catalog.brand

import com.loopers.domain.BaseEntity
import com.loopers.domain.catalog.brand.Brand
import com.loopers.domain.catalog.brand.BrandStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "brands")
class BrandEntity(
    name: String,
    description: String,
    status: BrandStatus = BrandStatus.ACTIVE,
) : BaseEntity() {

    @Column(nullable = false)
    var name: String = name
        protected set

    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String = description
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BrandStatus = status
        protected set

    fun update(name: String, description: String) {
        this.name = name
        this.description = description
    }

    fun updateStatus(status: BrandStatus) {
        this.status = status
    }

    fun toDomain(): Brand = Brand(
        id = this.id,
        name = this.name,
        description = this.description,
        status = this.status,
    )

    companion object {
        fun from(brand: Brand): BrandEntity = BrandEntity(
            name = brand.name,
            description = brand.description,
            status = brand.status,
        )
    }
}
