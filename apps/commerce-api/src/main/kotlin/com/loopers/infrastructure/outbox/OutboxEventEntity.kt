package com.loopers.infrastructure.outbox

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.ZonedDateTime

enum class OutboxStatus {
    PENDING,
    PUBLISHED,
}

@Entity
@Table(
    name = "outbox_events",
    indexes = [
        Index(name = "idx_outbox_status", columnList = "status"),
    ],
)
class OutboxEventEntity(
    @Column(nullable = false, length = 50)
    val aggregateType: String,

    @Column(nullable = false, length = 50)
    val aggregateId: String,

    @Column(nullable = false, length = 50)
    val eventType: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val payload: String,

    @Column(nullable = false, length = 100)
    val topic: String,

    @Column(nullable = false, length = 100)
    val partitionKey: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OutboxStatus = OutboxStatus.PENDING,

    var publishedAt: ZonedDateTime? = null,
) : BaseEntity() {

    fun markPublished() {
        this.status = OutboxStatus.PUBLISHED
        this.publishedAt = ZonedDateTime.now()
    }
}
