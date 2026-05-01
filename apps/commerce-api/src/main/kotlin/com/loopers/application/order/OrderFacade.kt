package com.loopers.application.order

import com.loopers.application.order.event.OrderItemSnapshot
import com.loopers.application.order.event.OrderPlacedEvent
import com.loopers.domain.catalog.brand.BrandRepository
import com.loopers.domain.catalog.product.ProductRepository
import com.loopers.domain.catalog.product.ProductService
import com.loopers.domain.catalog.product.ProductStockRepository
import com.loopers.domain.catalog.product.ProductStockService
import com.loopers.domain.coupon.CouponTemplateService
import com.loopers.domain.coupon.UserCouponService
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderService
import com.loopers.infrastructure.outbox.KafkaTopics
import com.loopers.infrastructure.outbox.OutboxEventService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderFacade(
    private val orderService: OrderService,
    private val productService: ProductService,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productStockService: ProductStockService,
    private val productStockRepository: ProductStockRepository,
    private val userCouponService: UserCouponService,
    private val couponTemplateService: CouponTemplateService,
    private val outboxEventService: OutboxEventService,
) {

    @Transactional
    fun placeOrder(userId: Long, cmd: PlaceOrderCommand): OrderResult {
        if (cmd.items.isEmpty()) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 항목이 비어있을 수 없습니다.")
        }

        // 1. 모든 상품 및 재고 일괄 조회 (N+1 방지)
        val sortedItems = cmd.items.sortedBy { it.productId }
        val productIds = sortedItems.map { it.productId }
        val productMap = productRepository.findAllByIds(productIds).associateBy { it.id }
        val stockMap = productStockRepository.findAllByProductIds(productIds).associateBy { it.productId }

        val itemWithProductsAndStocks = sortedItems.map { item ->
            val product = productMap[item.productId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "[${item.productId}] 상품이 존재하지 않습니다.")
            val stock = stockMap[item.productId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "[${item.productId}] 상품의 재고 정보가 존재하지 않습니다.")
            Triple(item, product, stock)
        }

        // 2. 모든 상품 주문 가능 여부 및 재고 검증 (fail-fast: 부분 처리 없이 전체 실패)
        itemWithProductsAndStocks.forEach { (item, product, stock) ->
            product.requireOrderable()
            stock.validate(item.quantity)
        }

        // 3. 모든 재고 차감 (비관적 락 — 개별 호출 필수)
        itemWithProductsAndStocks.forEach { (item, _, _) ->
            val updatedStock = productStockService.decrementStock(item.productId, item.quantity)
            if (updatedStock.isSoldOut) {
                productService.updateStockStatus(item.productId, 0)
            }
        }

        // 4. 브랜드 일괄 조회 및 주문 항목 스냅샷 생성 (N+1 방지)
        val brandIds = productMap.values.map { it.brandId }.distinct()
        val brandMap = brandRepository.findAllByIds(brandIds).associateBy { it.id }

        val orderItems = itemWithProductsAndStocks.map { (item, product, _) ->
            val brand = brandMap[product.brandId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "[${product.brandId}] 브랜드가 존재하지 않습니다.")
            OrderItem(
                orderId = 0L,
                productId = product.id,
                productName = product.name,
                brandId = brand.id,
                brandName = brand.name,
                price = product.price,
                quantity = item.quantity,
            )
        }

        // 5. 쿠폰 할인 계산
        val originalTotalPrice = orderItems.sumOf { it.subtotal() }
        var discountAmount = 0

        if (cmd.userCouponId != null) {
            val userCoupon = userCouponService.getById(cmd.userCouponId)
            if (userCoupon.userId != userId) {
                throw CoreException(ErrorType.BAD_REQUEST, "다른 사용자의 쿠폰은 사용할 수 없습니다.")
            }
            userCoupon.requireAvailable()

            val template = couponTemplateService.getById(userCoupon.couponTemplateId)
            if (template.isExpired()) {
                throw CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.")
            }
            if (originalTotalPrice < template.minOrderAmount) {
                throw CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액(${template.minOrderAmount}원)을 충족하지 못했습니다.")
            }

            discountAmount = template.discount(originalTotalPrice)
        }

        // 6. 주문 생성 및 저장
        val order = orderService.createOrder(
            userId = userId,
            items = orderItems,
            discountAmount = discountAmount,
            userCouponId = cmd.userCouponId,
        )

        // 7. 쿠폰 사용 처리 (주문 ID 확정 후)
        if (cmd.userCouponId != null) {
            userCouponService.useForOrder(cmd.userCouponId, order.id)
        }

        // 8. Outbox 저장 (같은 TX — At Least Once 보장)
        outboxEventService.save(
            aggregateType = "ORDER",
            aggregateId = order.id.toString(),
            eventType = "ORDER_PLACED",
            payload = OrderPlacedEvent(
                orderId = order.id,
                userId = userId,
                items = orderItems.map { OrderItemSnapshot(it.productId, it.quantity, it.price) },
                originalTotalPrice = originalTotalPrice,
                discountAmount = discountAmount,
                totalPrice = order.totalPrice,
                userCouponId = cmd.userCouponId,
                cardType = cmd.cardType,
                cardNo = cmd.cardNo,
            ),
            topic = KafkaTopics.ORDER_EVENTS,
            partitionKey = order.id.toString(),
        )

        return OrderResult.from(order)
    }
}
