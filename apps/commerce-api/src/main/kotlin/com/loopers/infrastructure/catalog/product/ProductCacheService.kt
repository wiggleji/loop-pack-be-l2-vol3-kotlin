package com.loopers.infrastructure.catalog.product

import com.fasterxml.jackson.core.type.TypeReference
import com.loopers.application.catalog.product.ProductDetailResult
import com.loopers.application.catalog.product.ProductSummaryResult
import com.loopers.config.redis.CacheOperator
import com.loopers.config.redis.CachePolicy
import com.loopers.domain.catalog.product.ProductSearchCondition
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class ProductCacheService(
    private val cacheOperator: CacheOperator,
) {

    companion object {
        private const val DETAIL_KEY_PREFIX = "product:detail:"
        private const val LIST_KEY_PREFIX = "product:list:"
        private const val LOCK_KEY_PREFIX = "product:detail:lock:"
        private val DETAIL_TTL = Duration.ofMinutes(5)
        private val LIST_TTL = Duration.ofSeconds(30)
        private val LOCK_TTL = Duration.ofSeconds(3)

        private val DETAIL_POLICY = CachePolicy.FALLBACK
        private val LIST_POLICY = CachePolicy.FAIL_FAST
    }

    fun getProductDetail(productId: Long): ProductDetailResult? =
        cacheOperator.get(
            "$DETAIL_KEY_PREFIX$productId",
            object : TypeReference<ProductDetailResult>() {},
            DETAIL_POLICY,
        )

    fun putProductDetail(productId: Long, result: ProductDetailResult) {
        cacheOperator.put("$DETAIL_KEY_PREFIX$productId", result, DETAIL_TTL)
    }

    fun evictProductDetail(productId: Long) {
        cacheOperator.evict("$DETAIL_KEY_PREFIX$productId")
    }

    fun getProductList(condition: ProductSearchCondition): List<ProductSummaryResult>? =
        cacheOperator.get(
            buildListKey(condition),
            object : TypeReference<List<ProductSummaryResult>>() {},
            LIST_POLICY,
        )

    fun putProductList(condition: ProductSearchCondition, results: List<ProductSummaryResult>) {
        cacheOperator.put(buildListKey(condition), results, LIST_TTL)
    }

    fun evictAllProductLists() {
        cacheOperator.evictByPattern("$LIST_KEY_PREFIX*")
    }

    fun tryLock(productId: Long): Boolean =
        cacheOperator.tryLock("$LOCK_KEY_PREFIX$productId", LOCK_TTL)

    fun unlock(productId: Long) {
        cacheOperator.unlock("$LOCK_KEY_PREFIX$productId")
    }

    private fun buildListKey(condition: ProductSearchCondition): String =
        "$LIST_KEY_PREFIX${condition.brandId}:${condition.sort}:${condition.page}:${condition.size}"
}
