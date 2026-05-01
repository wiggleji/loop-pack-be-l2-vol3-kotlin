package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderFacade
import com.loopers.application.order.OrderItemCommand
import com.loopers.application.order.PlaceOrderCommand
import com.loopers.application.queue.QueueFacade
import com.loopers.domain.order.OrderService
import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.security.LoginUser
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/orders")
class OrderV1Controller(
    private val orderFacade: OrderFacade,
    private val orderService: OrderService,
    private val queueFacade: QueueFacade,
) : OrderV1ApiSpec {

    @PostMapping
    override fun placeOrder(
        @LoginUser user: User,
        @RequestBody request: OrderV1Dto.PlaceOrderRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        // 토큰 검증은 QueueEntryInterceptor에서 완료됨 (Active Zone 관문)

        val cmd = PlaceOrderCommand(
            items = request.items.map { OrderItemCommand(productId = it.productId, quantity = it.quantity) },
            userCouponId = request.userCouponId,
            cardType = request.cardType,
            cardNo = request.cardNo,
        )

        // 주문 생성 (@Transactional) — 결제는 AFTER_COMMIT 이벤트로 비동기 처리
        val orderResult = orderFacade.placeOrder(user.id, cmd)

        // 주문 성공 후 입장 토큰 소비 (active slot 해제)
        // NOTE: 토큰 소비는 이벤트 기반으로 처리하면 어떨까?
        //   비동기로 처리시 주문 처리 성능은 빨라질 수 있지만, 이벤트가 소비되기 전에
        //   사용자가 토큰을 재사용할 수 있음 + 토큰 소비 실패하면 해당 요청이 실패되야할 수도 있기에
        //   동기로 처리하도록 유지하는게 올바른 방향이라 생각
        queueFacade.consumeToken(user.id)

        return OrderV1Dto.OrderResponse.from(orderResult)
            .let { ApiResponse.success(it) }
    }

    @GetMapping
    override fun getOrders(
        @LoginUser user: User,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startAt: LocalDate?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endAt: LocalDate?,
    ): ApiResponse<List<OrderV1Dto.OrderResponse>> =
        orderService.getOrders(user.id, startAt, endAt)
            .map { OrderV1Dto.OrderResponse.from(it) }
            .let { ApiResponse.success(it) }

    @GetMapping("/{orderId}")
    override fun getOrderDetail(
        @LoginUser user: User,
        @PathVariable orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderResponse> =
        orderService.getById(orderId)
            .let { OrderV1Dto.OrderResponse.from(it) }
            .let { ApiResponse.success(it) }
}
