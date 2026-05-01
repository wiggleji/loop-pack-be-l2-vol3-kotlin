package com.loopers.application.order

import com.loopers.domain.catalog.brand.Brand
import com.loopers.domain.catalog.brand.BrandRepository
import com.loopers.domain.catalog.product.Product
import com.loopers.domain.catalog.product.ProductRepository
import com.loopers.domain.catalog.product.ProductService
import com.loopers.domain.catalog.product.ProductStatus
import com.loopers.domain.catalog.product.ProductStock
import com.loopers.domain.catalog.product.ProductStockRepository
import com.loopers.domain.catalog.product.ProductStockService
import com.loopers.domain.coupon.CouponTemplateService
import com.loopers.domain.coupon.UserCouponService
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.CardType
import com.loopers.infrastructure.outbox.OutboxEventService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OrderFacadeUnitTest {

    private val mockOrderService = mockk<OrderService>()
    private val mockProductService = mockk<ProductService>()
    private val mockProductRepository = mockk<ProductRepository>()
    private val mockBrandRepository = mockk<BrandRepository>()
    private val mockProductStockService = mockk<ProductStockService>()
    private val mockProductStockRepository = mockk<ProductStockRepository>()
    private val mockUserCouponService = mockk<UserCouponService>()
    private val mockCouponTemplateService = mockk<CouponTemplateService>()
    private val mockOutboxEventService = mockk<OutboxEventService>(relaxed = true)

    private val orderFacade = OrderFacade(
        mockOrderService, mockProductService, mockProductRepository, mockBrandRepository,
        mockProductStockService, mockProductStockRepository, mockUserCouponService, mockCouponTemplateService,
        mockOutboxEventService,
    )

    // ─── placeOrder ───

    @Test
    fun `placeOrder() should batch fetch products, stocks, brands and create order`() {
        // Arrange
        val product = createProduct(id = 1L, brandId = 1L)
        val stock = ProductStock(productId = 1L, quantity = 10, id = 1L)
        val decrementedStock = ProductStock(productId = 1L, quantity = 8, id = 1L)
        val brand = createBrand(id = 1L, name = "Nike")
        val orderItem = createOrderItem(productId = 1L, brandId = 1L)
        val order = createOrder(userId = 1L, items = listOf(orderItem))

        every { mockProductRepository.findAllByIds(listOf(1L)) } returns listOf(product)
        every { mockProductStockRepository.findAllByProductIds(listOf(1L)) } returns listOf(stock)
        every { mockProductStockService.decrementStock(1L, 2) } returns decrementedStock
        every { mockBrandRepository.findAllByIds(listOf(1L)) } returns listOf(brand)
        every { mockOrderService.createOrder(any(), any(), any(), any()) } returns order

        val cmd = PlaceOrderCommand(items = listOf(OrderItemCommand(productId = 1L, quantity = 2)), cardType = CardType.SAMSUNG, cardNo = "1234-5678-9012-3456")

        // Act
        val result = orderFacade.placeOrder(userId = 1L, cmd = cmd)

        // Assert
        assertThat(result).isNotNull
        verify { mockProductRepository.findAllByIds(listOf(1L)) }
        verify { mockProductStockRepository.findAllByProductIds(listOf(1L)) }
        verify { mockProductStockService.decrementStock(1L, 2) }
        verify { mockOrderService.createOrder(1L, any(), any(), any()) }
    }

    @Test
    fun `placeOrder() throws BAD_REQUEST when one product is out of stock`() {
        // Arrange
        val product1 = createProduct(id = 1L)
        val product2 = createProduct(id = 2L)
        val stock1 = ProductStock(productId = 1L, quantity = 10, id = 1L)
        val stock2 = ProductStock(productId = 2L, quantity = 1, id = 2L) // only 1 in stock

        every { mockProductRepository.findAllByIds(listOf(1L, 2L)) } returns listOf(product1, product2)
        every { mockProductStockRepository.findAllByProductIds(listOf(1L, 2L)) } returns listOf(stock1, stock2)

        val cmd = PlaceOrderCommand(
            items = listOf(
                OrderItemCommand(productId = 1L, quantity = 2),
                OrderItemCommand(productId = 2L, quantity = 5), // exceeds stock
            ),
            cardType = CardType.SAMSUNG,
            cardNo = "1234-5678-9012-3456",
        )

        // Act & Assert
        assertThrows<CoreException> {
            orderFacade.placeOrder(userId = 1L, cmd = cmd)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        // stock should NOT be decremented for any product
        verify(exactly = 0) { mockProductStockService.decrementStock(any(), any()) }
        verify(exactly = 0) { mockOrderService.createOrder(any(), any(), any(), any()) }
    }

    @Test
    fun `placeOrder() throws NOT_FOUND when product does not exist`() {
        // Arrange
        every { mockProductRepository.findAllByIds(listOf(99L)) } returns emptyList()
        every { mockProductStockRepository.findAllByProductIds(listOf(99L)) } returns emptyList()

        val cmd = PlaceOrderCommand(items = listOf(OrderItemCommand(productId = 99L, quantity = 1)), cardType = CardType.SAMSUNG, cardNo = "1234-5678-9012-3456")

        // Act & Assert
        assertThrows<CoreException> {
            orderFacade.placeOrder(userId = 1L, cmd = cmd)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        verify(exactly = 0) { mockProductStockService.decrementStock(any(), any()) }
        verify(exactly = 0) { mockOrderService.createOrder(any(), any(), any(), any()) }
    }

    @Test
    fun `placeOrder() throws BAD_REQUEST when product is not orderable`() {
        // Arrange
        val hiddenProduct = createProduct(id = 1L, status = ProductStatus.HIDDEN)
        val stock = ProductStock(productId = 1L, quantity = 10, id = 1L)
        every { mockProductRepository.findAllByIds(listOf(1L)) } returns listOf(hiddenProduct)
        every { mockProductStockRepository.findAllByProductIds(listOf(1L)) } returns listOf(stock)

        val cmd = PlaceOrderCommand(items = listOf(OrderItemCommand(productId = 1L, quantity = 1)), cardType = CardType.SAMSUNG, cardNo = "1234-5678-9012-3456")

        // Act & Assert
        assertThrows<CoreException> {
            orderFacade.placeOrder(userId = 1L, cmd = cmd)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        verify(exactly = 0) { mockProductStockService.decrementStock(any(), any()) }
        verify(exactly = 0) { mockOrderService.createOrder(any(), any(), any(), any()) }
    }

    @Test
    fun `placeOrder() throws BAD_REQUEST when items list is empty`() {
        // Act & Assert
        assertThrows<CoreException> {
            orderFacade.placeOrder(userId = 1L, cmd = PlaceOrderCommand(items = emptyList(), cardType = CardType.SAMSUNG, cardNo = "1234-5678-9012-3456"))
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        verify(exactly = 0) { mockProductService.getById(any()) }
        verify(exactly = 0) { mockOrderService.createOrder(any(), any(), any(), any()) }
    }

    private fun createProduct(
        id: Long = 0L,
        brandId: Long = 1L,
        name: String = "Test Product",
        status: ProductStatus = ProductStatus.ACTIVE,
    ): Product = Product(id = id, brandId = brandId, name = name, description = "desc", price = 10000, status = status)

    private fun createBrand(id: Long = 0L, name: String = "TestBrand"): Brand =
        Brand(id = id, name = name, description = "desc")

    private fun createOrderItem(
        orderId: Long = 0L,
        productId: Long = 1L,
        brandId: Long = 1L,
        price: Int = 10000,
        quantity: Int = 2,
    ): OrderItem = OrderItem(
        orderId = orderId,
        productId = productId,
        productName = "Test Product",
        brandId = brandId,
        brandName = "Test Brand",
        price = price,
        quantity = quantity,
    )

    private fun createOrder(
        id: Long = 0L,
        userId: Long = 1L,
        items: List<OrderItem>,
    ): Order = Order(
        id = id,
        userId = userId,
        items = items,
        originalTotalPrice = items.sumOf { it.subtotal() },
    )
}
