package com.loopers.domain.catalog.product

enum class ProductStatus {
    ACTIVE,       // 판매중
    HIDDEN,       // 숨김 (임시 비노출)
    SOLD_OUT,     // 품절 (재입고 가능)
    SUSPENDED,    // 판매중지 (어드민 제재)
    DISCONTINUED, // 판매종료 (영구 중단, terminal)
}
