package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingPeriod

data class RankingPageResult(
    val period: RankingPeriod, // DAILY|WEEKLY|MONTHLY
    val periodKey: String, // DAILY: yyyyMMdd, WEEKLY: yyyy-Www, MONTHLY: yyyy-MM
    val date: String, // 입력 echo (yyyyMMdd)
    val page: Int,
    val size: Int,
    val totalCount: Long,
    val items: List<RankingItemResult>,
)

data class RankingItemResult(
    val rank: Long, // 0-based
    val score: Double,
    val productId: Long,
    val name: String,
    val price: Int,
    val likeCount: Int,
    val brandId: Long,
    val brandName: String,
)
