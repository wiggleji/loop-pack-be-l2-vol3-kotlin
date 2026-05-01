import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

/**
 * Ranking System Benchmark — Week 9 Phase A baseline
 *
 * Measures:
 *   - S3 (ranking_read_light):  GET /api/v1/rankings (light load, page 1)
 *   - S5 (product_detail_rank): GET /api/v1/products/{id} — rank enrichment + outbox PRODUCT_VIEWED (drives S2 writes)
 *   - S3' (ranking_read_heavy): GET /api/v1/rankings heavy concurrent load
 *   - S3'' (ranking_deep_page): deep pagination (page 4, size 50)
 *
 * The `product_detail_rank` scenario doubles as the write-driver (S2) — every detail
 * lookup emits a PRODUCT_VIEWED outbox event that the streamer applies to the ZSET.
 *
 * Run 10 times per phase:
 *   for i in 1 2 3 4 5 6 7 8 9 10; do k6 run k6/ranking-benchmark.js; done
 */

// --- Metrics ---
const rankingReadOk = new Counter('ranking_read_ok');
const rankingReadEmpty = new Counter('ranking_read_empty');
const productDetailOk = new Counter('product_detail_ok');
const productDetailWithRank = new Counter('product_detail_with_rank');
const productDetailWithoutRank = new Counter('product_detail_without_rank');
const rankingReadLatency = new Trend('ranking_read_latency_ms');
const productDetailLatency = new Trend('product_detail_latency_ms');
const readSuccessRate = new Rate('read_success_rate');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID_MAX = Number(__ENV.PRODUCT_ID_MAX || 10_000); // 상위 10k 상품에서 랜덤 조회 (power-law hot set)

export const options = {
  scenarios: {
    // S3: 랭킹 페이지 조회 — light load
    ranking_read_light: {
      executor: 'constant-vus',
      vus: 50,
      duration: '30s',
      exec: 'rankingReadLight',
    },

    // S5 + S2 driver: 상품 상세 조회 — rank enrichment 오버헤드 + 쓰기 경로 (outbox→kafka→streamer) 구동
    product_detail_rank: {
      executor: 'constant-vus',
      vus: 100,
      duration: '30s',
      exec: 'productDetailWithRankTest',
    },

    // S3': 랭킹 페이지 조회 — heavy load (light 종료 후 시작)
    ranking_read_heavy: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5s', target: 200 },
        { duration: '15s', target: 200 },
        { duration: '5s', target: 0 },
      ],
      startTime: '35s',
      exec: 'rankingReadHeavy',
    },

    // S3'': deep pagination
    ranking_deep_page: {
      executor: 'constant-vus',
      vus: 30,
      duration: '15s',
      startTime: '65s',
      exec: 'rankingDeepPage',
    },
  },

  thresholds: {
    http_req_failed: ['rate<0.05'],
    'http_req_duration{name:ranking_page}': ['p(95)<500', 'p(99)<1000'],
    'http_req_duration{name:product_detail}': ['p(95)<800', 'p(99)<1500'],
  },
};

// --- Scenario implementations ---

export function rankingReadLight() {
  const res = http.get(`${BASE_URL}/api/v1/rankings?page=1&size=20`, {
    tags: { name: 'ranking_page' },
  });

  const ok = check(res, {
    'ranking_light: 200': (r) => r.status === 200,
  });
  readSuccessRate.add(ok);

  if (res.status === 200) {
    rankingReadLatency.add(res.timings.duration);
    try {
      const body = JSON.parse(res.body);
      const items = body?.data?.items || [];
      if (items.length === 0) {
        rankingReadEmpty.add(1);
      } else {
        rankingReadOk.add(1);
      }
    } catch (e) {}
  }
  sleep(0.5);
}

export function rankingReadHeavy() {
  const res = http.get(`${BASE_URL}/api/v1/rankings?page=1&size=20`, {
    tags: { name: 'ranking_page' },
  });

  const ok = check(res, {
    'ranking_heavy: 200': (r) => r.status === 200,
  });
  readSuccessRate.add(ok);

  if (res.status === 200) {
    rankingReadLatency.add(res.timings.duration);
    rankingReadOk.add(1);
  }
  sleep(0.1);
}

export function rankingDeepPage() {
  const page = 1 + Math.floor(Math.random() * 5); // 1..5
  const res = http.get(`${BASE_URL}/api/v1/rankings?page=${page}&size=50`, {
    tags: { name: 'ranking_page' },
  });

  check(res, {
    'ranking_deep: 200': (r) => r.status === 200,
  });

  if (res.status === 200) {
    rankingReadLatency.add(res.timings.duration);
    rankingReadOk.add(1);
  }
  sleep(0.2);
}

export function productDetailWithRankTest() {
  const productId = 1 + Math.floor(Math.random() * PRODUCT_ID_MAX);
  const res = http.get(`${BASE_URL}/api/v1/products/${productId}`, {
    tags: { name: 'product_detail' },
  });

  const ok = check(res, {
    'detail: 200': (r) => r.status === 200,
  });
  readSuccessRate.add(ok);

  if (res.status === 200) {
    productDetailLatency.add(res.timings.duration);
    productDetailOk.add(1);
    try {
      const body = JSON.parse(res.body);
      if (body?.data?.rank !== null && body?.data?.rank !== undefined) {
        productDetailWithRank.add(1);
      } else {
        productDetailWithoutRank.add(1);
      }
    } catch (e) {}
  }
  sleep(0.1);
}

// --- Summary ---
export function handleSummary(data) {
  const now = new Date();
  const ts = now.toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const filename = `./k6/results/ranking-benchmark-${ts}.txt`;

  const header = [
    '='.repeat(60),
    `Ranking System Benchmark — ${now.toISOString()}`,
    '='.repeat(60),
    '',
    '[ Scenarios ]',
    '',
    '  ranking_read_light:',
    '    executor:  constant-vus (50 VUs, 30s)',
    '    endpoint:  GET /api/v1/rankings?page=1&size=20',
    '    purpose:   랭킹 페이지 조회 기본 부하',
    '',
    '  product_detail_rank:',
    '    executor:  constant-vus (100 VUs, 30s)',
    '    endpoint:  GET /api/v1/products/{id}',
    '    purpose:   상품 상세 + rank enrichment + outbox→streamer 쓰기 구동',
    '',
    '  ranking_read_heavy:',
    '    executor:  ramping-vus (0→200→0, 25s, starts at 35s)',
    '    purpose:   랭킹 페이지 동시 조회 폭증',
    '',
    '  ranking_deep_page:',
    '    executor:  constant-vus (30 VUs, 15s, starts at 65s)',
    '    endpoint:  GET /api/v1/rankings?page={1..5}&size=50',
    '    purpose:   deep pagination 오버헤드',
    '',
    '[ Thresholds ]',
    '    http_req_failed: rate < 0.05',
    '    ranking_page:    p(95)<500ms, p(99)<1000ms',
    '    product_detail:  p(95)<800ms, p(99)<1500ms',
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
