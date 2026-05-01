import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

/**
 * Redis Use-Case Benchmark
 *
 * 3가지 Redis 활용 사례의 성능을 측정한다:
 *
 *   Phase 1 — Cache (String)
 *     product detail 첫 호출 (cache miss) vs 반복 호출 (cache hit) 레이턴시 비교
 *
 *   Phase 2 — Ranking (Sorted Set)
 *     ZREVRANGE Top-N 조회 + 상품 상세 rank enrichment 동시 부하
 *
 *   Phase 3 — Cache + Ranking 혼합
 *     캐시된 상품 상세 + 랭킹 조회를 동시에 실행하는 실전 트래픽 시뮬레이션
 *
 * Usage:
 *   k6 run k6/redis-usecase-benchmark.js
 */

// --- Custom Metrics ---
const cacheHitLatency = new Trend('cache_hit_latency_ms');
const cacheMissLatency = new Trend('cache_miss_latency_ms');
const rankingLatency = new Trend('ranking_latency_ms');
const detailWithRankLatency = new Trend('detail_with_rank_latency_ms');
const mixedLatency = new Trend('mixed_latency_ms');
const cacheHitRate = new Rate('cache_hit_rate');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID_MAX = Number(__ENV.PRODUCT_ID_MAX || 1028);

export const options = {
  scenarios: {
    // Phase 1: Cache warm-up → cache hit 측정
    cache_warmup: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 50,
      exec: 'cacheWarmup',
      maxDuration: '30s',
    },

    // Phase 1b: Cache hit 측정 (warm-up 후)
    cache_hit: {
      executor: 'constant-vus',
      vus: 50,
      duration: '15s',
      startTime: '5s',
      exec: 'cacheHitTest',
    },

    // Phase 2: Ranking 조회 부하
    ranking_read: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5s', target: 100 },
        { duration: '15s', target: 100 },
        { duration: '5s', target: 0 },
      ],
      startTime: '25s',
      exec: 'rankingReadTest',
    },

    // Phase 2b: 상품 상세 + rank enrichment
    detail_with_rank: {
      executor: 'constant-vus',
      vus: 100,
      duration: '20s',
      startTime: '25s',
      exec: 'detailWithRankTest',
    },

    // Phase 3: 실전 혼합 트래픽 (Cache + Ranking 동시)
    mixed_traffic: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5s', target: 200 },
        { duration: '20s', target: 200 },
        { duration: '5s', target: 0 },
      ],
      startTime: '50s',
      exec: 'mixedTrafficTest',
    },
  },

  thresholds: {
    http_req_failed: ['rate<0.05'],
    cache_hit_latency_ms: ['p(95)<50'],
    ranking_latency_ms: ['p(95)<100'],
    'http_req_duration{name:product_detail}': ['p(95)<200'],
    'http_req_duration{name:ranking_page}': ['p(95)<200'],
  },
};

// --- Phase 1: Cache ---

// Warm up: first call = cache miss, measures DB + serialize + Redis SET
export function cacheWarmup() {
  const productId = 1 + Math.floor(Math.random() * PRODUCT_ID_MAX);
  const res = http.get(`${BASE_URL}/api/v1/products/${productId}`, {
    tags: { name: 'product_detail' },
  });

  if (res.status === 200) {
    cacheMissLatency.add(res.timings.duration);
  }
  sleep(0.05);
}

// Cache hit: same products should be cached now
export function cacheHitTest() {
  const productId = 1 + Math.floor(Math.random() * 50); // small range — likely cached
  const res = http.get(`${BASE_URL}/api/v1/products/${productId}`, {
    tags: { name: 'product_detail' },
  });

  const ok = check(res, { 'cache_hit: 200': (r) => r.status === 200 });

  if (res.status === 200) {
    cacheHitLatency.add(res.timings.duration);
    cacheHitRate.add(1);
  } else {
    cacheHitRate.add(0);
  }
  sleep(0.1);
}

// --- Phase 2: Ranking ---

export function rankingReadTest() {
  const page = 1 + Math.floor(Math.random() * 5);
  const res = http.get(`${BASE_URL}/api/v1/rankings?page=${page}&size=20`, {
    tags: { name: 'ranking_page' },
  });

  check(res, { 'ranking: 200': (r) => r.status === 200 });

  if (res.status === 200) {
    rankingLatency.add(res.timings.duration);
  }
  sleep(0.1);
}

export function detailWithRankTest() {
  const productId = 1 + Math.floor(Math.random() * PRODUCT_ID_MAX);
  const res = http.get(`${BASE_URL}/api/v1/products/${productId}`, {
    tags: { name: 'product_detail' },
  });

  check(res, { 'detail_rank: 200': (r) => r.status === 200 });

  if (res.status === 200) {
    detailWithRankLatency.add(res.timings.duration);
    try {
      const body = JSON.parse(res.body);
      if (body?.data?.rank !== null && body?.data?.rank !== undefined) {
        // rank enrichment 성공 — ZREVRANK 호출 포함
      }
    } catch (e) {}
  }
  sleep(0.1);
}

// --- Phase 3: Mixed ---

export function mixedTrafficTest() {
  const roll = Math.random();

  if (roll < 0.6) {
    // 60%: 상품 상세 (캐시 히트 기대)
    const productId = 1 + Math.floor(Math.random() * PRODUCT_ID_MAX);
    const res = http.get(`${BASE_URL}/api/v1/products/${productId}`, {
      tags: { name: 'product_detail' },
    });
    if (res.status === 200) mixedLatency.add(res.timings.duration);
  } else if (roll < 0.85) {
    // 25%: 랭킹 페이지
    const page = 1 + Math.floor(Math.random() * 5);
    const res = http.get(`${BASE_URL}/api/v1/rankings?page=${page}&size=20`, {
      tags: { name: 'ranking_page' },
    });
    if (res.status === 200) mixedLatency.add(res.timings.duration);
  } else {
    // 15%: 상품 목록 (리스트 캐시)
    const res = http.get(`${BASE_URL}/api/v1/products?page=0&size=20`, {
      tags: { name: 'product_list' },
    });
    if (res.status === 200) mixedLatency.add(res.timings.duration);
  }

  sleep(0.05);
}

// --- Summary ---
export function handleSummary(data) {
  const now = new Date();
  const ts = now.toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const filename = `./k6/results/redis-usecase-${ts}.txt`;

  const header = [
    '='.repeat(60),
    `Redis Use-Case Benchmark — ${now.toISOString()}`,
    '='.repeat(60),
    '',
    '[ Phase 1 — Cache (String) ]',
    '  cache_warmup:   1 VU × 50 iters — cold start (cache miss)',
    '  cache_hit:      50 VUs, 15s — warm cache (cache hit)',
    '',
    '[ Phase 2 — Ranking (Sorted Set) ]',
    '  ranking_read:   0→100→0 VUs, 25s — ZREVRANGE Top-N',
    '  detail_with_rank: 100 VUs, 20s — product detail + ZREVRANK',
    '',
    '[ Phase 3 — Mixed Traffic ]',
    '  mixed_traffic:  0→200→0 VUs, 30s',
    '  distribution:   60% detail, 25% ranking, 15% list',
    '',
    `[ Target ]  ${BASE_URL}`,
    `[ Products ] id=1..${PRODUCT_ID_MAX}`,
    '',
    '='.repeat(60),
    '',
  ].join('\n');

  const summary = textSummary(data, { indent: ' ', enableColors: false });

  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [filename]: header + summary,
  };
}
