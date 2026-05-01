package com.loopers.domain.order

import java.time.LocalDate
import java.time.ZonedDateTime

interface OrderRepository {
    fun save(order: Order): Order
    fun findById(id: Long): Order?
    fun findByUserId(userId: Long): List<Order>
    fun findByUserIdAndDateRange(userId: Long, startAt: LocalDate, endAt: LocalDate): List<Order>
    fun findAll(page: Int, size: Int): List<Order>
    fun findByStatus(status: OrderStatus, page: Int, size: Int): List<Order>
    fun findByStatusAndDateRange(status: OrderStatus, startAt: LocalDate, endAt: LocalDate, page: Int, size: Int): List<Order>
    fun findDelayedOrders(status: OrderStatus, olderThan: ZonedDateTime, page: Int, size: Int): List<Order>
    fun updateStatus(order: Order)
}
