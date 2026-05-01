package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.RankingItemResult
import com.loopers.application.ranking.RankingPageResult

class RankingV1Dto {

    data class RankingPageResponse(
        val period: String, // DAILY|WEEKLY|MONTHLY
        val periodKey: String, // yyyyMMdd | yyyy-Www | yyyy-MM
        val date: String, // 입력 echo (yyyyMMdd)
        val page: Int,
        val size: Int,
        val totalCount: Long,
        val items: List<RankingItemResponse>,
    ) {
        companion object {
            fun from(result: RankingPageResult) = RankingPageResponse(
                period = result.period.name,
                periodKey = result.periodKey,
                date = result.date,
                page = result.page,
                size = result.size,
                totalCount = result.totalCount,
                items = result.items.map { RankingItemResponse.from(it) },
            )
        }
    }

    data class RankingItemResponse(
        val rank: Long,
        val score: Double,
        val productId: Long,
        val name: String,
        val price: Int,
        val likeCount: Int,
        val brandId: Long,
        val brandName: String,
    ) {
        companion object {
            fun from(item: RankingItemResult) = RankingItemResponse(
                rank = item.rank,
                score = item.score,
                productId = item.productId,
                name = item.name,
                price = item.price,
                likeCount = item.likeCount,
                brandId = item.brandId,
                brandName = item.brandName,
            )
        }
    }
}
