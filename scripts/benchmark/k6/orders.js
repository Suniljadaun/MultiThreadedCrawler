// POST /orders from many users at once. 10% of requests repeat an earlier key (a client retry):
// they must return 200 with the original order, never create a second one.
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { intEnv, mainPhaseSummary, phasedOptions } from './summary.js';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const USER_MIN = intEnv('USER_MIN', 1);
const USERS = intEnv('USERS', 20000);
const MAIN = intEnv('MAIN_SECONDS', 60);
const RUN_ID = __ENV.RUN_ID || 'manual';
const SYMBOLS = ['ACME', 'GLOBEX', 'INITECH', 'UMBRELLA', 'STARK'];

const ordersCreated = new Counter('orders_created');
const retriesReplayed = new Counter('retries_replayed');

// No warm-up: every order counts, so the database check afterwards matches k6's numbers
export const options = phasedOptions(intEnv('VUS', 20), 0, MAIN);

let last = null; // per VU: the previous request, to replay as a retry

function send(key, body) {
  return http.post(`${BASE}/api/v1/orders`, body, {
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key.key, 'X-Request-ID': key.key },
    tags: { name: 'POST /orders' },
  });
}

export default function () {
  if (last && Math.random() < 0.1) {
    const res = send(last, last.body);
    if (check(res, { 'retry returns 200': (r) => r.status === 200 })) retriesReplayed.add(1);
    return;
  }
  const userId = USER_MIN + Math.floor(Math.random() * USERS);
  const body = JSON.stringify({
    userId, symbol: SYMBOLS[Math.floor(Math.random() * SYMBOLS.length)], side: 'BUY', quantity: 1, price: 1000,
  });
  const key = { key: `bench-${RUN_ID}-${__VU}-${__ITER}`, body };
  const res = send(key, body);
  if (check(res, { 'new order returns 201': (r) => r.status === 201 })) ordersCreated.add(1);
  last = key;
}

export function handleSummary(data) {
  return mainPhaseSummary(data, MAIN);
}
