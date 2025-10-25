// PUT /market-prices/ACME: each change evicts the cached portfolio of every ACME holder (20,000 seeded users).
import http from 'k6/http';
import { check } from 'k6';
import { intEnv, mainPhaseSummary, phasedOptions } from './summary.js';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const WARMUP = intEnv('WARMUP_SECONDS', 5);
const MAIN = intEnv('MAIN_SECONDS', 60);

export const options = phasedOptions(intEnv('VUS', 1), WARMUP, MAIN);

export default function () {
  // Alternate between two prices so every call is a real change
  const price = __ITER % 2 === 0 ? 101 : 99;
  const res = http.put(`${BASE}/api/v1/market-prices/ACME`, JSON.stringify({ price }), {
    headers: { 'Content-Type': 'application/json' }, tags: { name: 'PUT /market-prices/{symbol}' },
  });
  check(res, { 'status 200': (r) => r.status === 200 });
}

export function handleSummary(data) {
  return mainPhaseSummary(data, MAIN);
}
