import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

/**
 * Ranking System Benchmark — Heavy variant
 *
 * Compared to the base benchmark:
 *   - 2× VUs across all scenarios
 *   - 1.5× duration
 *   - 1000 products (10× base), 1000 ZSET entries
 *   - ranking_read_heavy ramps to 400 VUs (2× base 200)
 *   - deep pagination up to page 10 (2× base 5)
 *
 * Run 10 times:
 *   ./k6/run-ranking-benchmark.sh 10 k6/ranking-benchmark-heavy.js
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
const PRODUCT_ID_MAX = Number(__ENV.PRODUCT_ID_MAX || 1028);

export const options = {
  scenarios: {
    ranking_read_light: {
      executor: 'constant-vus',
      vus: 100,
      duration: '45s',
      exec: 'rankingReadLight',
    },

    product_detail_rank: {
      executor: 'constant-vus',
      vus: 200,
      duration: '45s',
      exec: 'productDetailWithRankTest',
    },

    ranking_read_heavy: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5s', target: 400 },
        { duration: '20s', target: 400 },
        { duration: '5s', target: 0 },
      ],
      startTime: '50s',
      exec: 'rankingReadHeavy',
    },

    ranking_deep_page: {
      executor: 'constant-vus',
      vus: 60,
      duration: '20s',
      startTime: '85s',
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
  const page = 1 + Math.floor(Math.random() * 10); // 1..10 (2× base)
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
  const filename = `./k6/results/ranking-benchmark-heavy-${ts}.txt`;

  const header = [
    '='.repeat(60),
    `Ranking System Benchmark (HEAVY) — ${now.toISOString()}`,
    '='.repeat(60),
    '',
    '[ Scenarios ]',
    '',
    '  ranking_read_light:',
    '    executor:  constant-vus (100 VUs, 45s)',
    '    endpoint:  GET /api/v1/rankings?page=1&size=20',
    '    purpose:   랭킹 페이지 조회 기본 부하 (2× base)',
    '',
    '  product_detail_rank:',
    '    executor:  constant-vus (200 VUs, 45s)',
    '    endpoint:  GET /api/v1/products/{id}',
    '    purpose:   상품 상세 + rank enrichment (2× base, 1000 products)',
    '',
    '  ranking_read_heavy:',
    '    executor:  ramping-vus (0→400→0, 30s, starts at 50s)',
    '    purpose:   랭킹 페이지 동시 조회 폭증 (2× base)',
    '',
    '  ranking_deep_page:',
    '    executor:  constant-vus (60 VUs, 20s, starts at 85s)',
    '    endpoint:  GET /api/v1/rankings?page={1..10}&size=50',
    '    purpose:   deep pagination 오버헤드 (2× depth)',
    '',
    '[ Thresholds ]',
    '    http_req_failed: rate < 0.05',
    '    ranking_page:    p(95)<500ms, p(99)<1000ms',
    '    product_detail:  p(95)<800ms, p(99)<1500ms',
    '',
    `[ Target ]  ${BASE_URL}`,
    `[ Products ] id=1..${PRODUCT_ID_MAX}`,
    `[ ZSET entries ] 1000`,
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
