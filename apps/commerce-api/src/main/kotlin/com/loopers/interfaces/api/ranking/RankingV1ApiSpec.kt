package com.loopers.interfaces.api.ranking

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Ranking V1 API", description = "상품 랭킹 조회 API (DAILY/WEEKLY/MONTHLY)")
interface RankingV1ApiSpec {

    @Operation(
        summary = "상품 랭킹 페이지 조회",
        description = """
            지정 일자(yyyyMMdd, 미지정 시 오늘) 의 상품 랭킹을 페이징으로 조회합니다.

            - period=DAILY (기본): Redis ZSET (실시간 적재) 에서 조회
            - period=WEEKLY      : 주간 MV 테이블 (배치 적재) 에서 ISO 주차 기준 조회
            - period=MONTHLY     : 월간 MV 테이블 (배치 적재) 에서 yyyy-MM 기준 조회
        """,
    )
    fun getRankingPage(
        period: String?,
        date: String?,
        page: Int,
        size: Int,
    ): ApiResponse<RankingV1Dto.RankingPageResponse>
}
