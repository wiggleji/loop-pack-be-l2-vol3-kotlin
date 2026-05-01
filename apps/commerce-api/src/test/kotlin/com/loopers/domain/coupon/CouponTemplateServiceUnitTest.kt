package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class CouponTemplateServiceUnitTest {

    private val mockRepository = mockk<CouponTemplateRepository>()
    private val service = CouponTemplateService(mockRepository)

    @Test
    fun `create() should save and return template`() {
        val template = createTemplate()
        every { mockRepository.save(any()) } returns template

        val result = service.create(template)

        assertThat(result.name).isEqualTo("10% 할인 쿠폰")
        verify { mockRepository.save(any()) }
    }

    @Test
    fun `getById() should return template when exists`() {
        val template = createTemplate(id = 1L)
        every { mockRepository.findById(1L) } returns template

        val result = service.getById(1L)

        assertThat(result.id).isEqualTo(1L)
    }

    @Test
    fun `getById() should throw NOT_FOUND when not exists`() {
        every { mockRepository.findById(99L) } returns null

        assertThrows<CoreException> {
            service.getById(99L)
        }.also {
            assertThat(it.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @Test
    fun `findAll() should return all templates`() {
        every { mockRepository.findAll() } returns listOf(createTemplate(id = 1L), createTemplate(id = 2L))

        val result = service.findAll()

        assertThat(result).hasSize(2)
    }

    @Test
    fun `delete() should call repository deleteById`() {
        every { mockRepository.deleteById(1L) } returns Unit

        service.delete(1L)

        verify { mockRepository.deleteById(1L) }
    }

    private fun createTemplate(
        id: Long = 0L,
        name: String = "10% 할인 쿠폰",
    ): CouponTemplate = CouponTemplate(
        id = id,
        name = name,
        type = CouponType.RATE,
        discountValue = 10,
        minOrderAmount = 10000,
        expiresAt = LocalDate.now().plusDays(30),
    )
}
