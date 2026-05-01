package com.loopers.application.catalog.product

import com.loopers.application.catalog.brand.BrandResult
import com.loopers.application.catalog.event.ProductViewedEvent
import com.loopers.application.ranking.RankingFacade
import com.loopers.config.redis.CacheException
import com.loopers.domain.catalog.brand.BrandRepository
import com.loopers.domain.catalog.brand.BrandService
import com.loopers.domain.catalog.product.ProductSearchCondition
import com.loopers.domain.catalog.product.ProductService
import com.loopers.domain.catalog.product.ProductStockService
import com.loopers.infrastructure.catalog.product.ProductCacheService
import com.loopers.infrastructure.outbox.KafkaTopics
import com.loopers.infrastructure.outbox.OutboxEventService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductFacade(
    private val productService: ProductService,
    private val brandService: BrandService,
    private val brandRepository: BrandRepository,
    private val productStockService: ProductStockService,
    private val productCacheService: ProductCacheService,
    private val outboxEventService: OutboxEventService,
    private val rankingFacade: RankingFacade,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createProduct(cmd: CreateProductCommand): ProductDetailResult {
        val brand = brandService.getById(cmd.brandId)
        val product = productService.createProduct(
            brandId = cmd.brandId,
            name = cmd.name,
            description = cmd.description,
            price = cmd.price,
        )
        val stock = productStockService.createStock(product.id, cmd.stock)
        productCacheService.evictAllProductLists()
        return ProductDetailResult(
            id = product.id,
            name = product.name,
            description = product.description,
            price = product.price,
            stock = stock.quantity,
            likeCount = product.likeCount,
            brand = BrandResult.from(brand),
        )
    }

    fun getProductDetail(productId: Long, userId: Long? = null): ProductDetailResult {
        // Outbox 저장 (조회수 로깅 — 별도 TX, fire-and-forget)
        try {
            outboxEventService.save(
                aggregateType = "PRODUCT",
                aggregateId = productId.toString(),
                eventType = "PRODUCT_VIEWED",
                payload = ProductViewedEvent(userId = userId, productId = productId),
                topic = KafkaTopics.CATALOG_EVENTS,
                partitionKey = productId.toString(),
            )
        } catch (ex: Exception) {
            log.warn("[ProductFacade] 조회 이벤트 Outbox 저장 실패: productId=$productId", ex)
        }

        productCacheService.getProductDetail(productId)?.let { return enrichWithRank(it) }

        val locked = productCacheService.tryLock(productId)
        try {
            if (!locked) {
                Thread.sleep(50)
                productCacheService.getProductDetail(productId)?.let { return enrichWithRank(it) }
            }
            val result = loadProductDetailFromDb(productId)
            productCacheService.putProductDetail(productId, result)
            return enrichWithRank(result)
        } finally {
            if (locked) productCacheService.unlock(productId)
        }
    }

    /**
     * 캐시/DB 에서 가져온 [ProductDetailResult] 에 오늘의 랭킹 순위를 덧붙인다.
     *
     * 랭킹은 변동이 잦으므로 캐시에 저장하지 않고 매 요청마다 ZREVRANK 를 호출한다.
     * Redis 장애 시에는 rank=null 로 응답을 유지해 main 흐름이 끊기지 않도록 한다.
     */
    private fun enrichWithRank(result: ProductDetailResult): ProductDetailResult {
        val rank = runCatching { rankingFacade.findTodayRank(result.id) }
            .onFailure { log.warn("[ProductFacade] 랭킹 조회 실패: productId=${result.id}", it) }
            .getOrNull()
        return result.copy(rank = rank)
    }

    @Transactional
    fun updateProduct(productId: Long, cmd: UpdateProductCommand): ProductDetailResult {
        val product = productService.update(
            id = productId,
            name = cmd.name,
            description = cmd.description,
            price = cmd.price,
        )
        val stock = productStockService.updateStock(productId, cmd.stock)
        productService.updateStockStatus(productId, stock.quantity)
        val brand = brandService.getById(product.brandId)
        productCacheService.evictProductDetail(productId)
        productCacheService.evictAllProductLists()
        return ProductDetailResult(
            id = product.id,
            name = product.name,
            description = product.description,
            price = product.price,
            stock = stock.quantity,
            likeCount = product.likeCount,
            brand = BrandResult.from(brand),
        )
    }

    @Transactional(readOnly = true)
    fun findProducts(condition: ProductSearchCondition): List<ProductSummaryResult> {
        try {
            productCacheService.getProductList(condition)?.let { return it }
        } catch (e: CacheException) {
            return emptyList()
        }
        val results = loadProductListFromDb(condition)
        productCacheService.putProductList(condition, results)
        return results
    }

    private fun loadProductDetailFromDb(productId: Long): ProductDetailResult {
        val product = productService.getActiveById(productId)
        val brand = brandService.getById(product.brandId)
        val stock = productStockService.getByProductId(productId)
        return ProductDetailResult(
            id = product.id,
            name = product.name,
            description = product.description,
            price = product.price,
            stock = stock.quantity,
            likeCount = product.likeCount,
            brand = BrandResult.from(brand),
        )
    }

    private fun loadProductListFromDb(condition: ProductSearchCondition): List<ProductSummaryResult> {
        val products = productService.findAll(condition)
        val brandIds = products.map { it.brandId }.distinct()
        val brandMap = brandRepository.findAllByIds(brandIds).associateBy { it.id }

        return products.map { product ->
            val brandName = brandMap[product.brandId]?.name ?: ""
            ProductSummaryResult.from(product, brandName)
        }
    }
}
