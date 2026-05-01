# DataSeedRunner

e-commerce 성능 테스트용 테스트 데이터 생성기.
실제 커머스 서비스의 데이터 분포 패턴을 반영하여 인덱스/캐시 최적화 전후 성능 비교에 활용한다.

## 실행 방법

```bash
# CLI 인자로 실행 (1회성)
./gradlew :apps:commerce-api:bootRun --args='--app.data-seed.enabled=true'

# application.yml local 프로필에 추가 (local 개발 시 항상)
app:
  data-seed:
    enabled: true

# 테스트 코드에서 사용
@TestPropertySource(properties = ["app.data-seed.enabled=true"])
```

## 실행 조건

- `app.data-seed.enabled=true` 설정 시에만 Bean 생성 (`@ConditionalOnProperty`)
- **멱등(Idempotent)**: `products` 테이블에 데이터가 이미 존재하면 스킵
- 재실행 시 데이터를 초기화하려면 수동으로 테이블을 TRUNCATE 해야 한다

## 데이터 규모

| 테이블 | 건수 | 비고 |
|--------|------|------|
| `brands` | 500 | 95% ACTIVE, 5% INACTIVE |
| `products` | 50,000 | 85% ACTIVE, 7% SOLD_OUT, 5% HIDDEN, 3% DISCONTINUED |
| `product_stocks` | 50,000 | 상품당 1:1, 수량 0~199 |
| `orders` | 100,000 | 최근 6개월, 유저 10,000명 |
| `order_items` | ~200,000 | 주문당 1~3개 |
| `likes` | 100,000 | UNIQUE(user_id, product_id) |
| `coupon_templates` | 20 | FIXED/RATE 반반 |
| `user_coupons` | 30,000 | 70% AVAILABLE, 30% USED |

## 분포 모델

실제 커머스 서비스에서는 균등 분포(uniform)가 아닌 **멱법칙(power-law)** 분포가 일반적이다.
소수의 인기 브랜드, 파워 유저, 인기 상품이 전체 데이터의 대부분을 차지한다.

### 핵심 분포 함수

```
powerLawId(max, alpha): id = floor(max * u^alpha) + 1,  u ~ Uniform(0,1)
```

- `alpha = 1.0` → 균등 분포
- `alpha = 2.0` → 상위 ~30%가 전체의 ~70% (중간 편중)
- `alpha = 2.5` → 상위 ~20%가 전체의 ~75% (강한 편중)
- `alpha = 3.0` → 상위 ~15%가 전체의 ~80% (매우 강한 편중)

### 테이블별 분포

#### 1. 브랜드-상품 (products.brand_id)

```
분포: powerLawId(500, alpha=2.0)
```

| 브랜드 그룹 | 상품 비율 | 실제 커머스 |
|-------------|----------|------------|
| 상위 50개 (10%) | ~70% | Nike, Apple 등 메가 브랜드 |
| 중위 150개 (30%) | ~20% | 중견 브랜드 |
| 하위 300개 (60%) | ~10% | 소규모/신규 브랜드 |

**인덱스 영향**: `WHERE brand_id = ?`의 선택도(selectivity)가 브랜드마다 크게 다르다.
메가 브랜드(brand_id=1) 조회 시 수천 행 반환 vs 소규모 브랜드 조회 시 수 행 반환.
→ 복합 인덱스 `(status, brand_id, sort_col)`이 두 케이스 모두에서 효과적인지 EXPLAIN으로 검증.

#### 2. 유저-주문 (orders.user_id)

```
분포: powerLawId(10000, alpha=2.5)
```

| 유저 그룹 | 주문 비율 | 실제 커머스 |
|-----------|----------|------------|
| 파워 유저 상위 500명 (5%) | ~50% | 충성 고객, 리셀러 |
| 일반 유저 (45%) | ~40% | 월 1~2회 주문 |
| 비활성 유저 (50%) | ~10% | 가입 후 1~2회 주문 |

**인덱스 영향**: `WHERE user_id = ?` 결과 행 수가 유저마다 극단적으로 다르다.
파워 유저(user_id=1) 조회 시 수백 행 vs 비활성 유저 조회 시 1~2행.
→ `(user_id, created_at)` 복합 인덱스가 파워 유저의 날짜 범위 쿼리에서 효과적인지 검증.

#### 3. 유저-좋아요 (likes.user_id)

```
분포: powerLawId(10000, alpha=2.5)
```

동일한 파워 유저 편중. 상위 5% 유저가 전체 좋아요의 ~50%.

#### 4. 상품-좋아요 (likes.product_id → products.like_count)

```
분포: powerLawIdLong(maxProductId, alpha=2.0)
```

| 상품 그룹 | 좋아요 비율 | 실제 커머스 |
|-----------|-----------|------------|
| 인기 상품 (상위 10%) | ~70% | 베스트셀러, 바이럴 상품 |
| 일반 상품 (30%) | ~20% | 일반 판매 상품 |
| 비인기 상품 (60%) | ~10% | 신규/비활성 상품 |

**like_count 동기화**: 좋아요 생성 후 `products.like_count`를 실제 `likes` 행 수로 UPDATE.
→ `ORDER BY like_count DESC` (POPULAR 정렬)이 실제 분포에서 인덱스를 올바르게 활용하는지 검증.

**기대 결과**:
- 인기 상품(id=1): like_count ~500~1,000
- 일반 상품: like_count ~1~10
- 비인기 상품: like_count = 0 (전체의 ~30%)

#### 5. 주문 날짜 (orders.created_at)

```
분포: recentBiasedDayOffset(180) → offset = floor(180 * u^0.5)
```

| 기간 | 주문 비율 | 실제 커머스 |
|------|----------|------------|
| 최근 30일 | ~33% | 성장 중인 서비스의 최근 트래픽 집중 |
| 최근 60일 | ~58% | |
| 최근 90일 | ~70% | |
| 전체 180일 | 100% | |

**인덱스 영향**: `WHERE user_id = ? AND created_at BETWEEN ? AND ?` 쿼리에서
최근 날짜 범위일수록 더 많은 행이 매칭 → 인덱스 효율성 차이 검증.

#### 6. 상품 상태 (products.status)

```
분포: 85% ACTIVE / 7% SOLD_OUT / 5% HIDDEN / 3% DISCONTINUED
```

**인덱스 영향**: `WHERE status = 'ACTIVE'`는 전체의 85% → 선택도가 낮다.
단독 `status` 인덱스는 비효율적이지만, 복합 인덱스 `(status, brand_id, ...)`의 선두 컬럼으로는
동등 조건 필터 역할을 하므로 효과적.

## EXPLAIN 테스트 쿼리

데이터 생성 후 아래 쿼리로 인덱스 적용 전/후 성능을 비교한다.

```sql
-- 1. 상품 목록 — LATEST (브랜드 필터, 메가 브랜드)
EXPLAIN SELECT * FROM products
WHERE deleted_at IS NULL AND status = 'ACTIVE' AND brand_id = 1
ORDER BY created_at DESC LIMIT 20;

-- 2. 상품 목록 — LATEST (브랜드 필터, 소규모 브랜드)
EXPLAIN SELECT * FROM products
WHERE deleted_at IS NULL AND status = 'ACTIVE' AND brand_id = 450
ORDER BY created_at DESC LIMIT 20;

-- 3. 상품 목록 — LATEST (브랜드 필터 없음)
EXPLAIN SELECT * FROM products
WHERE deleted_at IS NULL AND status = 'ACTIVE'
ORDER BY created_at DESC LIMIT 20;

-- 4. 상품 목록 — PRICE_ASC
EXPLAIN SELECT * FROM products
WHERE deleted_at IS NULL AND status = 'ACTIVE' AND brand_id = 1
ORDER BY price ASC LIMIT 20;

-- 5. 상품 목록 — POPULAR
EXPLAIN SELECT * FROM products
WHERE deleted_at IS NULL AND status = 'ACTIVE' AND brand_id = 1
ORDER BY like_count DESC LIMIT 20;

-- 6. 주문 조회 — 파워 유저
EXPLAIN SELECT * FROM orders WHERE user_id = 1;

-- 7. 주문 조회 — 일반 유저
EXPLAIN SELECT * FROM orders WHERE user_id = 5000;

-- 8. 주문 날짜 범위 — 파워 유저, 최근 30일
EXPLAIN SELECT * FROM orders
WHERE user_id = 1
  AND created_at >= NOW() - INTERVAL 30 DAY
  AND created_at <= NOW()
  AND deleted_at IS NULL;

-- 9. 브랜드 목록
EXPLAIN SELECT * FROM brands
WHERE deleted_at IS NULL AND status = 'ACTIVE';

-- 10. 좋아요 — 유저별 (파워 유저)
EXPLAIN SELECT * FROM likes
WHERE user_id = 1 AND deleted_at IS NULL;

-- 11. 좋아요 — 상품별 (인기 상품)
EXPLAIN SELECT * FROM likes
WHERE product_id = 1 AND deleted_at IS NULL;

-- 12. 유저 쿠폰 — 파워 유저
EXPLAIN SELECT * FROM user_coupons
WHERE user_id = 1 AND deleted_at IS NULL;
```

### EXPLAIN 결과 확인 포인트

| 컬럼 | 좋은 값 | 나쁜 값 | 의미 |
|------|---------|---------|------|
| `type` | `ref`, `range`, `eq_ref` | `ALL` | ALL = 풀 테이블 스캔 |
| `key` | 인덱스 이름 | `NULL` | NULL = 인덱스 미사용 |
| `rows` | 실제 결과보다 약간 큰 값 | 테이블 전체 행 수 | 스캔 행 수 추정 |
| `Extra` | `Using index` | `Using filesort` | filesort = 정렬에 인덱스 미사용 |

## 기술 구현

- **JDBC batch insert**: JPA `save()` 대신 `JdbcTemplate.batchUpdate()` 사용 (1,000건 단위)
- **멱등성**: `products` 테이블 카운트 체크 → 데이터 존재 시 스킵
- **UNIQUE 제약 조건**: `likes`, `user_coupons`는 in-memory `HashSet` 으로 사전 중복 제거
- **like_count 동기화**: 좋아요 INSERT 후 `UPDATE products SET like_count = (SELECT COUNT(*) FROM likes)` 실행
- **분포 로깅**: 생성 완료 후 주요 분포 통계를 로그로 출력하여 검증 가능
