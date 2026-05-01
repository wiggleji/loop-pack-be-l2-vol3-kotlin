import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

// Custom metrics
const paymentSuccess = new Counter('payment_success');
const paymentFail = new Counter('payment_fail');
const statusPending = new Counter('status_pending');
const statusSuccessResult = new Counter('status_success_result');
const statusFailedResult = new Counter('status_failed_result');
const statusCheckDuration = new Trend('status_check_duration');

const PG_BASE = 'http://localhost:8082';
const CALLBACK_URL = 'http://localhost:8080/api/v1/callback';

// --- Scenario 1: Payment Request Throughput ---
// Tests how many concurrent payment requests the PG can handle
export const options = {
  scenarios: {
    // Phase 1: Payment request load test
    payment_request: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '10s', target: 10 },   // ramp up to 10
        { duration: '20s', target: 10 },   // hold 10
        { duration: '10s', target: 30 },   // ramp up to 30
        { duration: '20s', target: 30 },   // hold 30
        { duration: '10s', target: 50 },   // ramp up to 50
        { duration: '20s', target: 50 },   // hold 50
        { duration: '10s', target: 0 },    // ramp down
      ],
      exec: 'paymentRequest',
    },
    // Phase 2: Status check load test (starts after payment phase)
    status_check: {
      executor: 'constant-vus',
      vus: 10,
      duration: '30s',
      startTime: '105s',  // starts after payment phase
      exec: 'statusCheck',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<3000'],  // 95% of requests under 3s
    http_req_failed: ['rate<0.5'],       // less than 50% failed (PG has 60% success rate)
  },
};

// Payment Request Test
export function paymentRequest() {
  const orderId = `bench-${__VU}-${__ITER}-${Date.now()}`;
  const cardTypes = ['SAMSUNG', 'KB', 'HYUNDAI'];
  const cardType = cardTypes[Math.floor(Math.random() * cardTypes.length)];

  const payload = JSON.stringify({
    orderId: orderId,
    cardType: cardType,
    cardNo: '1234-5678-9814-1451',
    amount: 5000 + Math.floor(Math.random() * 45000), // 5000 ~ 50000
    callbackUrl: CALLBACK_URL,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-USER-ID': `user-${__VU}`,
    },
    tags: { name: 'POST_payment' },
  };

  const res = http.post(`${PG_BASE}/api/v1/payments`, payload, params);

  const success = check(res, {
    'status is 200': (r) => r.status === 200,
    'has transactionKey': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data && body.data.transactionKey;
      } catch (e) {
        return false;
      }
    },
  });

  if (res.status === 200) {
    try {
      const body = JSON.parse(res.body);
      if (body.meta.result === 'SUCCESS') {
        paymentSuccess.add(1);
      } else {
        paymentFail.add(1);
      }
    } catch (e) {
      paymentFail.add(1);
    }
  } else {
    paymentFail.add(1);
  }

  sleep(0.1); // 100ms between requests per VU
}

// Status Check Test — uses pre-created transaction keys
export function statusCheck() {
  // First create a payment to get a transactionKey
  const orderId = `status-${__VU}-${__ITER}-${Date.now()}`;
  const createRes = http.post(
    `${PG_BASE}/api/v1/payments`,
    JSON.stringify({
      orderId: orderId,
      cardType: 'SAMSUNG',
      cardNo: '1234-5678-9814-1451',
      amount: 10000,
      callbackUrl: CALLBACK_URL,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-USER-ID': `user-${__VU}`,
      },
      tags: { name: 'POST_payment_for_status' },
    }
  );

  if (createRes.status !== 200) {
    sleep(1);
    return;
  }

  let txKey;
  try {
    txKey = JSON.parse(createRes.body).data.transactionKey;
  } catch (e) {
    sleep(1);
    return;
  }

  // Poll status (simulates real-world polling pattern)
  // PG processing delay: 1~5s
  sleep(2);

  const start = Date.now();
  const statusRes = http.get(
    `${PG_BASE}/api/v1/payments/${txKey}?userId=user-${__VU}`,
    {
      headers: { 'X-USER-ID': `user-${__VU}` },
      tags: { name: 'GET_payment_status' },
    }
  );
  statusCheckDuration.add(Date.now() - start);

  check(statusRes, {
    'status check 200': (r) => r.status === 200,
  });

  if (statusRes.status === 200) {
    try {
      const body = JSON.parse(statusRes.body);
      const status = body.data && body.data.status;
      if (status === 'PENDING') statusPending.add(1);
      else if (status === 'SUCCESS') statusSuccessResult.add(1);
      else if (status === 'FAILED') statusFailedResult.add(1);
    } catch (e) {}
  }

  sleep(0.5);
}

// Save results to file with timestamp
export function handleSummary(data) {
  const now = new Date();
  const ts = now.toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const filename = `./k6/results/pg-benchmark-${ts}.txt`;

  const scenarioHeader = [
    '='.repeat(60),
    `PG Simulator Benchmark — ${now.toISOString()}`,
    '='.repeat(60),
    '',
    '[ Scenarios ]',
    '',
    '  payment_request:',
    '    executor:  ramping-vus',
    '    startVUs:  1',
    '    stages:',
    '      - 10s → 10 VUs',
    '      - 20s → 10 VUs (hold)',
    '      - 10s → 30 VUs',
    '      - 20s → 30 VUs (hold)',
    '      - 10s → 50 VUs',
    '      - 20s → 50 VUs (hold)',
    '      - 10s → 0 VUs (ramp down)',
    '',
    '  status_check:',
    '    executor:  constant-vus',
    '    vus:       10',
    '    duration:  30s',
    '    startTime: 105s',
    '',
    '[ Thresholds ]',
    '    http_req_duration: p(95)<3000',
    '    http_req_failed: rate<0.5',
    '',
    `[ Target ]  ${PG_BASE}`,
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
