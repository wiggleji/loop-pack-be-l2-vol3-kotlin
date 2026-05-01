package com.loopers.interfaces.api.order

import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

@RestController
@RequestMapping("/api-admin/v1/orders")
class OrderAdminV1Controller(
    private val orderService: OrderService,
) : OrderAdminV1ApiSpec {

    @GetMapping
    override fun getOrders(
        @RequestParam(required = false) status: OrderStatus?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startAt: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endAt: LocalDate?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<List<OrderV1Dto.OrderResponse>> {
        val orders = when {
            status != null && startAt != null && endAt != null ->
                orderService.getByStatusAndDateRange(status, startAt, endAt, page, size)
            status != null ->
                orderService.getByStatus(status, page, size)
            else ->
                orderService.findAll(page, size)
        }
        return orders.map { OrderV1Dto.OrderResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{orderId}")
    override fun getOrderDetail(@PathVariable orderId: Long): ApiResponse<OrderV1Dto.OrderResponse> =
        orderService.getById(orderId)
            .let { OrderV1Dto.OrderResponse.from(it) }
            .let { ApiResponse.success(it) }

    @GetMapping("/delayed")
    override fun getDelayedOrders(
        @RequestParam status: OrderStatus,
        @RequestParam(defaultValue = "2") days: Int,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<List<OrderV1Dto.OrderResponse>> {
        val olderThan = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days.toLong())
        return orderService.getDelayedOrders(status, olderThan, page, size)
            .map { OrderV1Dto.OrderResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PatchMapping("/{orderId}/status")
    override fun updateOrderStatus(
        @PathVariable orderId: Long,
        @RequestBody request: OrderV1Dto.UpdateStatusRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val action: (com.loopers.domain.order.Order) -> Unit = when (request.action.uppercase()) {
            "PAY" -> { order -> order.pay(now) }
            "PREPARE" -> { order -> order.startPreparing() }
            "SHIP" -> { order -> order.ship(now) }
            "DELIVER" -> { order -> order.deliver(now) }
            "CANCEL" -> { order -> order.cancel(now) }
            "REFUND" -> { order -> order.refund(now) }
            else -> throw CoreException(ErrorType.BAD_REQUEST, "[${request.action}] 지원하지 않는 상태 변경입니다.")
        }
        return orderService.updateStatus(orderId, action)
            .let { OrderV1Dto.OrderResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
