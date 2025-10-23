# Performance

Rule: every number here comes from a script in `scripts/benchmark/`, run on the machine described in its report.
Raw output of every run is kept in `benchmark-results/<run id>/`. No number is estimated or copied from elsewhere.

## Tool choice: k6

- One binary (`winget install k6`), tests are small JavaScript files kept in the repo.
- Built-in p50/p95/p99, error rate and throughput; results exported as JSON.
- Gatling needs a JVM build setup, JMeter uses XML test plans that are hard to review in a diff.

## What is measured

| Test | Script | Question it answers |
|---|---|---|
| Portfolio read, cache on vs off | `k6/portfolio.js` | Does the Redis cache (ADR-004) lower latency and raise throughput? |
| Order placement | `k6/orders.js` | API latency under concurrent orders; 10% retries must replay, never duplicate |
| Order pipeline | `sql/order-latency.sql` | Time from HTTP insert to execution through outbox and Kafka; executed orders per second |
| Price update | `k6/price-update.js` | Cost of evicting every holder's cached portfolio on a price change |
| Holder lookup (DB) | `db-holders.ps1` | The query behind the price update: plan and throughput before/after an index |
| RAG query | `k6/rag.js` | ai-service retrieval + answer latency (offline providers) |

Each run also stores `environment.txt` (machine, versions, git commit), `parameters.txt`, the k6 JSON summary
and `prometheus.txt` (backend CPU, heap, DB pool, cache hit ratio, Kafka lag, outbox backlog during the run).

## Setup (once)

```powershell
docker compose up -d
Get-Content scripts\benchmark\sql\seed.sql | docker exec -i finintel-postgres psql -U finintel -d finintel
```

The seed adds 20,000 users (`bench-N@bench.test`) holding all five symbols (100,000 positions).
`sql/cleanup.sql` removes them and everything the benchmarks created.

Start the backend with per-request INFO logs switched off, so console output does not limit throughput.
Use the same setting for every run you compare:

```powershell
cd backend
$env:LOGGING_LEVEL_COM_SUNIL_FININTEL = "WARN"
mvn spring-boot:run
```

The ai-service is started as usual (`uvicorn app.main:app --host 0.0.0.0 --port 8000`).

## Runs

```powershell
.\scripts\benchmark\run.ps1 -Test portfolio -Label cache-on
# restart the backend with the cache disabled, then:
#   $env:APP_CACHE_PORTFOLIO_ENABLED = "false"; mvn spring-boot:run
.\scripts\benchmark\run.ps1 -Test portfolio -Label cache-off
# restart normally (cache on) for the rest
.\scripts\benchmark\run.ps1 -Test orders -Label baseline
.\scripts\benchmark\run.ps1 -Test price-update -Label baseline
.\scripts\benchmark\db-holders.ps1 -Label baseline
.\scripts\benchmark\run.ps1 -Test rag -Label baseline
```

Defaults: portfolio 20 VUs over 1,000 hot users (15 s warm-up, 60 s measured); orders 20 VUs, 60 s, no warm-up;
price update 1 VU; RAG 5 VUs. Only the measured phase is reported. Close other heavy programs while running.

## Reports

Filled in only from real runs, using the template below.

Machine and versions for every run: `environment.txt` in the run's folder. Baseline runs: 2026-09-25,
git commit of the Phase 8 benchmark kit, backend at `LOGGING_LEVEL_COM_SUNIL_FININTEL=WARN`.

Between runs: stop the backend, run `sql/cleanup.sql`, re-run `sql/seed.sql`, start the backend again.
The orders test leaves a large outbox backlog behind (see below); a test started on top of it measures the
backlog, not the endpoint. Two early price-update runs (p50 25.4 s and 29.0 s) and two RAG runs (175.7 and
157.4 req/s) were made with a 29-31k event backlog and are not used.

### Portfolio read: cache on vs off

20 VUs, 1,000 hot users, 15 s warm-up, 60 s measured.

| Metric | Cache on | Cache off |
|---|---:|---:|
| P50 | 3.73 ms | 21.6 ms |
| P95 | 5.66 ms | 32.06 ms |
| P99 | 9.36 ms | 40.11 ms |
| Max | 47.88 ms | 87.8 ms |
| Throughput | 4,763.3 req/s | 879.6 req/s |
| Error rate | 0% | 0% |
| Cache hit ratio | 0.996 | no lookups (cache disabled) |
| Backend CPU (max) | 0.418 | 0.315 |
| Machine CPU (max) | 0.862 | 0.785 |
| DB pool active / waiting threads (max) | 9 / - | 10 / 10 |

Interpretation: with the cache on, p50 is 5.8x lower and throughput 5.4x higher. Without it every request needs a
database connection; the pool (10) is full with 10 threads waiting, so the pool, not CPU, limits the cache-off
run. The cache-on number is for a hot set of 1,000 users with a 60 s TTL and no writes during the test, the best
case for a cache; a workload with frequent fills or price changes would see a lower hit ratio.

Raw: `benchmark-results/portfolio-cache-on-20260925-090346/`, `benchmark-results/portfolio-cache-off-20260925-100933/`.

### Order placement and pipeline

Baseline, 20 VUs, 60 s, 10% of requests are retries with the same idempotency key.

| Metric | Baseline | After |
|---|---:|---:|
| Requests | 27,746 | pending |
| Throughput | 462.4 req/s | pending |
| P50 | 38.4 ms | pending |
| P95 | 74.85 ms | pending |
| P99 | 91.25 ms | pending |
| Max | 413 ms | pending |
| Error rate | 0% | pending |
| DB pool active / waiting threads (max) | 10 / 12 | pending |
| Outbox pending (max) | 24,911 | pending |

Idempotency held: 24,972 orders created, exactly 24,972 `OrderCreated` events.

Pipeline (`order-pipeline-final.txt`, queried about 9 minutes after the load ended; the 3-minute drain wait
in `run.ps1` was not enough). At that point 2,664 orders were CREATED, 20,186 VALIDATED and 2,122 EXECUTED.
Latency is over executed orders only, so it understates the real delay of the orders still waiting:

| Metric | Baseline | After |
|---|---:|---:|
| Executed orders | 2,123 | pending |
| Executed per second | 4.0 | pending |
| Created -> executed P50 | 285,151 ms | pending |
| Created -> executed P95 | 501,312 ms | pending |
| Created -> executed P99 | 520,374 ms | pending |

Interpretation: the API accepts orders far faster than the pipeline moves them. The outbox relay sends one
event at a time and waits for each Kafka ack, 100 events per 500 ms run, so it publishes roughly 90-100
events/s while each order needs 4 events. The backlog is FIFO, so `OrderValidated` events wait behind thousands
of others; the execution consumer is idle most of the time. The DB pool (10 connections) is fully used with
threads waiting, a second limit that is left unchanged in this round so each change can be attributed.

Raw: `benchmark-results/orders-baseline-*/`, `order-pipeline-final.txt`.

### Price update and holder lookup

Price update, 1 VU alternating ACME 101/99, 60 s, empty outbox.

| Metric | Baseline | After |
|---|---:|---:|
| Requests in 60 s | 3 | pending |
| P50 | 23,903 ms | pending |
| P95 | 23,918 ms | pending |
| Max | 23,919 ms | pending |
| Backend CPU (max) | 0.036 | pending |

Holder lookup (`db-holders.ps1`: EXPLAIN ANALYZE + pgbench, 4 clients, 30 s):

| Metric | Baseline | After |
|---|---:|---:|
| Plan | Seq Scan on positions (80,004 rows removed by filter) | pending |
| Execution time (EXPLAIN) | 22.246 ms | pending |
| pgbench latency avg | 19.427 ms | pending |
| pgbench throughput | 205.9 tps | pending |

Interpretation: the holder query costs about 20 ms, so it is not what makes a price update take 24 s. With CPU
near idle, the time goes to waiting: one Redis DEL round trip per holder, about 20,000 of them.

Raw: `benchmark-results/price-update-baseline-clean-20260925-093025/`,
`benchmark-results/db-holders-baseline-20260925-085548/`.

### RAG query

5 VUs, 10 s warm-up, 60 s measured, hash embeddings + extractive answers (no LLM).

| Metric | Baseline |
|---|---:|
| P50 | 20.32 ms |
| P95 | 27.59 ms |
| P99 | 36.55 ms |
| Max | 81.74 ms |
| Throughput | 240.2 req/s |
| Error rate | 0% |

No optimization planned for RAG in this phase. Raw: `benchmark-results/rag-baseline-clean-20260925-093143/`.

### Changes under test (step B)

1. Outbox relay sends a whole batch, then checks the acks in order (stops at the first failure, later events
   are sent again: at-least-once, consumers deduplicate). The scheduler repeats full batches up to
   `app.outbox.publisher.max-rounds` (20) per run instead of one batch per 500 ms. Batch size stays 100.
2. Price update evicts holders with one DEL per 1,000 keys instead of one per user.
3. `V5__index_positions_symbol.sql`: index `positions (symbol, user_id) INCLUDE (quantity)` for the holder lookup.
4. Not changed: DB pool size, Kafka settings, batch size.

## Report template

```markdown
# Benchmark Report

## Environment
- OS / CPU / RAM / Java / Python / Database / Docker: (from environment.txt)
- Dataset: 20,000 seeded users, 100,000 positions

## Workload
## Methodology
## Baseline
## Optimized

## Results
| Metric | Before | After |
|---|---:|---:|
| P50 | | |
| P95 | | |
| P99 | | |
| Throughput | | |
| Error rate | | |

## Interpretation
## Reproduction Command
## Raw Result Location
## Limitations
```

## Known limitations

- k6, the apps and Docker share one laptop, so they compete for CPU; numbers are comparable between runs on the
  same machine, not absolute capacity figures.
- The apps run outside Docker; Postgres, Kafka and Redis run in Docker Desktop's VM.
- Database CPU is not measured directly; DB pool usage and repository timings are used instead.
