package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

@Repository
class OrderRepositoryImpl(
    private val orderJpaRepository: OrderJpaRepository,
    private val orderItemJpaRepository: OrderItemJpaRepository,
) : OrderRepository {

    override fun save(order: Order): Order {
        val orderEntity = orderJpaRepository.save(OrderEntity.from(order))
        val itemEntities = order.items.map { item ->
            OrderItemEntity.from(item, orderId = orderEntity.id)
        }
        val savedItems = orderItemJpaRepository.saveAll(itemEntities)
        return orderEntity.toDomain(savedItems)
    }

    override fun findById(id: Long): Order? {
        val orderEntity = orderJpaRepository.findById(id)
            .filter { it.deletedAt == null }
            .orElse(null) ?: return null
        val items = orderItemJpaRepository.findByOrderId(orderEntity.id)
        return orderEntity.toDomain(items)
    }

    override fun findByUserId(userId: Long): List<Order> {
        val orderEntities = orderJpaRepository.findByUserId(userId)
            .filter { it.deletedAt == null }
        return toOrders(orderEntities)
    }

    override fun findByUserIdAndDateRange(userId: Long, startAt: LocalDate, endAt: LocalDate): List<Order> {
        val startZdt = startAt.atStartOfDay(ZoneOffset.UTC)
        val endZdt = endAt.plusDays(1).atStartOfDay(ZoneOffset.UTC)
        val orderEntities = orderJpaRepository.findByUserIdAndCreatedAtBetween(userId, startZdt, endZdt)
            .filter { it.deletedAt == null }
        return toOrders(orderEntities)
    }

    override fun findAll(page: Int, size: Int): List<Order> {
        val orderEntities = orderJpaRepository.findAllActive(PageRequest.of(page, size))
        return toOrders(orderEntities)
    }

    override fun findByStatus(status: OrderStatus, page: Int, size: Int): List<Order> {
        val orderEntities = orderJpaRepository.findByStatusAndDeletedAtIsNull(status.name, PageRequest.of(page, size))
        return toOrders(orderEntities)
    }

    override fun findByStatusAndDateRange(
        status: OrderStatus,
        startAt: LocalDate,
        endAt: LocalDate,
        page: Int,
        size: Int,
    ): List<Order> {
        val startZdt = startAt.atStartOfDay(ZoneOffset.UTC)
        val endZdt = endAt.plusDays(1).atStartOfDay(ZoneOffset.UTC)
        val orderEntities = orderJpaRepository.findByStatusAndCreatedAtBetween(
            status.name, startZdt, endZdt, PageRequest.of(page, size)
        )
        return toOrders(orderEntities)
    }

    override fun findDelayedOrders(
        status: OrderStatus,
        olderThan: ZonedDateTime,
        page: Int,
        size: Int,
    ): List<Order> {
        val orderEntities = orderJpaRepository.findByStatusAndCreatedAtBefore(
            status.name, olderThan, PageRequest.of(page, size)
        )
        return toOrders(orderEntities)
    }

    override fun updateStatus(order: Order) {
        val entity = orderJpaRepository.findById(order.id)
            .orElseThrow { IllegalStateException("Order not found: ${order.id}") }
        entity.updateStatus(order)
    }

    private fun toOrders(orderEntities: List<OrderEntity>): List<Order> {
        if (orderEntities.isEmpty()) return emptyList()
        val orderIds = orderEntities.map { it.id }
        val itemsByOrderId = orderItemJpaRepository.findByOrderIdIn(orderIds)
            .groupBy { it.orderId }
        return orderEntities.map { orderEntity ->
            orderEntity.toDomain(itemsByOrderId[orderEntity.id] ?: emptyList())
        }
    }
}
