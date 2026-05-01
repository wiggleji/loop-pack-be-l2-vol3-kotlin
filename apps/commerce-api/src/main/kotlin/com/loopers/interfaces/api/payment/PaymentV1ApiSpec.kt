package com.loopers.interfaces.api.payment

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Payment V1 API", description = "결제 관련 API입니다.")
interface PaymentV1ApiSpec {

    @Operation(
        summary = "결제 콜백",
        description = "PG로부터 결제 결과 콜백을 수신합니다.",
    )
    fun handleCallback(request: PaymentV1Dto.PaymentCallbackRequest): ApiResponse<PaymentV1Dto.PaymentResponse>
}
