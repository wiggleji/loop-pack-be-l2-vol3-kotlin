# Week 4 Implementation Notes

## ✅ Requirements Checklist
- [x] 동시성 버그 증명 (Lost Update on stock / likeCount)
- [x] 비관적 락 (SELECT FOR UPDATE) — 재고 차감
- [x] 원자적 SQL (UPDATE SET col = col + 1) — 좋아요 카운트
- [x] 쿠폰 도메인 4-layer 구현 (CouponTemplate, UserCoupon)
- [x] 쿠폰 적용 주문 (Order에 할인 필드 추가)
- [x] 쿠폰 동시 사용 방지 (낙관적 락 @Version)
- [x] 동시성 테스트 4종 (재고, 좋아요 다수 유저, 좋아요 단일 유저, 쿠폰)
- [x] Product/Stock 분리 — 비관적 락 범위 축소 (products → product_stocks)

## 📁 File Structure

### Product/Stock 분리
- `domain/catalog/product/ProductStock.kt` — 재고 도메인 모델 (validate, decrement, update, isSoldOut)
- `domain/catalog/product/ProductStockRepository.kt` — findByProductId, findByProductIdForUpdate
- `domain/catalog/product/ProductStockService.kt` — getByProductId, createStock, decrementStock(비관적 락), updateStock
- `infrastructure/catalog/product/ProductStockEntity.kt` — product_stocks 테이블, product_id(unique), quantity
- `infrastructure/catalog/product/ProductStockJpaRepository.kt` — Spring Data JPA
- `infrastructure/catalog/product/ProductStockRepositoryImpl.kt` — EntityManager.refresh(PESSIMISTIC_WRITE)

### 동시성 락
- `ProductStockRepository.kt` — `findByProductIdForUpdate` (비관적 락, 재고 전용)
- `ProductJpaRepository.kt` — `incrementLikeCountAtomic`, `decrementLikeCountAtomic` (원자적 SQL)
- `UserCouponEntity.kt` — `@Version` (낙관적 락)
- `OrderFacade.kt` — `productId ASC` 정렬 (데드락 방지)

### 쿠폰 도메인
- `domain/coupon/` — CouponType, UserCouponStatus, CouponTemplate, UserCoupon, Repository interfaces, Services
- `infrastructure/coupon/` — Entities, JPA repositories, Repository implementations
- `application/coupon/` — CouponCommand, CouponResult, CouponFacade
- `interfaces/api/coupon/` — Admin/User controllers, DTOs, API specs

### Order 쿠폰 적용
- `Order.kt` — `originalTotalPrice`, `discountAmount`, `totalPrice`(computed), `userCouponId`
- `OrderEntity.kt` — 할인 필드 3개 + `userCouponId` 추가
- `OrderFacade.kt` — 쿠폰 유효성 검증 → 할인 계산 → 주문 → 쿠폰 사용 처리

## 🏗️ Class Diagram

```mermaid
classDiagram
    namespace Domain_Product {
        class Product {
            +update(name, description, price)
            +requireOrderable()
            +markSoldOut()
            +restock()
        }
        class ProductStock {
            +validate(qty)
            +decrement(qty)
            +update(qty)
            +isSoldOut: Boolean
        }
        class ProductStockRepository {
            <<interface>>
            +findByProductIdForUpdate(productId) ProductStock?
        }
        class ProductStockService {
            +decrementStock(productId, qty) ProductStock
        }
    }

    namespace Domain_Coupon {
        class CouponTemplate {
            +discount(totalPrice) Int
            +isExpired() Boolean
            +requireIssuable()
            +incrementIssuedCount()
        }
        class UserCoupon {
            +requireAvailable()
            +use(orderId)
            +expire()
        }
        class UserCouponRepository {
            <<interface>>
        }
        class UserCouponService {
            +issue(userId, templateId) UserCoupon
            +useForOrder(couponId, orderId) UserCoupon
        }
    }

    namespace Infrastructure_Product {
        class ProductStockEntity {
            +@Table product_stocks
        }
        class ProductStockRepositoryImpl {
            -entityManager: EntityManager
            +findByProductIdForUpdate() PESSIMISTIC_WRITE
        }
    }

    namespace Infrastructure_Coupon {
        class UserCouponEntity {
            +@Version version
        }
        class UserCouponRepositoryImpl
    }

    namespace Application_Order {
        class OrderFacade {
            +placeOrder() 재고비관적락 + 쿠폰낙관적락
        }
    }

    ProductStockService --> ProductStockRepository
    ProductStockRepositoryImpl ..|> ProductStockRepository
    UserCouponService --> UserCouponRepository
    UserCouponRepositoryImpl ..|> UserCouponRepository
    OrderFacade --> ProductStockService
    OrderFacade --> UserCouponService
```

## 🔁 Sequence Diagram — 쿠폰 적용 주문 (Stock 분리 후)

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant OrderFacade
    participant ProductService
    participant ProductStockService
    participant UserCouponService
    participant CouponTemplateService
    participant OrderService

    Client->>OrderFacade: placeOrder(userId, cmd)

    loop 상품 + 재고 조회 (productId ASC 정렬)
        OrderFacade->>ProductService: getById(productId)
        OrderFacade->>ProductStockService: getByProductId(productId)
    end

    OrderFacade->>OrderFacade: requireOrderable + stock.validate

    loop 재고 차감 (비관적 락 on product_stocks)
        OrderFacade->>ProductStockService: decrementStock(productId, qty)
        Note over ProductStockService: findByProductIdForUpdate → PESSIMISTIC_WRITE
        alt 품절 시
            OrderFacade->>ProductService: updateStockStatus(productId, 0)
        end
    end

    alt 쿠폰 사용
        OrderFacade->>UserCouponService: getById(couponId)
        OrderFacade->>CouponTemplateService: getById(templateId)
        OrderFacade->>OrderFacade: 할인 계산 (template.discount)
    end

    OrderFacade->>OrderService: createOrder(userId, items, discountAmount, couponId)

    alt 쿠폰 사용
        OrderFacade->>UserCouponService: useForOrder(couponId, orderId)
        Note over UserCouponService: findById → @Version 낙관적 락으로 동시 사용 방지
    end

    OrderFacade-->>Client: OrderResult
```

## 🔁 Sequence Diagram — 동시 재고 차감 (비관적 락, product_stocks)

```mermaid
sequenceDiagram
    participant Thread1
    participant Thread2
    participant StockRepo
    participant DB

    Thread1->>StockRepo: findByProductIdForUpdate(1)
    StockRepo->>DB: SELECT ... FOR UPDATE on product_stocks (row lock 획득)
    DB-->>StockRepo: quantity=5
    StockRepo-->>Thread1: ProductStock(quantity=5)

    Thread2->>StockRepo: findByProductIdForUpdate(1)
    StockRepo->>DB: SELECT ... FOR UPDATE (대기...)
    Note over DB: Thread1이 lock 보유 중

    Thread1->>Thread1: decrement(1) → quantity=4
    Thread1->>DB: UPDATE product_stocks SET quantity=4, COMMIT
    Note over DB: lock 해제

    DB-->>StockRepo: quantity=4 (Thread2 lock 획득)
    StockRepo-->>Thread2: ProductStock(quantity=4)
    Thread2->>Thread2: decrement(1) → quantity=3
    Thread2->>DB: UPDATE product_stocks SET quantity=3, COMMIT
```

## 🔁 Sequence Diagram — 쿠폰 동시 사용 (낙관적 락)

```mermaid
sequenceDiagram
    participant Thread1
    participant Thread2
    participant UserCouponRepo
    participant DB

    Thread1->>UserCouponRepo: findById(1)
    UserCouponRepo->>DB: SELECT (version=0)
    DB-->>Thread1: UserCoupon(AVAILABLE, version=0)

    Thread2->>UserCouponRepo: findById(1)
    UserCouponRepo->>DB: SELECT (version=0)
    DB-->>Thread2: UserCoupon(AVAILABLE, version=0)

    Thread1->>Thread1: use(orderId=100) → status=USED
    Thread1->>DB: UPDATE SET status='USED' WHERE version=0
    Note over DB: version 0→1, 성공

    Thread2->>Thread2: use(orderId=200) → status=USED
    Thread2->>DB: UPDATE SET status='USED' WHERE version=0
    Note over DB: version already 1 → OptimisticLockException
    Thread2-->>Thread2: 트랜잭션 롤백 (주문도 롤백)
```

## 🎯 Design Decisions

### 1. Product/Stock 분리 — 비관적 락 범위 축소
- **문제**: Product에 stock이 있으면 재고 차감 시 `products` 행 전체를 잠금 → 좋아요, 상태 변경 등 다른 작업도 블로킹
- **해결**: `ProductStock`을 별도 도메인으로 분리, `product_stocks` 테이블에 비관적 락 적용
- **효과**: 재고 락이 좋아요(atomic SQL on `products`)와 독립적으로 동작

### 2. JPA 1차 캐시 우회 — `EntityManager.refresh(PESSIMISTIC_WRITE)`
- **문제**: `@Lock` JPQL 쿼리는 DB에서 `FOR UPDATE`를 실행하지만, 같은 트랜잭션 내에서 이미 로드된 엔티티는 JPA 1차 캐시에서 반환됨 → stale data
- **해결**: `entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE)` — DB에서 최신 데이터를 다시 읽으면서 행 잠금 획득
- **적용 위치**: `ProductStockRepositoryImpl.findByProductIdForUpdate()`

### 3. 쿠폰: 비관적 락 → 낙관적 락 전환
- **이유**: 쿠폰은 단일 사용자 소유 → 동시 경합은 더블클릭/DDOS 같은 극히 드문 상황
- **방법**: `UserCouponEntity`에 `@Version` 추가, `findByIdForUpdate()` 제거
- **충돌 시**: `ObjectOptimisticLockingFailureException` → 트랜잭션 전체 롤백 (주문도 함께 롤백)
- **재시도 불필요**: 충돌 = 이미 다른 요청이 사용 완료 → 실패가 올바른 결과

### 4. 재고 차감: 비관적 락 vs 원자적 SQL 코드 비교

#### A. 현재 구현 — 비관적 락 (채택)

```kotlin
// Domain Model — 비즈니스 룰 소유
class ProductStock(productId: Long, quantity: Int, val id: Long = 0L) {
    var quantity: Int = quantity; private set

    val isSoldOut: Boolean get() = quantity == 0

    fun validate(qty: Int) {
        if (qty <= 0) throw CoreException(BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.")
        if (quantity < qty) throw CoreException(BAD_REQUEST, "재고가 부족합니다. 현재 재고: $quantity, 요청 수량: $qty")
    }

    fun decrement(qty: Int) {
        if (qty <= 0) throw CoreException(BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.")
        if (quantity - qty < 0) throw CoreException(BAD_REQUEST, "재고가 부족합니다. 현재 재고: $quantity, 요청 수량: $qty")
        this.quantity -= qty
    }
}

// Service — SELECT ... FOR UPDATE → 도메인 모델에서 차감
@Service
class ProductStockService(private val productStockRepository: ProductStockRepository) {
    @Transactional
    fun decrementStock(productId: Long, quantity: Int): ProductStock {
        val stock = productStockRepository.findByProductIdForUpdate(productId)  // PESSIMISTIC_WRITE
            ?: throw CoreException(NOT_FOUND, "재고 정보가 존재하지 않습니다.")
        stock.decrement(quantity)           // 비즈니스 룰은 도메인 모델이 소유
        return productStockRepository.save(stock)
    }
}

// Repository — EntityManager.refresh로 행 잠금 획득
class ProductStockRepositoryImpl(...) : ProductStockRepository {
    override fun findByProductIdForUpdate(productId: Long): ProductStock? {
        val entity = productStockJpaRepository.findByProductId(productId) ?: return null
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE)  // SELECT ... FOR UPDATE
        return entity.toDomain()
    }
}

// Facade 호출
val updatedStock = productStockService.decrementStock(item.productId, item.quantity)
if (updatedStock.isSoldOut) {
    productService.updateStockStatus(item.productId, 0)
}
```

**장점:**
- 비즈니스 룰(`validate`, `decrement`, `isSoldOut`)이 도메인 모델에 집중 → **단위 테스트만으로 검증 가능**
- 요구사항 확장 시(예약 재고, 구매 제한, 번들 재고) 도메인 모델에 메서드 추가로 대응
- `decrement()` 후 결과(`isSoldOut`)를 즉시 사용 가능 → 상태 전이 오케스트레이션 자연스러움
- JPA 영속성 컨텍스트와 일관 — dirty checking, 트랜잭션 롤백이 투명하게 동작

**단점:**
- `SELECT ... FOR UPDATE` → 동시 요청이 직렬 대기 (처리량 제한)
- 다중 상품 주문 시 데드락 가능 → `productId` 오름차순 정렬로 해결 필요
- `EntityManager` 의존 — infrastructure 레이어에 JPA 구현 상세가 노출

---

#### B. 대안 — 원자적 SQL (미채택)

```kotlin
// JpaRepository — 비즈니스 룰이 SQL에 존재
interface ProductStockJpaRepository : JpaRepository<ProductStockEntity, Long> {
    fun findByProductId(productId: Long): ProductStockEntity?

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE product_stocks
        SET quantity = quantity - :qty
        WHERE product_id = :productId AND quantity >= :qty
    """, nativeQuery = true)
    fun decrementStock(productId: Long, qty: Int): Int  // affected rows
}

// Service — SQL 결과(affected rows)로 성공/실패 판단
@Service
class ProductStockService(private val productStockRepository: ProductStockRepository) {
    @Transactional
    fun decrementStock(productId: Long, quantity: Int): ProductStock {
        val affected = productStockRepository.decrementStockAtomic(productId, quantity)
        if (affected == 0) {
            throw CoreException(BAD_REQUEST, "재고가 부족합니다.")
        }
        return productStockRepository.findByProductId(productId)!!  // 차감 후 결과를 다시 조회
    }
}

// Repository — EntityManager 불필요
class ProductStockRepositoryImpl(...) : ProductStockRepository {
    override fun decrementStockAtomic(productId: Long, qty: Int): Int =
        productStockJpaRepository.decrementStock(productId, qty)
    // findByProductIdForUpdate() 제거
    // EntityManager 의존 제거
}

// Facade 호출 — 동일
val updatedStock = productStockService.decrementStock(item.productId, item.quantity)
if (updatedStock.isSoldOut) {
    productService.updateStockStatus(item.productId, 0)
}
```

**장점:**
- 잠금 대기 없음 → **높은 처리량** (플래시 세일 등 고경합 시나리오에 유리)
- 데드락 불가 — 단일 UPDATE 문이므로 정렬 불필요
- `EntityManager` 의존 제거 → infrastructure 레이어 단순화
- Product/Stock 분리 덕분에 `product_stocks`는 `quantity`만 관리 → 원자적 SQL이 깔끔하게 적용 가능

**단점:**
- 비즈니스 룰(`quantity >= :qty`)이 SQL에 존재 → **단위 테스트 불가, 통합 테스트 필수**
- 차감 후 결과를 알려면 **추가 SELECT 필요** (`affected rows`만으로는 남은 수량을 알 수 없음)
- 요구사항 확장 시(예약 재고, 구매 제한 등) **모든 룰이 SQL에서 관리되어야 함**
- JPA 영속성 컨텍스트 우회 → `clearAutomatically = true` 필요, 1차 캐시 무효화

---

#### 결론

| 기준 | 비관적 락 (채택) | 원자적 SQL (미채택) |
|---|---|---|
| 비즈니스 룰 위치 | 도메인 모델 (`ProductStock`) | SQL (`WHERE quantity >= :qty`) |
| 단위 테스트 | ✅ 가능 | ❌ 불가 (DB 필요) |
| 처리량 | 직렬 대기 (소-중규모 충분) | 높음 (고경합 유리) |
| 데드락 | 가능 (정렬로 해결) | 불가 |
| 확장성 | 도메인 메서드 추가 | SQL 수정 필요 |
| 차감 후 결과 | 즉시 사용 가능 | 추가 SELECT 필요 |

- **기술적으로 둘 다 동작한다**: native `@Modifying` 쿼리도 Spring `@Transactional` 내에서 실행되므로 롤백 시 함께 롤백됨
- **Product/Stock 분리가 원자적 SQL을 선택지로 만들었다**: 분리 전에는 `products` 행에 stock이 있어 다른 컬럼과 충돌 가능. 분리 후 `product_stocks`는 `quantity`만 관리하므로 원자적 SQL이 깔끔하게 적용 가능. 즉, **느슨한 결합이 전략 선택의 자유도를 높인 사례**
- **현재 선택**: 재고 도메인의 확장 가능성을 고려해 비즈니스 룰을 도메인 모델에 유지하는 비관적 락 채택. 성능 병목 발생 시 원자적 SQL로 전환 비용이 낮음 (Stock 도메인만 변경)

#### 성능 차이 추정 — 락 보유 시간

`OrderFacade.placeOrder()` 흐름에서 비관적 락은 Step 3(재고 차감)에서 획득 → Step 7(쿠폰 사용) 이후 COMMIT까지 보유. 원자적 SQL은 UPDATE 문 실행 동안만 DB 내부 행 잠금.

```
Step 3. 재고 차감 ← 비관적 락 획득 / 원자적 SQL 실행+해제
Step 4. 브랜드 조회 + 스냅샷 생성
Step 5. 쿠폰 할인 계산
Step 6. 주문 생성 (INSERT)
Step 7. 쿠폰 사용 처리 (UPDATE)
        ← COMMIT → 비관적 락 해제
```

| | 비관적 락 | 원자적 SQL |
|---|---|---|
| 락 보유 시간 (추정) | ~10-50ms (Step 3~COMMIT) | ~1-5ms (UPDATE문만) |
| 10 스레드 시 마지막 대기 | ~270ms (9 × 30ms) | ~27ms (9 × 3ms) |
| 차이 | 약 **5~10배** | |

- 정확한 수치는 DB 종류, 커넥션 풀, 네트워크 지연에 따라 달라지며 실제 부하 테스트(`k6`, `JMeter`)로 측정 필요
- 현재 소-중규모에서는 유의미한 병목이 아니며, 병목이 실측되었을 때 전환하는 것이 합리적

### 5. 왜 쿠폰에 원자적 SQL을 쓰지 않는가?
- 쿠폰 사용은 주문 생성과 같은 `@Transactional` 안에서 JPA 엔티티 상태로 관리되어야 함
- 원자적 SQL은 JPA 엔티티 상태를 우회하므로, 같은 트랜잭션 내 다른 JPA 엔티티 연산과 일관성 보장이 어려움
- 다중 쿠폰 적용 시 JPA dirty checking 기반 부분 롤백이 불가능
- **결론**: 다른 엔티티와 함께 JPA 영속성 컨텍스트 안에서 관리되어야 하는 경우 → 낙관적 락이 적합

### 6. 전략 비교표

| | 비관적 락 (Pessimistic) | 낙관적 락 (Optimistic) | 원자적 SQL (Atomic) |
|---|---|---|---|
| **잠금 방식** | DB 행 잠금 (FOR UPDATE) | @Version 컬럼 비교 | 잠금 없음, DB 원자적 UPDATE |
| **블로킹** | Yes — 다른 트랜잭션 대기 | No | No |
| **충돌 시** | 대기 후 진행 | Exception → 실패/재시도 | 충돌 불가 |
| **트랜잭션 참여** | Yes | Yes | Yes (DB 트랜잭션 참여, JPA 영속성 컨텍스트만 우회) |
| **데드락 위험** | Yes (다중 행) → 정렬로 해결 | No | No |
| **적합한 상황** | 높은 경합 + 롤백 필요 | 낮은 경합 + 트랜잭션 내 일관성 | 단순 산술 + 항상 성공 |
| **적용 대상** | 재고 차감 (product_stocks) | 쿠폰 사용 (user_coupons) | 좋아요 카운트 (products) |

### 7. 전략 선택 기준

```
Q1. 비즈니스 룰 없이 단일 컬럼 증감만으로 완결되는가?
├─ Yes → ✅ Atomic SQL (좋아요: count+1, 끝)
└─ No  → Q2. 경합 빈도는?
         ├─ 높음 → ✅ 비관적 락 (재고: 검증→차감→품절전이, 다수 유저 동시)
         └─ 낮음 → ✅ 낙관적 락 (쿠폰: 상태전이, 단일 유저)
```

| Domain  | Q1 | Q2 | Result |
|---------|---|---|---|
| 상품 좋아요  | Yes (count+1, 끝) | — | Atomic SQL |
| 주문 + 재고 | No (validate→decrement→isSoldOut) | 높음 | 비관적 락 |
| 쿠폰      | No (requireAvailable→use 상태전이) | 낮음 | 낙관적 락 |

### 8. 데드락 방지 — productId 오름차순 정렬
- 여러 상품을 동시에 주문할 때, 트랜잭션 A가 상품 1→2 순서로, 트랜잭션 B가 상품 2→1 순서로 잠그면 데드락 발생
- `cmd.items.sortedBy { it.productId }` — 모든 트랜잭션이 동일한 순서로 잠금 → 데드락 방지

### 9. 좋아요 동시성 — UNIQUE 제약 + 트랜잭션 롤백
- `likes` 테이블의 `(userId, productId)` UNIQUE 제약이 중복 방지의 최후 방어선
- `LikeService.existsBy` 체크는 TOCTOU race 있으나, UNIQUE 제약이 보장
- 같은 유저가 동시에 좋아요 시: 1개만 INSERT 성공, 나머지는 UNIQUE 위반 → 트랜잭션 롤백 → `incrementLikeCountAtomic` 미도달
- **결과**: likeCount 정합성 보장, 별도 락 불필요

### 10. 쿠폰 할인 모델
- `Order`에 `originalTotalPrice`, `discountAmount`, `totalPrice`(computed) 분리
- 할인 전/후 금액을 모두 기록하여 감사 추적(audit trail) 가능
- `CouponTemplate.discount(totalPrice)` — FIXED는 정액 할인(totalPrice 초과 방지), RATE는 비율 할인

## 🧪 Test Coverage

### Unit Tests
- `ProductUnitTest`: init 검증, update, incrementLike/decrementLike (7)
- `ProductStockUnitTest`: init, isSoldOut, validate, decrement, update 경계값 (12)
- `ProductServiceUnitTest`: createProduct(no stock), updateStockStatus(ACTIVE↔SOLD_OUT), like atomic (10)
- `ProductStockServiceUnitTest`: getByProductId, createStock, decrementStock, updateStock (7)
- `ProductFacadeUnitTest`: createProduct+stock, getDetail+stock, updateProduct+stock (6)
- `CouponTemplateDomainTest`: FIXED/RATE 할인 계산, 만료, 발급 수량, init 검증 (6)
- `CouponTemplateServiceUnitTest`: CRUD 서비스 (5)
- `UserCouponServiceUnitTest`: issue, useForOrder(findById), 중복 발급, 만료, 수량 초과, 이미 사용 (8)
- `OrderFacadeUnitTest`: placeOrder with ProductStockService + coupon params (5)
- `OrderServiceUnitTest`: createOrder with discount params (8)

### E2E Tests
- `CouponAdminV1ApiE2ETest`: 생성, 목록 조회, 삭제 (4)
- `CouponV1ApiE2ETest`: 발급, 중복 발급, 수량 초과, 인증 실패, 보유 목록 조회 (6)

### Concurrency Tests
- `OrderFacadeConcurrencyTest`: 10 threads × stock 5 → success 5, fail 5, stock 0 (비관적 락 on product_stocks)
- `LikeFacadeConcurrencyTest`: 50명 각자 좋아요 → likeCount = 50 (원자적 SQL)
- `LikeFacadeConcurrencyTest`: 10 threads × 같은 유저 같은 상품 → success 1, fail 9, likeCount = 1 (UNIQUE 제약)
- `CouponFacadeConcurrencyTest`: 5 threads × 같은 쿠폰 → success 1, fail 4 (낙관적 락 @Version)

