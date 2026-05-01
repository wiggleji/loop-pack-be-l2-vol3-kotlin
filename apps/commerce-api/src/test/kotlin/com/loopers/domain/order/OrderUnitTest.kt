package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime

class OrderUnitTest {

    // ─── OrderItem.subtotal ───

    @Test
    fun `subtotal() should return price multiplied by quantity`() {
        // Arrange
        val item = createOrderItem(price = 10000, quantity = 3)

        // Act & Assert
        assertThat(item.subtotal()).isEqualTo(30000)
    }

    @Test
    fun `subtotal() should return 0 when price is 0`() {
        // Arrange
        val item = createOrderItem(price = 0, quantity = 5)

        // Assert
        assertThat(item.subtotal()).isEqualTo(0)
    }

    // ─── Order.create ───

    @Test
    fun `Order create() should compute correct totalPrice`() {
        // Arrange
        val items = listOf(
            createOrderItem(price = 10000, quantity = 2),
            createOrderItem(price = 5000, quantity = 3),
        )

        // Act
        val order = Order.create(userId = 1L, items = items)

        // Assert
        assertThat(order.totalPrice).isEqualTo(35000) // 20000 + 15000
        assertThat(order.items).hasSize(2)
        assertThat(order.userId).isEqualTo(1L)
    }

    @Test
    fun `Order create() should set status to PLACED`() {
        val items = listOf(createOrderItem())
        val order = Order.create(userId = 1L, items = items)
        assertThat(order.status).isEqualTo(OrderStatus.PLACED)
    }

    @Test
    fun `Order create() throws CoreException(BAD_REQUEST) when items is empty`() {
        // Act & Assert
        assertThrows<CoreException> {
            Order.create(userId = 1L, items = emptyList())
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    // ─── OrderItem init ───

    @Test
    fun `OrderItem init throws CoreException(BAD_REQUEST) when quantity is 0`() {
        assertThrows<CoreException> {
            createOrderItem(quantity = 0)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `OrderItem init throws CoreException(BAD_REQUEST) when productName is blank`() {
        assertThrows<CoreException> {
            createOrderItem(productName = "  ")
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `OrderItem init throws CoreException(BAD_REQUEST) when brandName is blank`() {
        assertThrows<CoreException> {
            createOrderItem(brandName = "  ")
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    // ─── State transitions: valid ───

    @Test
    fun `pay() should transition PLACED to PAID and set paidAt`() {
        val order = createPlacedOrder()
        val now = ZonedDateTime.now()

        order.pay(now)

        assertThat(order.status).isEqualTo(OrderStatus.PAID)
        assertThat(order.paidAt).isEqualTo(now)
    }

    @Test
    fun `startPreparing() should transition PAID to PREPARING`() {
        val order = createPlacedOrder()
        order.pay(ZonedDateTime.now())

        order.startPreparing()

        assertThat(order.status).isEqualTo(OrderStatus.PREPARING)
    }

    @Test
    fun `ship() should transition PREPARING to SHIPPING and set shippedAt`() {
        val order = createPlacedOrder()
        order.pay(ZonedDateTime.now())
        order.startPreparing()
        val now = ZonedDateTime.now()

        order.ship(now)

        assertThat(order.status).isEqualTo(OrderStatus.SHIPPING)
        assertThat(order.shippedAt).isEqualTo(now)
    }

    @Test
    fun `deliver() should transition SHIPPING to DELIVERED and set deliveredAt`() {
        val order = createPlacedOrder()
        order.pay(ZonedDateTime.now())
        order.startPreparing()
        order.ship(ZonedDateTime.now())
        val now = ZonedDateTime.now()

        order.deliver(now)

        assertThat(order.status).isEqualTo(OrderStatus.DELIVERED)
        assertThat(order.deliveredAt).isEqualTo(now)
    }

    @Test
    fun `cancel() should transition PLACED to CANCELLED and set cancelledAt`() {
        val order = createPlacedOrder()
        val now = ZonedDateTime.now()

        order.cancel(now)

        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(order.cancelledAt).isEqualTo(now)
    }

    @Test
    fun `cancel() should transition PAID to CANCELLED`() {
        val order = createPlacedOrder()
        order.pay(ZonedDateTime.now())

        order.cancel(ZonedDateTime.now())

        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
    }

    @Test
    fun `refund() should transition DELIVERED to REFUNDED and set cancelledAt`() {
        val order = createPlacedOrder()
        order.pay(ZonedDateTime.now())
        order.startPreparing()
        order.ship(ZonedDateTime.now())
        order.deliver(ZonedDateTime.now())
        val now = ZonedDateTime.now()

        order.refund(now)

        assertThat(order.status).isEqualTo(OrderStatus.REFUNDED)
        assertThat(order.cancelledAt).isEqualTo(now)
    }

    // ─── State transitions: invalid ───

    @Test
    fun `pay() throws BAD_REQUEST when status is not PLACED`() {
        val order = createPlacedOrder()
        order.pay(ZonedDateTime.now())

        assertThrows<CoreException> {
            order.pay(ZonedDateTime.now())
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `startPreparing() throws BAD_REQUEST when status is not PAID`() {
        val order = createPlacedOrder()

        assertThrows<CoreException> {
            order.startPreparing()
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `ship() throws BAD_REQUEST when status is not PREPARING`() {
        val order = createPlacedOrder()
        order.pay(ZonedDateTime.now())

        assertThrows<CoreException> {
            order.ship(ZonedDateTime.now())
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `deliver() throws BAD_REQUEST when status is not SHIPPING`() {
        val order = createPlacedOrder()
        order.pay(ZonedDateTime.now())
        order.startPreparing()

        assertThrows<CoreException> {
            order.deliver(ZonedDateTime.now())
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `cancel() throws BAD_REQUEST when status is PREPARING`() {
        val order = createPlacedOrder()
        order.pay(ZonedDateTime.now())
        order.startPreparing()

        assertThrows<CoreException> {
            order.cancel(ZonedDateTime.now())
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `cancel() throws BAD_REQUEST when status is SHIPPING`() {
        val order = createPlacedOrder()
        order.pay(ZonedDateTime.now())
        order.startPreparing()
        order.ship(ZonedDateTime.now())

        assertThrows<CoreException> {
            order.cancel(ZonedDateTime.now())
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `refund() throws BAD_REQUEST when status is not DELIVERED`() {
        val order = createPlacedOrder()
        order.pay(ZonedDateTime.now())

        assertThrows<CoreException> {
            order.refund(ZonedDateTime.now())
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    // ─── Helpers ───

    private fun createPlacedOrder(): Order = Order.create(
        userId = 1L,
        items = listOf(createOrderItem()),
    )

    private fun createOrderItem(
        orderId: Long = 0L,
        productId: Long = 1L,
        productName: String = "Test Product",
        brandId: Long = 1L,
        brandName: String = "Test Brand",
        price: Int = 10000,
        quantity: Int = 1,
    ): OrderItem = OrderItem(
        orderId = orderId,
        productId = productId,
        productName = productName,
        brandId = brandId,
        brandName = brandName,
        price = price,
        quantity = quantity,
    )
}
