package com.loopers.infrastructure.event

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "event_handled")
class EventHandledEntity(
    @Id
    @Column(length = 100)
    val eventId: String,

    @Column(nullable = false)
    val handledAt: ZonedDateTime = ZonedDateTime.now(),
)
