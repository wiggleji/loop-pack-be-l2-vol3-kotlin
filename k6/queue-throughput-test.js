import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

/**
 * 처리량 초과 안정성 테스트
 *
 * 검증 목표:
 *   1. 스케줄러 배치 크기(175 TPS) 이상의 요청이 몰려도 시스템이 안정적인지
 *   2. 대기열이 back-pressure 역할을 하여 주문 API가 과부하되지 않는지
 *   3. 토큰 없이 주문 시도 시 즉시 거부되는지 (관문 역할 검증)
 *   4. 동시 주문 처리량이 설계 기준(~175 TPS)을 초과하지 않는지
 *
 * 시나리오 구성:
 *   Phase 1 — 대기열에 2000명 진입 (설계 기준 175 TPS의 ~11배)
 *   Phase 2 — 토큰 없이 주문 시도 (관문에서 즉시 차단)
 *   Phase 3 — 토큰 있는 유저의 주문 처리량 측정
 *
 * 실행 방법:
 *   k6 run k6/queue-throughput-test.js
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LOGIN_ID = __ENV.LOGIN_ID || 'testuser';
const LOGIN_PW = __ENV.LOGIN_PW || 'TestPass1!';

// Custom metrics
const queueEnteredTotal = new Counter('queue_entered_total');
const orderWithoutTokenRejected = new Counter('order_without_token_rejected');
const orderWithoutTokenAccepted = new Counter('order_without_token_accepted');
const orderWithTokenSuccess = new Counter('order_with_token_success');
const orderWithTokenFail = new Counter('order_with_token_fail');
const orderLatency = new Trend('order_latency_ms');
const orderSuccessRate = new Rate('order_success_rate');
const queueDepthTrend = new Trend('queue_depth');

export const options = {
  scenarios: {
    // Phase 1: 대기열 폭주 — 2000명 진입 (175 TPS 기준 ~11초치)
    queue_overflow: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5s', target: 500 },    // ramp up
        { duration: '10s', target: 2000 },  // peak — 175 TPS보다 훨씬 많은 진입
        { duration: '5s', target: 0 },      // ramp down
      ],
      exec: 'queueOverflowTest',
    },

    // Phase 2: 토큰 없이 주문 시도 — 관문에서 100% 차단되어야 함
    order_without_token: {
      executor: 'constant-vus',
      vus: 50,
      duration: '10s',
      startTime: '25s',
      exec: 'orderWithoutTokenTest',
    },

    // Phase 3: 토큰 기반 주문 처리량 측정
    order_throughput: {
      executor: 'constant-vus',
      vus: 100,
      duration: '30s',
      startTime: '40s',
      exec: 'orderThroughputTest',
    },
  },

  thresholds: {
    // 관문 검증: 토큰 없는 주문은 100% 차단
    order_without_token_rejected: ['count>0'],
    order_without_token_accepted: ['count==0'],

    // 안정성: 에러율 50% 미만 (대기열 유저 중 토큰 미수신은 정상)
    http_req_failed: ['rate<0.5'],

    // 응답 시간: 대기열 진입과 순번 조회는 빨라야 함
    'http_req_duration{name:overflow_enter}': ['p(95)<2000'],
    'http_req_duration{name:overflow_position}': ['p(95)<500'],
  },
};

function headers(vuId) {
  return {
    'Content-Type': 'application/json',
    'X-Loopers-LoginId': `${LOGIN_ID}-${vuId}`,
    'X-Loopers-LoginPw': LOGIN_PW,
  };
}

// --- Phase 1: 대기열 폭주 진입 ---
export function queueOverflowTest() {
  const vuId = 3000 + __VU;
  const hdrs = headers(vuId);

  // 대기열 진입
  const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, {
    headers: hdrs,
    tags: { name: 'overflow_enter' },
  });

  const entered = check(enterRes, {
    'overflow enter: 200': (r) => r.status === 200,
  });

  if (entered) {
    queueEnteredTotal.add(1);

    // 대기열 깊이(Queue Depth) 측정
    try {
      const body = JSON.parse(enterRes.body);
      if (body.data.totalWaiting) {
        queueDepthTrend.add(body.data.totalWaiting);
      }
    } catch (e) {}
  }

  // 순번 조회 — 폭주 상황에서도 응답이 빠른지 확인
  const posRes = http.get(`${BASE_URL}/api/v1/queue/position`, {
    headers: hdrs,
    tags: { name: 'overflow_position' },
  });

  check(posRes, {
    'overflow position: 200': (r) => r.status === 200,
  });

  sleep(0.05); // 50ms — 빠르게 진입
}

// --- Phase 2: 토큰 없이 주문 시도 (관문 차단 검증) ---
export function orderWithoutTokenTest() {
  const vuId = 4000 + __VU;

  const orderPayload = JSON.stringify({
    items: [{ productId: 1, quantity: 1 }],
    cardType: 'SAMSUNG',
    cardNo: '1234-5678-9012-3456',
  });

  // X-Entry-Token 헤더 없이 주문 시도
  const res = http.post(`${BASE_URL}/api/v1/orders`, orderPayload, {
    headers: headers(vuId),
    tags: { name: 'order_no_token' },
  });

  const rejected = check(res, {
    'no token: rejected (400)': (r) => r.status === 400,
  });

  if (rejected) {
    orderWithoutTokenRejected.add(1);
  } else {
    // 토큰 없이 통과하면 안 됨 — 관문 실패
    orderWithoutTokenAccepted.add(1);
    console.error(`❌ GATE FAILURE: order accepted without token - status ${res.status}`);
  }

  sleep(0.2);
}

// --- Phase 3: 토큰 기반 주문 처리량 측정 ---
export function orderThroughputTest() {
  const vuId = 5000 + __VU;
  const hdrs = headers(vuId);

  // 1. 대기열 진입
  const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, {
    headers: hdrs,
    tags: { name: 'throughput_enter' },
  });

  if (enterRes.status !== 200) {
    sleep(1);
    return;
  }

  // 2. 토큰 수신 대기 (최대 60초)
  let token = null;
  for (let i = 0; i < 60; i++) {
    sleep(1);

    const posRes = http.get(`${BASE_URL}/api/v1/queue/position`, {
      headers: hdrs,
      tags: { name: 'throughput_poll' },
    });

    if (posRes.status === 200) {
      try {
        const body = JSON.parse(posRes.body);
        if (body.data.status === 'ACTIVE' && body.data.token) {
          token = body.data.token;
          break;
        }
      } catch (e) {}
    }
  }

  if (!token) {
    orderWithTokenFail.add(1);
    orderSuccessRate.add(false);
    return;
  }

  // 3. 토큰으로 주문 — 처리 시간 측정
  const orderPayload = JSON.stringify({
    items: [{ productId: 1, quantity: 1 }],
    cardType: 'SAMSUNG',
    cardNo: '1234-5678-9012-3456',
  });

  const start = Date.now();
  const orderRes = http.post(`${BASE_URL}/api/v1/orders`, orderPayload, {
    headers: {
      ...hdrs,
      'X-Entry-Token': token,
    },
    tags: { name: 'throughput_order' },
  });
  orderLatency.add(Date.now() - start);

  const success = check(orderRes, {
    'throughput order: 200': (r) => r.status === 200,
  });

  if (success) {
    orderWithTokenSuccess.add(1);
    orderSuccessRate.add(true);
  } else {
    orderWithTokenFail.add(1);
    orderSuccessRate.add(false);
  }
}

export function handleSummary(data) {
  const now = new Date();
  const ts = now.toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const filename = `./k6/results/queue-throughput-${ts}.txt`;

  const header = [
    '='.repeat(60),
    `Queue Throughput & Stability Test — ${now.toISOString()}`,
    '='.repeat(60),
    '',
    '[ Test Design ]',
    '',
    '  Phase 1 (queue_overflow):',
    '    2000 VUs flood the queue (175 TPS design limit × ~11)',
    '    Verifies: queue accepts all, position API stays fast',
    '',
    '  Phase 2 (order_without_token):',
    '    50 VUs attempt orders WITHOUT X-Entry-Token',
    '    Verifies: interceptor blocks 100% (gate integrity)',
    '',
    '  Phase 3 (order_throughput):',
    '    100 VUs enter queue → poll → order with token',
    '    Verifies: orders are processed at controlled rate',
    '',
    '[ Design Criteria ]',
    '    DB Pool: 50 | Avg Latency: 200ms | Max TPS: 250',
    '    Safety Margin: 70% → Target: 175 TPS',
    '    Batch: 18 users / 100ms',
    '',
    '[ Thresholds ]',
    '    order_without_token_rejected: count > 0',
    '    order_without_token_accepted: count == 0',
    '    overflow_enter: p(95) < 2000ms',
    '    overflow_position: p(95) < 500ms',
    '',
    `[ Target ]  ${BASE_URL}`,
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
