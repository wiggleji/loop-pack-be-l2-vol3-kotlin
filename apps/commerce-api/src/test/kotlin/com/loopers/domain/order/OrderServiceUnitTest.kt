package com.loopers.domain.order

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
import java.time.LocalDate
import java.time.ZonedDateTime

class OrderServiceUnitTest {

    private val mockRepository = mockk<OrderRepository>()
    private val orderService = OrderService(mockRepository)

    // ─── createOrder ───

    @Test
    fun `createOrder() should persist and return the Order`() {
        // Arrange
        val items = listOf(createOrderItem(price = 10000, quantity = 2))
        val expected = createOrder(userId = 1L, items = items)
        every { mockRepository.save(any()) } returns expected

        // Act
        val result = orderService.createOrder(userId = 1L, items = items)

        // Assert
        assertThat(result.userId).isEqualTo(1L)
        assertThat(result.totalPrice).isEqualTo(20000)
        assertThat(result.status).isEqualTo(OrderStatus.PLACED)
        verify { mockRepository.save(any()) }
    }

    @Test
    fun `createOrder() throws CoreException(BAD_REQUEST) when items is empty`() {
        // Act & Assert
        assertThrows<CoreException> {
            orderService.createOrder(userId = 1L, items = emptyList())
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
        verify(exactly = 0) { mockRepository.save(any()) }
    }

    // ─── getById ───

    @Test
    fun `getById() returns order when it exists`() {
        // Arrange
        val order = createOrder(id = 1L, userId = 1L)
        every { mockRepository.findById(1L) } returns order

        // Act
        val result = orderService.getById(1L)

        // Assert
        assertThat(result.id).isEqualTo(1L)
        assertThat(result.userId).isEqualTo(1L)
    }

    @Test
    fun `getById() throws CoreException(NOT_FOUND) when order does not exist`() {
        // Arrange
        every { mockRepository.findById(99L) } returns null

        // Act & Assert
        assertThrows<CoreException> {
            orderService.getById(99L)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    // ─── getOrders ───

    @Test
    fun `getOrders() returns list when no date range provided`() {
        // Arrange
        val orders = listOf(createOrder(id = 1L, userId = 1L), createOrder(id = 2L, userId = 1L))
        every { mockRepository.findByUserId(1L) } returns orders

        // Act
        val result = orderService.getOrders(userId = 1L, startAt = null, endAt = null)

        // Assert
        assertThat(result).hasSize(2)
        verify { mockRepository.findByUserId(1L) }
    }

    @Test
    fun `getOrders() returns empty list when user has no orders`() {
        // Arrange
        every { mockRepository.findByUserId(1L) } returns emptyList()

        // Act
        val result = orderService.getOrders(userId = 1L, startAt = null, endAt = null)

        // Assert
        assertThat(result).isEmpty()
    }

    @Test
    fun `getOrders() returns filtered list when date range provided`() {
        // Arrange
        val start = LocalDate.of(2024, 1, 1)
        val end = LocalDate.of(2024, 1, 31)
        val orders = listOf(createOrder(id = 1L, userId = 1L))
        every { mockRepository.findByUserIdAndDateRange(1L, start, end) } returns orders

        // Act
        val result = orderService.getOrders(userId = 1L, startAt = start, endAt = end)

        // Assert
        assertThat(result).hasSize(1)
        verify { mockRepository.findByUserIdAndDateRange(1L, start, end) }
    }

    @Test
    fun `getOrders() throws BAD_REQUEST when only startAt is provided`() {
        // Act & Assert
        assertThrows<CoreException> {
            orderService.getOrders(userId = 1L, startAt = LocalDate.of(2024, 1, 1), endAt = null)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `getOrders() throws BAD_REQUEST when only endAt is provided`() {
        // Act & Assert
        assertThrows<CoreException> {
            orderService.getOrders(userId = 1L, startAt = null, endAt = LocalDate.of(2024, 1, 31))
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    // ─── findAll ───

    @Test
    fun `findAll() returns list of orders`() {
        // Arrange
        val orders = listOf(createOrder(id = 1L, userId = 1L), createOrder(id = 2L, userId = 2L))
        every { mockRepository.findAll(0, 20) } returns orders

        // Act
        val result = orderService.findAll(page = 0, size = 20)

        // Assert
        assertThat(result).hasSize(2)
    }

    @Test
    fun `findAll() returns empty list when no orders exist`() {
        // Arrange
        every { mockRepository.findAll(0, 20) } returns emptyList()

        // Act
        val result = orderService.findAll(page = 0, size = 20)

        // Assert
        assertThat(result).isEmpty()
    }

    // ─── getByStatus ───

    @Test
    fun `getByStatus() returns orders with given status`() {
        // Arrange
        val orders = listOf(createOrder(id = 1L, userId = 1L))
        every { mockRepository.findByStatus(OrderStatus.PREPARING, 0, 20) } returns orders

        // Act
        val result = orderService.getByStatus(OrderStatus.PREPARING, 0, 20)

        // Assert
        assertThat(result).hasSize(1)
    }

    // ─── getByStatusAndDateRange ───

    @Test
    fun `getByStatusAndDateRange() returns orders within range`() {
        // Arrange
        val start = LocalDate.of(2024, 1, 1)
        val end = LocalDate.of(2024, 1, 31)
        val orders = listOf(createOrder(id = 1L, userId = 1L))
        every { mockRepository.findByStatusAndDateRange(OrderStatus.DELIVERED, start, end, 0, 20) } returns orders

        // Act
        val result = orderService.getByStatusAndDateRange(OrderStatus.DELIVERED, start, end, 0, 20)

        // Assert
        assertThat(result).hasSize(1)
    }

    // ─── getDelayedOrders ───

    @Test
    fun `getDelayedOrders() returns delayed orders`() {
        // Arrange
        val olderThan = ZonedDateTime.now().minusDays(2)
        val orders = listOf(createOrder(id = 1L, userId = 1L))
        every { mockRepository.findDelayedOrders(OrderStatus.PAID, olderThan, 0, 20) } returns orders

        // Act
        val result = orderService.getDelayedOrders(OrderStatus.PAID, olderThan, 0, 20)

        // Assert
        assertThat(result).hasSize(1)
    }

    // ─── updateStatus ───

    @Test
    fun `updateStatus() applies action and persists`() {
        // Arrange
        val order = createOrder(id = 1L, userId = 1L)
        every { mockRepository.findById(1L) } returns order
        every { mockRepository.updateStatus(any()) } just Runs

        // Act
        val result = orderService.updateStatus(1L) { it.pay(ZonedDateTime.now()) }

        // Assert
        assertThat(result.status).isEqualTo(OrderStatus.PAID)
        verify { mockRepository.updateStatus(any()) }
    }

    @Test
    fun `updateStatus() throws NOT_FOUND when order does not exist`() {
        // Arrange
        every { mockRepository.findById(99L) } returns null

        // Act & Assert
        assertThrows<CoreException> {
            orderService.updateStatus(99L) { it.pay(ZonedDateTime.now()) }
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    private fun createOrderItem(
        orderId: Long = 0L,
        productId: Long = 1L,
        productName: String = "Test Product",
        brandId: Long = 1L,
        brandName: String = "Test Brand",
        price: Int = 10000,
        quantity: Int = 2,
    ): OrderItem = OrderItem(
        orderId = orderId,
        productId = productId,
        productName = productName,
        brandId = brandId,
        brandName = brandName,
        price = price,
        quantity = quantity,
    )

    private fun createOrder(
        id: Long = 0L,
        userId: Long = 1L,
        items: List<OrderItem> = listOf(createOrderItem()),
    ): Order = Order(
        id = id,
        userId = userId,
        items = items,
        originalTotalPrice = items.sumOf { it.subtotal() },
    )
}
