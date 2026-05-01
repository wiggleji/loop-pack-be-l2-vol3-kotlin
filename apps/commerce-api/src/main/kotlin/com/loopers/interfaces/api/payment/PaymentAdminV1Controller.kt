package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentRecoveryFacade
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Payment Admin V1 API", description = "결제 관리 API입니다.")
@RestController
@RequestMapping("/api-admin/v1/payments")
class PaymentAdminV1Controller(
    private val paymentService: PaymentService,
    private val paymentRecoveryFacade: PaymentRecoveryFacade,
) {

    @Operation(summary = "미완료 결제 목록 조회")
    @GetMapping("/pending")
    fun getPendingPayments(): ApiResponse<List<PaymentV1Dto.PaymentResponse>> {
        val payments = paymentService.findByStatusIn(
            listOf(PaymentStatus.PENDING, PaymentStatus.UNKNOWN),
        )
        return payments
            .map { PaymentV1Dto.PaymentResponse.from(com.loopers.application.payment.PaymentResult.from(it)) }
            .let { ApiResponse.success(it) }
    }

    @Operation(summary = "개별 결제 복구")
    @PostMapping("/{paymentId}/recover")
    fun recoverPayment(@PathVariable paymentId: Long): ApiResponse<Any> {
        val payment = paymentService.getByOrderId(paymentId)
        paymentRecoveryFacade.recoverSingle(payment.id, payment.orderId, payment.transactionKey)
        return ApiResponse.success()
    }
}
