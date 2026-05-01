package com.loopers.domain.catalog.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductService(
    private val productRepository: ProductRepository,
) {

    @Transactional
    fun createProduct(
        brandId: Long,
        name: String,
        description: String,
        price: Int,
    ): Product {
        val product = Product(
            brandId = brandId,
            name = name,
            description = description,
            price = price,
        )
        return productRepository.save(product)
    }

    @Transactional(readOnly = true)
    fun getById(id: Long): Product =
        productRepository.findById(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[$id] 해당 ID에 해당하는 상품이 존재하지 않습니다.")

    @Transactional(readOnly = true)
    fun getActiveById(id: Long): Product {
        val product = productRepository.findById(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[$id] 해당 ID에 해당하는 상품이 존재하지 않습니다.")
        if (product.status != ProductStatus.ACTIVE)
            throw CoreException(ErrorType.NOT_FOUND, "[$id] 해당 ID에 해당하는 상품이 존재하지 않습니다.")
        return product
    }

    @Transactional
    fun update(id: Long, name: String, description: String, price: Int): Product {
        val product = getById(id)
        product.update(name, description, price)
        return productRepository.save(product)
    }

    @Transactional
    fun updateStockStatus(id: Long, newStock: Int) {
        val product = getById(id)
        if (newStock == 0 && product.status == ProductStatus.ACTIVE) {
            product.markSoldOut()
            productRepository.save(product)
        } else if (newStock > 0 && product.status == ProductStatus.SOLD_OUT) {
            product.restock()
            productRepository.save(product)
        }
    }

    @Transactional
    fun incrementLikeCount(productId: Long) {
        if (!productRepository.incrementLikeCountAtomic(productId))
            throw CoreException(ErrorType.NOT_FOUND, "[$productId] 해당 ID에 해당하는 상품이 존재하지 않습니다.")
    }

    @Transactional
    fun decrementLikeCount(productId: Long) {
        if (!productRepository.decrementLikeCountAtomic(productId))
            throw CoreException(ErrorType.NOT_FOUND, "[$productId] 해당 ID에 해당하는 상품이 존재하지 않습니다.")
    }

    @Transactional
    fun delete(id: Long) {
        productRepository.deleteById(id)
    }

    @Transactional
    fun deleteAllByBrandId(brandId: Long) {
        productRepository.deleteAllByBrandId(brandId)
    }

    @Transactional(readOnly = true)
    fun findAll(condition: ProductSearchCondition): List<Product> =
        productRepository.findAll(condition)
}
