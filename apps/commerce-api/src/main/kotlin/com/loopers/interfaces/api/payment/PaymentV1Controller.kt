package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentCallbackCommand
import com.loopers.application.payment.PaymentCallbackFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentV1Controller(
    private val paymentCallbackFacade: PaymentCallbackFacade,
) : PaymentV1ApiSpec {

    @PostMapping("/callback")
    override fun handleCallback(
        @RequestBody request: PaymentV1Dto.PaymentCallbackRequest,
    ): ApiResponse<PaymentV1Dto.PaymentResponse> {
        val cmd = PaymentCallbackCommand(
            transactionKey = request.transactionKey,
            status = request.status,
            reason = request.reason,
        )
        return paymentCallbackFacade.handleCallback(cmd)
            .let { PaymentV1Dto.PaymentResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
