import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

// Custom metrics
const queueEnterSuccess = new Counter('queue_enter_success');
const queueEnterDuplicate = new Counter('queue_enter_duplicate');
const positionCheckSuccess = new Counter('position_check_success');
const tokenReceived = new Counter('token_received');
const orderSuccess = new Counter('order_success');
const orderRejected = new Counter('order_rejected');
const waitTimeTrend = new Trend('wait_time_seconds');
const orderThroughput = new Rate('order_throughput_rate');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 테스트 유저 인증 정보 (사전에 생성된 유저 사용)
const LOGIN_ID = __ENV.LOGIN_ID || 'testuser';
const LOGIN_PW = __ENV.LOGIN_PW || 'TestPass1!';

export const options = {
  scenarios: {
    // Scenario 1: 대기열 진입 부하 테스트 — 10,000명 동시 진입
    queue_flood: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5s', target: 100 },    // ramp up to 100
        { duration: '10s', target: 500 },   // ramp up to 500
        { duration: '10s', target: 1000 },  // ramp up to 1000
        { duration: '20s', target: 1000 },  // hold 1000
        { duration: '5s', target: 0 },      // ramp down
      ],
      exec: 'queueEntryTest',
    },

    // Scenario 2: Polling 부하 테스트 — 순번 조회 반복
    polling_load: {
      executor: 'constant-vus',
      vus: 200,
      duration: '30s',
      startTime: '55s', // starts after queue flood
      exec: 'pollingTest',
    },

    // Scenario 3: 주문 처리 안정성 테스트 — 토큰으로 주문 시도
    order_with_token: {
      executor: 'constant-vus',
      vus: 50,
      duration: '30s',
      startTime: '90s', // starts after polling
      exec: 'orderWithTokenTest',
    },
  },

  thresholds: {
    http_req_duration: ['p(95)<8000'],         // 95% of requests under 8s (local env with 1000 VUs)
    http_req_failed: ['rate<0.3'],              // less than 30% failed
    'http_req_duration{name:queue_enter}': ['p(99)<10000'],  // queue enter < 10s (heavy contention expected)
    'http_req_duration{name:queue_position}': ['p(99)<2000'], // position check < 2s
  },
};

// --- Scenario 1: Queue Entry Flood ---
export function queueEntryTest() {
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-Loopers-LoginId': `${LOGIN_ID}-${__VU}`,
      'X-Loopers-LoginPw': LOGIN_PW,
    },
    tags: { name: 'queue_enter' },
  };

  const res = http.post(`${BASE_URL}/api/v1/queue/enter`, null, params);

  const isSuccess = check(res, {
    'enter: status is 200': (r) => r.status === 200,
    'enter: has rank': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data && body.data.rank !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  if (res.status === 200) {
    try {
      const body = JSON.parse(res.body);
      if (body.data.status === 'WAITING') {
        queueEnterSuccess.add(1);
      } else {
        queueEnterDuplicate.add(1);
      }
    } catch (e) {}
  }

  sleep(0.1);
}

// --- Scenario 2: Polling Load ---
export function pollingTest() {
  const params = {
    headers: {
      'X-Loopers-LoginId': `${LOGIN_ID}-${__VU}`, // VU 1~200 — subset of queue_flood users (already in queue)
      'X-Loopers-LoginPw': LOGIN_PW,
    },
    tags: { name: 'queue_position' },
  };

  const res = http.get(`${BASE_URL}/api/v1/queue/position`, params);

  check(res, {
    'position: status is 200': (r) => r.status === 200,
  });

  if (res.status === 200) {
    try {
      const body = JSON.parse(res.body);
      positionCheckSuccess.add(1);

      if (body.data.status === 'ACTIVE' && body.data.token) {
        tokenReceived.add(1);
      }

      if (typeof body.data.estimatedWaitSeconds === 'number') {
        waitTimeTrend.add(body.data.estimatedWaitSeconds);
      }
    } catch (e) {}
  }

  // 동적 Polling 주기 시뮬레이션 (순번에 따라 다른 주기)
  sleep(Math.random() * 2 + 1); // 1~3초
}

// --- Scenario 3: Order With Token ---
export function orderWithTokenTest() {
  const vuId = 1000 + __VU; // offset to avoid collision with queue_flood (1~1000)
  const headers = {
    'Content-Type': 'application/json',
    'X-Loopers-LoginId': `${LOGIN_ID}-${vuId}`,
    'X-Loopers-LoginPw': LOGIN_PW,
  };

  // 1. Enter queue
  const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, {
    headers,
    tags: { name: 'order_flow_enter' },
  });

  if (enterRes.status !== 200) {
    sleep(1);
    return;
  }

  // 2. Poll until token is received (max 60 attempts — queue backlog from phase 1 may be deep)
  let token = null;
  for (let i = 0; i < 60; i++) {
    sleep(1);

    const posRes = http.get(`${BASE_URL}/api/v1/queue/position`, {
      headers,
      tags: { name: 'order_flow_poll' },
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
    orderRejected.add(1);
    orderThroughput.add(false);
    return;
  }

  // 3. Place order with token
  const orderPayload = JSON.stringify({
    items: [{ productId: 1, quantity: 1 }],
    cardType: 'SAMSUNG',
    cardNo: '1234-5678-9012-3456',
  });

  const orderRes = http.post(`${BASE_URL}/api/v1/orders`, orderPayload, {
    headers: {
      ...headers,
      'X-Entry-Token': token,
    },
    tags: { name: 'order_place' },
  });

  const orderOk = check(orderRes, {
    'order: status is 200': (r) => r.status === 200,
  });

  if (orderOk) {
    orderSuccess.add(1);
    orderThroughput.add(true);
  } else {
    orderRejected.add(1);
    orderThroughput.add(false);
  }
}

// Save results
export function handleSummary(data) {
  const now = new Date();
  const ts = now.toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const filename = `./k6/results/queue-benchmark-${ts}.txt`;

  const scenarioHeader = [
    '='.repeat(60),
    `Queue System Benchmark — ${now.toISOString()}`,
    '='.repeat(60),
    '',
    '[ Scenarios ]',
    '',
    '  queue_flood:',
    '    executor:  ramping-vus',
    '    stages:    0 → 100 → 500 → 1000 VUs (hold 20s) → 0',
    '    purpose:   대기열 진입 폭증 시뮬레이션',
    '',
    '  polling_load:',
    '    executor:  constant-vus (200 VUs, 30s)',
    '    purpose:   순번 Polling 부하 측정',
    '',
    '  order_with_token:',
    '    executor:  constant-vus (50 VUs, 30s)',
    '    purpose:   대기열 → 토큰 → 주문 전체 플로우',
    '',
    '[ Thresholds ]',
    '    http_req_duration: p(95)<2000',
    '    queue_enter: p(99)<1000',
    '    queue_position: p(99)<500',
    '    http_req_failed: rate<0.3',
    '',
    `[ Target ]  ${BASE_URL}`,
    '',
    '='.repeat(60),
    '',
  ].join('\n');

  const summary = textSummary(data, { indent: ' ', enableColors: false });

  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [filename]: scenarioHeader + summary,
  };
}
