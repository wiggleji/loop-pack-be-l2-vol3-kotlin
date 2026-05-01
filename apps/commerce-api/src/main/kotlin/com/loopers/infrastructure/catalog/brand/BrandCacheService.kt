package com.loopers.infrastructure.catalog.brand

import com.fasterxml.jackson.core.type.TypeReference
import com.loopers.application.catalog.brand.BrandResult
import com.loopers.config.redis.CacheOperator
import com.loopers.config.redis.CachePolicy
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class BrandCacheService(
    private val cacheOperator: CacheOperator,
) {

    companion object {
        private const val DETAIL_KEY_PREFIX = "brand:detail:"
        private const val LIST_KEY_PREFIX = "brand:list:"
        private val DETAIL_TTL = Duration.ofMinutes(30)
        private val LIST_TTL = Duration.ofMinutes(10)

        private val DETAIL_POLICY = CachePolicy.FALLBACK
        private val LIST_POLICY = CachePolicy.FALLBACK
    }

    fun getBrand(brandId: Long): BrandResult? =
        cacheOperator.get(
            "$DETAIL_KEY_PREFIX$brandId",
            object : TypeReference<BrandResult>() {},
            DETAIL_POLICY,
        )

    fun putBrand(brandId: Long, result: BrandResult) {
        cacheOperator.put("$DETAIL_KEY_PREFIX$brandId", result, DETAIL_TTL)
    }

    fun evictBrand(brandId: Long) {
        cacheOperator.evict("$DETAIL_KEY_PREFIX$brandId")
    }

    fun getBrandList(page: Int, size: Int): List<BrandResult>? =
        cacheOperator.get(
            "$LIST_KEY_PREFIX$page:$size",
            object : TypeReference<List<BrandResult>>() {},
            LIST_POLICY,
        )

    fun putBrandList(page: Int, size: Int, results: List<BrandResult>) {
        cacheOperator.put("$LIST_KEY_PREFIX$page:$size", results, LIST_TTL)
    }

    fun evictAllBrandLists() {
        cacheOperator.evictByPattern("$LIST_KEY_PREFIX*")
    }
}
