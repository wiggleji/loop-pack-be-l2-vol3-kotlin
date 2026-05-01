import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

/**
 * 토큰 TTL 만료 테스트
 *
 * 검증 목표:
 *   1. 토큰 발급 후 TTL(5분) 이내에 주문하면 성공하는지
 *   2. 토큰 발급 후 TTL(5분) 초과 시 주문이 거부되는지
 *   3. 만료된 토큰으로 주문 시도 시 적절한 에러가 반환되는지
 *
 * 실행 방법:
 *   k6 run k6/queue-token-ttl-test.js
 *   k6 run -e TOKEN_TTL_SECONDS=300 k6/queue-token-ttl-test.js
 *
 * 주의: TTL 5분 테스트는 실행 시간이 ~6분 소요됩니다.
 *       빠른 검증을 원하면 서버의 TOKEN_TTL을 짧게 조정하세요.
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LOGIN_ID = __ENV.LOGIN_ID || 'testuser';
const LOGIN_PW = __ENV.LOGIN_PW || 'TestPass1!';
const TOKEN_TTL_SECONDS = parseInt(__ENV.TOKEN_TTL_SECONDS || '300');

// Custom metrics
const tokenValidBeforeTTL = new Counter('token_valid_before_ttl');
const tokenExpiredAfterTTL = new Counter('token_expired_after_ttl');
const tokenUnexpectedResult = new Counter('token_unexpected_result');

export const options = {
  scenarios: {
    // Phase 1: 토큰 발급 직후 주문 — 성공해야 함
    token_fresh: {
      executor: 'per-vu-iterations',
      vus: 5,
      iterations: 1,
      exec: 'tokenFreshTest',
    },

    // Phase 2: 토큰 만료 후 주문 — 거부되어야 함
    token_expired: {
      executor: 'per-vu-iterations',
      vus: 5,
      iterations: 1,
      startTime: '10s', // Phase 1 이후 시작
      exec: 'tokenExpiredTest',
    },
  },

  thresholds: {
    token_valid_before_ttl: ['count>=5'],    // 5명 모두 성공
    token_expired_after_ttl: ['count>=5'],   // 5명 모두 만료 확인
    token_unexpected_result: ['count==0'],    // 예상 밖 결과 0건
  },
};

function headers(vuId) {
  return {
    'Content-Type': 'application/json',
    'X-Loopers-LoginId': `${LOGIN_ID}-${vuId}`,
    'X-Loopers-LoginPw': LOGIN_PW,
  };
}

function enterAndWaitForToken(vuId, maxPollAttempts) {
  const hdrs = headers(vuId);

  // 1. 대기열 진입
  const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, {
    headers: hdrs,
    tags: { name: 'ttl_enter' },
  });

  if (enterRes.status !== 200) {
    console.error(`VU ${vuId}: enter failed - ${enterRes.status}`);
    return null;
  }

  // 2. Polling으로 토큰 수신 대기
  for (let i = 0; i < maxPollAttempts; i++) {
    sleep(1);

    const posRes = http.get(`${BASE_URL}/api/v1/queue/position`, {
      headers: hdrs,
      tags: { name: 'ttl_poll' },
    });

    if (posRes.status === 200) {
      try {
        const body = JSON.parse(posRes.body);
        if (body.data.status === 'ACTIVE' && body.data.token) {
          return body.data.token;
        }
      } catch (e) {}
    }
  }

  console.error(`VU ${vuId}: token not received after ${maxPollAttempts} polls`);
  return null;
}

// --- Phase 1: 토큰 발급 직후 주문 (TTL 이내) ---
export function tokenFreshTest() {
  const vuId = 1000 + __VU; // 토큰 만료 테스트 유저와 분리
  const token = enterAndWaitForToken(vuId, 30);

  if (!token) {
    tokenUnexpectedResult.add(1);
    return;
  }

  // 토큰 발급 직후 주문 시도
  const orderPayload = JSON.stringify({
    items: [{ productId: 1, quantity: 1 }],
    cardType: 'SAMSUNG',
    cardNo: '1234-5678-9012-3456',
  });

  const orderRes = http.post(`${BASE_URL}/api/v1/orders`, orderPayload, {
    headers: {
      ...headers(vuId),
      'X-Entry-Token': token,
    },
    tags: { name: 'ttl_order_fresh' },
  });

  const isSuccess = check(orderRes, {
    'fresh token: order accepted (200)': (r) => r.status === 200,
  });

  if (isSuccess) {
    tokenValidBeforeTTL.add(1);
    console.log(`VU ${vuId}: ✅ fresh token order succeeded`);
  } else {
    tokenUnexpectedResult.add(1);
    console.error(`VU ${vuId}: ❌ fresh token order failed - ${orderRes.status} ${orderRes.body}`);
  }
}

// --- Phase 2: 토큰 만료 후 주문 (TTL 초과) ---
export function tokenExpiredTest() {
  const vuId = 2000 + __VU;
  const token = enterAndWaitForToken(vuId, 30);

  if (!token) {
    tokenUnexpectedResult.add(1);
    return;
  }

  console.log(`VU ${vuId}: token received, waiting ${TOKEN_TTL_SECONDS}s for TTL expiry...`);

  // TTL + 여유 10초 대기
  sleep(TOKEN_TTL_SECONDS + 10);

  // 만료된 토큰으로 주문 시도
  const orderPayload = JSON.stringify({
    items: [{ productId: 1, quantity: 1 }],
    cardType: 'SAMSUNG',
    cardNo: '1234-5678-9012-3456',
  });

  const orderRes = http.post(`${BASE_URL}/api/v1/orders`, orderPayload, {
    headers: {
      ...headers(vuId),
      'X-Entry-Token': token,
    },
    tags: { name: 'ttl_order_expired' },
  });

  const isExpired = check(orderRes, {
    'expired token: order rejected (400)': (r) => r.status === 400,
    'expired token: error message mentions token': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.meta.message && body.meta.message.includes('토큰');
      } catch (e) {
        return false;
      }
    },
  });

  if (isExpired) {
    tokenExpiredAfterTTL.add(1);
    console.log(`VU ${vuId}: ✅ expired token correctly rejected`);
  } else {
    tokenUnexpectedResult.add(1);
    console.error(`VU ${vuId}: ❌ expired token was NOT rejected - ${orderRes.status} ${orderRes.body}`);
  }
}

export function handleSummary(data) {
  const now = new Date();
  const ts = now.toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const filename = `./k6/results/queue-token-ttl-${ts}.txt`;

  const header = [
    '='.repeat(60),
    `Queue Token TTL Test — ${now.toISOString()}`,
    '='.repeat(60),
    '',
    '[ Test Design ]',
    '',
    '  Phase 1 (token_fresh):',
    '    5 users enter queue, receive token, order immediately',
    '    Expected: all orders succeed (200)',
    '',
    '  Phase 2 (token_expired):',
    `    5 users enter queue, receive token, wait ${TOKEN_TTL_SECONDS}s + 10s`,
    '    Expected: all orders rejected (400)',
    '',
    '[ Thresholds ]',
    '    token_valid_before_ttl: count >= 5',
    '    token_expired_after_ttl: count >= 5',
    '    token_unexpected_result: count == 0',
    '',
    `[ Target ]  ${BASE_URL}`,
    `[ TTL ]     ${TOKEN_TTL_SECONDS}s`,
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
