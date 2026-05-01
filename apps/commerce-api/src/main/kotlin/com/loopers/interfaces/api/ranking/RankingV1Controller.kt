package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.RankingFacade
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/rankings")
class RankingV1Controller(
    private val rankingFacade: RankingFacade,
) : RankingV1ApiSpec {

    @GetMapping
    override fun getRankingPage(
        @RequestParam(required = false) period: String?,
        @RequestParam(required = false) date: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<RankingV1Dto.RankingPageResponse> {
        val parsedPeriod = RankingPeriod.parse(period)
        return rankingFacade.getRankingPage(period = parsedPeriod, dateString = date, page = page, size = size)
            .let { RankingV1Dto.RankingPageResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
