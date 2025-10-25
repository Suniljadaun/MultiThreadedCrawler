// POST /research/query on the ai-service with the evaluation questions (retrieval + answer, no LLM by default).
import http from 'k6/http';
import { check } from 'k6';
import { intEnv, mainPhaseSummary, phasedOptions } from './summary.js';

const BASE = __ENV.AI_URL || 'http://localhost:8000';
const WARMUP = intEnv('WARMUP_SECONDS', 10);
const MAIN = intEnv('MAIN_SECONDS', 60);
const QUESTIONS = JSON.parse(open('../../../test-data/eval/research-eval.json')).examples.map((e) => e.question);

export const options = phasedOptions(intEnv('VUS', 5), WARMUP, MAIN);

export default function () {
  const question = QUESTIONS[Math.floor(Math.random() * QUESTIONS.length)];
  const res = http.post(`${BASE}/api/v1/research/query`, JSON.stringify({ question }), {
    headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /research/query' },
  });
  check(res, { 'status 200': (r) => r.status === 200 });
}

export function handleSummary(data) {
  return mainPhaseSummary(data, MAIN);
}
