package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PgPaymentClient
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgPaymentResponse
import com.loopers.domain.payment.PgPaymentStatusResponse
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
@EnableConfigurationProperties(PgClientConfig::class)
class PgPaymentClientImpl(
    pgClientConfig: PgClientConfig,
) : PgPaymentClient {

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(pgClientConfig.baseUrl)
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(pgClientConfig.connectTimeout.toInt())
                setReadTimeout(pgClientConfig.readTimeout.toInt())
            },
        )
        .build()

    override fun requestPayment(request: PgPaymentRequest): PgPaymentResponse {
        return restClient.post()
            .uri("/api/v1/payments")
            .body(request)
            .retrieve()
            .body<PgPaymentResponse>()
            ?: throw CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 요청 응답이 비어있습니다.")
    }

    override fun getPaymentStatus(transactionKey: String): PgPaymentStatusResponse {
        return restClient.get()
            .uri("/api/v1/payments/{transactionKey}", transactionKey)
            .retrieve()
            .body<PgPaymentStatusResponse>()
            ?: throw CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 상태 조회 응답이 비어있습니다.")
    }
}
