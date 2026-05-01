package com.loopers.domain.catalog.brand

interface BrandRepository {
    fun save(brand: Brand): Brand
    fun findById(id: Long): Brand?
    fun findAllByIds(ids: List<Long>): List<Brand>
    fun findAll(page: Int, size: Int): List<Brand>
    fun deleteById(id: Long)
    fun existsById(id: Long): Boolean
}
