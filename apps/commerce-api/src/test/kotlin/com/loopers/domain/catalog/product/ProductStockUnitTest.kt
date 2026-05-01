package com.loopers.domain.catalog.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProductStockUnitTest {

    // ─── init ───

    @Test
    fun `ProductStock init throws CoreException(BAD_REQUEST) when quantity is negative`() {
        assertThrows<CoreException> {
            ProductStock(productId = 1L, quantity = -1)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `ProductStock init allows quantity of 0`() {
        val stock = ProductStock(productId = 1L, quantity = 0)
        assertThat(stock.quantity).isEqualTo(0)
    }

    // ─── isSoldOut ───

    @Test
    fun `isSoldOut should return true when quantity is 0`() {
        val stock = ProductStock(productId = 1L, quantity = 0)
        assertThat(stock.isSoldOut).isTrue()
    }

    @Test
    fun `isSoldOut should return false when quantity is greater than 0`() {
        val stock = ProductStock(productId = 1L, quantity = 1)
        assertThat(stock.isSoldOut).isFalse()
    }

    // ─── validate ───

    @Test
    fun `validate() should pass when quantity equals stock (exact boundary)`() {
        val stock = ProductStock(productId = 1L, quantity = 5)
        stock.validate(5) // no exception
    }

    @Test
    fun `validate() throws CoreException(BAD_REQUEST) when quantity exceeds stock`() {
        val stock = ProductStock(productId = 1L, quantity = 3)
        assertThrows<CoreException> {
            stock.validate(4)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `validate() throws CoreException(BAD_REQUEST) when quantity is zero`() {
        val stock = ProductStock(productId = 1L, quantity = 10)
        assertThrows<CoreException> {
            stock.validate(0)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `validate() throws CoreException(BAD_REQUEST) when quantity is negative`() {
        val stock = ProductStock(productId = 1L, quantity = 10)
        assertThrows<CoreException> {
            stock.validate(-1)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    // ─── decrement ───

    @Test
    fun `decrement() should reduce quantity by given amount`() {
        val stock = ProductStock(productId = 1L, quantity = 10)
        stock.decrement(3)
        assertThat(stock.quantity).isEqualTo(7)
    }

    @Test
    fun `decrement() should succeed when quantity equals stock (boundary)`() {
        val stock = ProductStock(productId = 1L, quantity = 5)
        stock.decrement(5)
        assertThat(stock.quantity).isEqualTo(0)
    }

    @Test
    fun `decrement() throws CoreException(BAD_REQUEST) when quantity exceeds stock`() {
        val stock = ProductStock(productId = 1L, quantity = 3)
        assertThrows<CoreException> {
            stock.decrement(4)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
        assertThat(stock.quantity).isEqualTo(3) // unchanged
    }

    @Test
    fun `decrement() throws CoreException(BAD_REQUEST) when quantity is zero`() {
        val stock = ProductStock(productId = 1L, quantity = 10)
        assertThrows<CoreException> {
            stock.decrement(0)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Test
    fun `decrement() throws CoreException(BAD_REQUEST) when quantity is negative`() {
        val stock = ProductStock(productId = 1L, quantity = 10)
        assertThrows<CoreException> {
            stock.decrement(-1)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    // ─── update ───

    @Test
    fun `update() should set new quantity`() {
        val stock = ProductStock(productId = 1L, quantity = 10)
        stock.update(50)
        assertThat(stock.quantity).isEqualTo(50)
    }

    @Test
    fun `update() allows quantity of 0`() {
        val stock = ProductStock(productId = 1L, quantity = 10)
        stock.update(0)
        assertThat(stock.quantity).isEqualTo(0)
    }

    @Test
    fun `update() throws CoreException(BAD_REQUEST) when quantity is negative`() {
        val stock = ProductStock(productId = 1L, quantity = 10)
        assertThrows<CoreException> {
            stock.update(-1)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
