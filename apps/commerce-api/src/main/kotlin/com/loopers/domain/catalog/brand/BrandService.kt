package com.loopers.domain.catalog.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BrandService(
    private val brandRepository: BrandRepository,
) {

    @Transactional
    fun createBrand(name: String, description: String): Brand {
        val brand = Brand(name = name, description = description)
        return brandRepository.save(brand)
    }

    @Transactional(readOnly = true)
    fun getById(id: Long): Brand =
        brandRepository.findById(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[$id] 해당 ID에 해당하는 브랜드가 존재하지 않습니다.")

    @Transactional
    fun update(id: Long, name: String, description: String): Brand {
        val brand = getById(id)
        brand.update(name, description)
        return brandRepository.save(brand)
    }

    @Transactional
    fun delete(id: Long) {
        brandRepository.deleteById(id)
    }

    @Transactional(readOnly = true)
    fun findAll(page: Int, size: Int): List<Brand> =
        brandRepository.findAll(page, size)
}
