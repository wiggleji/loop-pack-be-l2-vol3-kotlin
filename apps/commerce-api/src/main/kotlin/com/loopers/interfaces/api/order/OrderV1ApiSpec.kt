package com.loopers.interfaces.api.order

import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.security.AuthHeader
import com.loopers.interfaces.api.security.LoginUser
import com.loopers.interfaces.api.security.QueueEntryInterceptor
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag
import java.time.LocalDate

@Tag(name = "Order V1 API", description = "주문 관련 API입니다.")
interface OrderV1ApiSpec {

    @Operation(
        summary = "주문 생성",
        description = "새로운 주문을 생성합니다. 대기열에서 발급받은 입장 토큰(X-Entry-Token)이 필요합니다. 토큰 검증은 인터셉터에서 수행됩니다.",
        parameters = [
            Parameter(name = AuthHeader.HEADER_LOGIN_ID, `in` = ParameterIn.HEADER, required = true),
            Parameter(name = AuthHeader.HEADER_LOGIN_PW, `in` = ParameterIn.HEADER, required = true, hidden = true),
            Parameter(name = QueueEntryInterceptor.HEADER_ENTRY_TOKEN, `in` = ParameterIn.HEADER, required = true),
        ]
    )
    fun placeOrder(@LoginUser user: User, request: OrderV1Dto.PlaceOrderRequest): ApiResponse<OrderV1Dto.OrderResponse>

    @Operation(
        summary = "주문 목록 조회",
        description = "사용자의 주문 목록을 조회합니다. startAt과 endAt은 모두 제공하거나 모두 생략해야 합니다.",
        parameters = [
            Parameter(name = AuthHeader.HEADER_LOGIN_ID, `in` = ParameterIn.HEADER, required = true),
            Parameter(name = AuthHeader.HEADER_LOGIN_PW, `in` = ParameterIn.HEADER, required = true, hidden = true),
        ]
    )
    fun getOrders(@LoginUser user: User, startAt: LocalDate?, endAt: LocalDate?): ApiResponse<List<OrderV1Dto.OrderResponse>>

    @Operation(
        summary = "주문 상세 조회",
        description = "주문 ID로 주문 상세 정보를 조회합니다.",
        parameters = [
            Parameter(name = AuthHeader.HEADER_LOGIN_ID, `in` = ParameterIn.HEADER, required = true),
            Parameter(name = AuthHeader.HEADER_LOGIN_PW, `in` = ParameterIn.HEADER, required = true, hidden = true),
        ]
    )
    fun getOrderDetail(@LoginUser user: User, orderId: Long): ApiResponse<OrderV1Dto.OrderResponse>
}
