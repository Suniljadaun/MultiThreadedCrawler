// GET /portfolio/{userId} for a hot set of seeded users. Run once with the cache on, once with it off.
import http from 'k6/http';
import { check } from 'k6';
import { intEnv, mainPhaseSummary, phasedOptions } from './summary.js';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const USER_MIN = intEnv('USER_MIN', 1);
const USERS = intEnv('USERS', 1000);
const WARMUP = intEnv('WARMUP_SECONDS', 15);
const MAIN = intEnv('MAIN_SECONDS', 60);

export const options = phasedOptions(intEnv('VUS', 20), WARMUP, MAIN);

export default function () {
  const userId = USER_MIN + Math.floor(Math.random() * USERS);
  const res = http.get(`${BASE}/api/v1/portfolio/${userId}`, { tags: { name: 'GET /portfolio/{userId}' } });
  check(res, { 'status 200': (r) => r.status === 200 });
}

export function handleSummary(data) {
  return mainPhaseSummary(data, MAIN);
}
