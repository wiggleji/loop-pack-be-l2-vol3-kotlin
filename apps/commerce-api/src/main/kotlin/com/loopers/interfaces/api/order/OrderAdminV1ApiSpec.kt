package com.loopers.interfaces.api.order

import com.loopers.domain.order.OrderStatus
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.security.AdminHeader
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag
import java.time.LocalDate

@Tag(name = "Order Admin V1 API", description = "어드민 주문 관련 API입니다.")
interface OrderAdminV1ApiSpec {

    @Operation(summary = "주문 목록 조회", description = "전체/상태별/기간별 주문 목록을 페이지네이션으로 조회합니다.",
        parameters = [Parameter(name = AdminHeader.HEADER_LDAP, `in` = ParameterIn.HEADER, required = true)])
    fun getOrders(
        status: OrderStatus?,
        startAt: LocalDate?,
        endAt: LocalDate?,
        page: Int,
        size: Int,
    ): ApiResponse<List<OrderV1Dto.OrderResponse>>

    @Operation(summary = "주문 상세 조회", description = "주문 ID로 주문 상세 정보를 조회합니다.",
        parameters = [Parameter(name = AdminHeader.HEADER_LDAP, `in` = ParameterIn.HEADER, required = true)])
    fun getOrderDetail(orderId: Long): ApiResponse<OrderV1Dto.OrderResponse>

    @Operation(summary = "지연 주문 조회", description = "특정 상태에서 일정 기간 이상 경과한 주문을 조회합니다.",
        parameters = [Parameter(name = AdminHeader.HEADER_LDAP, `in` = ParameterIn.HEADER, required = true)])
    fun getDelayedOrders(
        status: OrderStatus,
        days: Int,
        page: Int,
        size: Int,
    ): ApiResponse<List<OrderV1Dto.OrderResponse>>

    @Operation(summary = "주문 상태 변경", description = "주문의 상태를 변경합니다. (PAY, PREPARE, SHIP, DELIVER, CANCEL, REFUND)",
        parameters = [Parameter(name = AdminHeader.HEADER_LDAP, `in` = ParameterIn.HEADER, required = true)])
    fun updateOrderStatus(
        orderId: Long,
        request: OrderV1Dto.UpdateStatusRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse>
}
