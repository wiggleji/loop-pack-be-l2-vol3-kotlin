package com.loopers.domain.catalog.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductStockService(
    private val productStockRepository: ProductStockRepository,
) {

    @Transactional(readOnly = true)
    fun getByProductId(productId: Long): ProductStock =
        productStockRepository.findByProductId(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[$productId] 해당 상품의 재고 정보가 존재하지 않습니다.")

    @Transactional
    fun createStock(productId: Long, quantity: Int): ProductStock {
        val stock = ProductStock(productId = productId, quantity = quantity)
        return productStockRepository.save(stock)
    }

    @Transactional
    fun decrementStock(productId: Long, quantity: Int): ProductStock {
        val stock = productStockRepository.findByProductIdForUpdate(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[$productId] 해당 상품의 재고 정보가 존재하지 않습니다.")
        stock.decrement(quantity)
        return productStockRepository.save(stock)
    }

    @Transactional
    fun updateStock(productId: Long, quantity: Int): ProductStock {
        val stock = getByProductId(productId)
        stock.update(quantity)
        return productStockRepository.save(stock)
    }
}
