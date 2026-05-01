package com.loopers.infrastructure.outbox

object KafkaTopics {
    const val ORDER_EVENTS = "order-events"
    const val CATALOG_EVENTS = "catalog-events"
    const val COUPON_ISSUE_REQUESTS = "coupon-issue-requests"
}
