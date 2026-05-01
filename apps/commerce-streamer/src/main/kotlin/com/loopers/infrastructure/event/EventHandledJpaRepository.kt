package com.loopers.infrastructure.event

import org.springframework.data.jpa.repository.JpaRepository

interface EventHandledJpaRepository : JpaRepository<EventHandledEntity, String>
