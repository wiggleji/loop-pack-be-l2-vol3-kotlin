package com.loopers.infrastructure.payment

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "pg.client")
data class PgClientConfig(
    val baseUrl: String = "http://localhost:8082",
    val connectTimeout: Long = 1000,
    val readTimeout: Long = 2000,
)
