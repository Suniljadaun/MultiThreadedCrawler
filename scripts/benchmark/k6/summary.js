// Shared summary: only the "main" phase counts (warm-up is excluded), written as JSON + short text.
export function mainPhaseSummary(data, mainSeconds) {
  const m = data.metrics;
  const d = m['http_req_duration{phase:main}'];
  const reqs = m['http_reqs{phase:main}'];
  const failed = m['http_req_failed{phase:main}'];
  const result = {
    requests: reqs ? reqs.values.count : 0,
    rps: reqs ? +(reqs.values.count / mainSeconds).toFixed(1) : 0,
    errorRate: failed ? +failed.values.rate.toFixed(4) : 0,
    latencyMs: d ? {
      p50: +d.values['p(50)'].toFixed(2),
      p95: +d.values['p(95)'].toFixed(2),
      p99: +d.values['p(99)'].toFixed(2),
      max: +d.values.max.toFixed(2),
    } : null,
    checksFailed: m.checks ? m.checks.values.fails : 0,
  };
  for (const [name, metric] of Object.entries(m)) {
    if (metric.type === 'counter' && !name.includes('{') && !name.startsWith('http_') && !name.startsWith('data_')
        && !name.startsWith('iterations')) {
      result[name] = metric.values.count;
    }
  }
  const text = `\nmain phase (${mainSeconds}s): ${result.requests} requests, ${result.rps} req/s, `
    + `errors ${(result.errorRate * 100).toFixed(2)}%\n`
    + (result.latencyMs ? `latency ms: p50 ${result.latencyMs.p50}  p95 ${result.latencyMs.p95}  `
      + `p99 ${result.latencyMs.p99}  max ${result.latencyMs.max}\n` : '');
  const out = { stdout: text };
  if (__ENV.SUMMARY_FILE) {
    out[__ENV.SUMMARY_FILE] = JSON.stringify({ summary: result, raw: data }, null, 2);
  }
  return out;
}

// Scenario pair: warm-up (not measured) then main. Submetric thresholds make k6 report the main phase alone.
export function phasedOptions(vus, warmupSeconds, mainSeconds) {
  const scenarios = {
    main: { executor: 'constant-vus', vus, duration: `${mainSeconds}s`, startTime: `${warmupSeconds}s`,
            tags: { phase: 'main' } },
  };
  if (warmupSeconds > 0) {
    scenarios.warmup = { executor: 'constant-vus', vus, duration: `${warmupSeconds}s`, tags: { phase: 'warmup' } };
  }
  return {
    scenarios,
    summaryTrendStats: ['avg', 'min', 'p(50)', 'p(95)', 'p(99)', 'max'],
    thresholds: {
      'http_req_duration{phase:main}': ['max>=0'],
      'http_reqs{phase:main}': ['count>=0'],
      'http_req_failed{phase:main}': ['rate>=0'],
    },
  };
}

export function intEnv(name, fallback) {
  return __ENV[name] ? parseInt(__ENV[name], 10) : fallback;
}
