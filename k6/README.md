# k6 Load Tests

이 프로젝트의 k6 부하 테스트 스크립트 모음입니다.

## Prerequisites

### k6 설치

```bash
# macOS
brew install k6

# Docker (설치 없이 실행)
docker run --rm -i grafana/k6 run - <script.js
```

### 서비스 실행

k6 테스트를 실행하기 전에 대상 서비스가 동작 중이어야 합니다.

```bash
# 인프라 (MySQL, Redis, Kafka)
docker-compose up -d

# Commerce API
./gradlew :apps:commerce-api:bootRun
```

---

## 테스트 목록

| 스크립트 | 대상 | 총 소요 시간 | 설명 |
|---------|------|-------------|------|
| `pg-benchmark.js` | PG Simulator (`:8082`) | ~2분 15초 | 결제 요청 처리량 & 상태 조회 |
| `queue-benchmark.js` | Commerce API (`:8080`) | ~2분 | 대기열 진입, Polling, 주문 전체 플로우 |
| `queue-token-ttl-test.js` | Commerce API (`:8080`) | ~6분 | 토큰 TTL 만료 검증 |
| `queue-throughput-test.js` | Commerce API (`:8080`) | ~1분 30초 | 처리량 초과 안정성 & 관문 차단 |
| `ranking-benchmark.js` | Commerce API (`:8080`) | ~1분 20초 | Ranking ZSET 조회 / 상품 상세 rank enrichment (Week 9) |

---

## 1. PG Benchmark (`pg-benchmark.js`)

PG(Payment Gateway) 시뮬레이터의 결제 요청 처리량과 상태 조회 성능을 측정합니다.

### 시나리오

| 시나리오 | VUs | 시간 | 설명 |
|---------|-----|------|------|
| `payment_request` | 1 → 10 → 30 → 50 → 0 | 100초 | 결제 요청 부하 (ramping-vus) |
| `status_check` | 10 (constant) | 30초 | 결제 상태 조회 부하 (105초 후 시작) |

### Thresholds

- `http_req_duration` — p(95) < 3000ms
- `http_req_failed` — rate < 50% (PG 60% 성공률 기준)

### 실행

```bash
k6 run k6/pg-benchmark.js
```

### Custom Metrics

| Metric | 설명 |
|--------|------|
| `payment_success` | 결제 성공 건수 |
| `payment_fail` | 결제 실패 건수 |
| `status_pending` | 조회 시 PENDING 상태 건수 |
| `status_success_result` | 조회 시 SUCCESS 상태 건수 |
| `status_failed_result` | 조회 시 FAILED 상태 건수 |
| `status_check_duration` | 상태 조회 응답 시간 |

### 사전 조건

- PG Simulator가 `localhost:8082`에서 실행 중
- Commerce API가 `localhost:8080`에서 실행 중 (callback 수신용)

---

## 2. Queue Benchmark (`queue-benchmark.js`)

대기열 시스템의 진입 폭증, Polling 부하, 주문 전체 플로우를 순차적으로 측정합니다.

### 시나리오

| 시나리오 | VUs | 시간 | 시작 시점 | 설명 |
|---------|-----|------|----------|------|
| `queue_flood` | 0 → 100 → 500 → 1000 → 0 | 50초 | 0초 | 대기열 진입 폭증 |
| `polling_load` | 200 (constant) | 30초 | 55초 | 순번 Polling 부하 |
| `order_with_token` | 50 (constant) | 30초 | 90초 | 대기열 → 토큰 → 주문 전체 플로우 |

### Thresholds

| Threshold | 기준 | 설명 |
|-----------|------|------|
| `http_req_duration` | p(95) < 2000ms | 전체 요청 응답 시간 |
| `queue_enter` | p(99) < 1000ms | 대기열 진입 응답 시간 |
| `queue_position` | p(99) < 500ms | 순번 조회 응답 시간 |
| `http_req_failed` | rate < 30% | 전체 실패율 |

### 실행

```bash
# 기본 실행 (localhost:8080, testuser/TestPass1!)
k6 run k6/queue-benchmark.js

# 환경 변수 지정
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e LOGIN_ID=testuser \
  -e LOGIN_PW='TestPass1!' \
  k6/queue-benchmark.js
```

### 환경 변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `BASE_URL` | `http://localhost:8080` | Commerce API 주소 |
| `LOGIN_ID` | `testuser` | 테스트 유저 loginId 접두사 (VU별 `{LOGIN_ID}-{VU번호}` 생성) |
| `LOGIN_PW` | `TestPass1!` | 테스트 유저 비밀번호 |

### Custom Metrics

| Metric | 설명 |
|--------|------|
| `queue_enter_success` | 대기열 진입 성공 (신규) |
| `queue_enter_duplicate` | 대기열 중복 진입 (이미 대기 중) |
| `position_check_success` | 순번 조회 성공 |
| `token_received` | 입장 토큰 수신 |
| `order_success` | 토큰으로 주문 성공 |
| `order_rejected` | 주문 실패 (토큰 미발급 or 거부) |
| `wait_time_seconds` | 예상 대기 시간 분포 |
| `order_throughput_rate` | 주문 성공률 |

### 사전 조건

- Commerce API가 실행 중이고 `queue.scheduler.enabled=true` (기본값)
- 테스트 유저가 사전 생성되어 있어야 함 (`testuser-1` ~ `testuser-1000`)
- 주문 시나리오(`order_with_token`)를 테스트하려면 상품/재고 데이터도 필요

### 테스트 데이터 준비 예시

```bash
# 유저 1000명 생성 (API 호출 또는 DB 직접 삽입)
for i in $(seq 1 1000); do
  curl -s -X POST http://localhost:8080/api/v1/users \
    -H 'Content-Type: application/json' \
    -d "{
      \"userId\": \"testuser-$i\",
      \"name\": \"Test User $i\",
      \"email\": \"testuser$i@test.com\",
      \"password\": \"TestPass1!\",
      \"birthDate\": \"1990-01-01\"
    }" > /dev/null
done

# 상품 + 재고 생성 (주문 시나리오용)
# Admin API 또는 DB 직접 삽입으로 productId=1 상품 및 충분한 재고 확보
```

---

---

## 3. Queue Token TTL Test (`queue-token-ttl-test.js`)

토큰 TTL(5분) 만료 시 주문이 정상 거부되는지 검증합니다.

### 시나리오

| 시나리오 | VUs | 설명 |
|---------|-----|------|
| `token_fresh` | 5 (1회) | 토큰 발급 직후 주문 → 성공해야 함 |
| `token_expired` | 5 (1회) | 토큰 발급 후 TTL+10초 대기 → 거부되어야 함 |

### Thresholds

| Threshold | 기준 | 설명 |
|-----------|------|------|
| `token_valid_before_ttl` | count >= 5 | 신선한 토큰으로 주문 성공 |
| `token_expired_after_ttl` | count >= 5 | 만료 토큰으로 주문 거부 |
| `token_unexpected_result` | count == 0 | 예상 밖 결과 없음 |

### 실행

```bash
# 기본 실행 (TTL 5분, 총 ~6분 소요)
k6 run k6/queue-token-ttl-test.js

# 서버 TTL을 짧게 조정한 경우 (e.g. 10초)
k6 run -e TOKEN_TTL_SECONDS=10 k6/queue-token-ttl-test.js
```

### Custom Metrics

| Metric | 설명 |
|--------|------|
| `token_valid_before_ttl` | TTL 이내 주문 성공 건수 |
| `token_expired_after_ttl` | TTL 초과 후 정상 거부 건수 |
| `token_unexpected_result` | 예상 밖 결과 (성공해야 할 때 실패, 또는 그 반대) |

---

## 4. Queue Throughput Test (`queue-throughput-test.js`)

설계 기준(175 TPS)을 초과하는 트래픽에서 시스템 안정성과 관문(interceptor) 차단을 검증합니다.

### 시나리오

| 시나리오 | VUs | 시간 | 시작 시점 | 설명 |
|---------|-----|------|----------|------|
| `queue_overflow` | 0 → 2000 → 0 | 20초 | 0초 | 대기열 폭주 (175 TPS × 11배) |
| `order_without_token` | 50 (constant) | 10초 | 25초 | 토큰 없이 주문 → 100% 차단 |
| `order_throughput` | 100 (constant) | 30초 | 40초 | 토큰 기반 주문 처리량 측정 |

### Thresholds

| Threshold | 기준 | 설명 |
|-----------|------|------|
| `order_without_token_rejected` | count > 0 | 토큰 없는 주문 차단 |
| `order_without_token_accepted` | count == 0 | 토큰 없이 통과한 건 0 (관문 무결성) |
| `overflow_enter` | p(95) < 2000ms | 폭주 시에도 진입 응답 2초 이내 |
| `overflow_position` | p(95) < 500ms | 폭주 시에도 순번 조회 500ms 이내 |

### 실행

```bash
k6 run k6/queue-throughput-test.js
```

### Custom Metrics

| Metric | 설명 |
|--------|------|
| `queue_entered_total` | 대기열 진입 성공 건수 |
| `order_without_token_rejected` | 토큰 없는 주문 차단 건수 |
| `order_without_token_accepted` | 토큰 없이 통과한 건수 (0이어야 함) |
| `order_with_token_success` | 토큰 주문 성공 건수 |
| `order_with_token_fail` | 토큰 주문 실패 건수 |
| `order_latency_ms` | 주문 처리 지연 시간 |
| `order_success_rate` | 주문 성공률 |
| `queue_depth` | 대기열 깊이 변화 추이 |

### 결과 해석 포인트

- **`queue_depth` 추이** — 2000명 진입 후 스케줄러가 175 TPS로 소화하면서 점진적으로 감소해야 함
- **`order_without_token_accepted == 0`** — 관문이 100% 차단하는지 (가장 중요)
- **`order_latency_ms` p(99)** — 주문 처리 지연이 설계 기준(200ms) 근처인지

---

## 5. Ranking Benchmark (`ranking-benchmark.js`)

Week 9 Ranking 시스템의 Phase A(단건 ZINCRBY) 기준선을 측정합니다.

### 시나리오

| 시나리오 | VUs | 시간 | 시작 시점 | 설명 |
|---------|-----|------|----------|------|
| `ranking_read_light` | 50 (constant) | 30s | 0s | GET /api/v1/rankings — 기본 조회 부하 |
| `product_detail_rank` | 100 (constant) | 30s | 0s | GET /api/v1/products/{id} — rank enrichment + outbox 쓰기 구동 |
| `ranking_read_heavy` | 0→200→0 | 25s | 35s | 랭킹 페이지 폭증 |
| `ranking_deep_page` | 30 (constant) | 15s | 65s | deep pagination (page 1~5, size 50) |

### Thresholds

| Threshold | 기준 |
|-----------|------|
| `http_req_failed` | rate < 0.05 |
| `ranking_page` | p(95) < 500ms, p(99) < 1000ms |
| `product_detail` | p(95) < 800ms, p(99) < 1500ms |

### 실행 (10회 반복)

```bash
# 단발
k6 run k6/ranking-benchmark.js

# 10회 반복 (분산/평균 측정)
./k6/run-ranking-benchmark.sh 10
```

### 사전 조건

- Commerce API + Commerce Streamer + Redis + Kafka 실행 중
- Product 시드 데이터 존재 (기본 `id=1..10000` 핫셋)
- 환경변수 `PRODUCT_ID_MAX` 로 상품 범위 조정 가능

### 측정 포인트

- **ranking_page p(99)** — ZREVRANGE + product/brand IN 쿼리 Aggregation 지연
- **product_detail p(99)** — 기존 detail 조회 + ZREVRANK enrichment 추가 오버헤드
- **http_req_failed** — Redis/DB 장애 없이 안정적인지
- **product_detail_with_rank / without_rank 비율** — 핫셋 상품의 ZSET 진입율

---

## 결과 확인

모든 테스트 결과는 `k6/results/` 디렉토리에 타임스탬프와 함께 저장됩니다.

```bash
ls k6/results/
# pg-benchmark-2026-03-18T13-17-21.txt
# queue-benchmark-2026-04-01T14-30-00.txt
```

### 결과 해석 포인트

**PG Benchmark**
- `payment_success` vs `payment_fail` — PG 성공률이 기대치(~60%)에 부합하는지
- `status_check_duration` — 상태 조회 지연이 콜백 방식 대비 합리적인지

**Queue Benchmark**
- `queue_enter` p(99) — 1000 VUs 동시 진입 시에도 1초 이내 응답하는지
- `queue_position` p(99) — Redis 기반 순번 조회가 500ms 이내인지
- `token_received` — 스케줄러가 정상적으로 토큰을 발급하고 있는지
- `order_throughput_rate` — 토큰 발급 → 주문 성공 전환율
- `wait_time_seconds` — 유저 체감 대기 시간 분포

---

## 개별 시나리오만 실행

k6는 `--scenario` 플래그로 특정 시나리오만 실행할 수 없지만, 스크립트를 수정하거나 아래처럼 VU/duration을 오버라이드할 수 있습니다.

```bash
# 가볍게 smoke test (VU 5, 10초)
k6 run --vus 5 --duration 10s k6/queue-benchmark.js
```

> **Note:** `--vus`/`--duration` 오버라이드는 `scenarios`가 정의된 스크립트에서는 무시됩니다.
> 개별 시나리오 테스트가 필요하면 스크립트의 `scenarios` 객체에서 불필요한 시나리오를 주석 처리하세요.
