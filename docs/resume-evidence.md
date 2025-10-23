# Resume evidence

Only measured facts. Every number links to the command, dataset, environment and result file it came from.
All runs: 2026-09-25, one Windows laptop (apps on the host, PostgreSQL/Kafka/Redis in Docker Desktop),
synthetic data. Machine details: `environment.txt` inside each run folder.
These are single-machine numbers, comparable between runs, not production capacity.

## Technology

Java 21, Spring Boot 4, PostgreSQL 17 + pgvector, Kafka 4 (KRaft), Redis 7.4, Flyway, Testcontainers,
Python 3.14, FastAPI, RAG, Micrometer, Prometheus, Grafana, k6, Docker Compose, GitHub Actions.

## Measured

| Claim | Number | Command | Dataset | Result file |
|---|---|---|---|---|
| Portfolio read throughput with Redis cache | 4,763.3 req/s, p95 5.66 ms (cache off: 879.6 req/s, p95 32.06 ms) | `run.ps1 -Test portfolio -Label cache-on` / `cache-off` | 20k users, 100k positions, 1,000 hot users, 20 VUs, 60 s | `benchmark-results/portfolio-cache-on-20260925-090346/`, `portfolio-cache-off-20260925-100933/` |
| Cache hit rate | 99.6% | same run | same | `portfolio-cache-on-20260925-090346/prometheus.txt` |
| Order API under load | 460.8 req/s, p95 65.57 ms, 0% errors, 10% retried requests, 0 duplicate orders | `run.ps1 -Test orders -Label optimized` | 20 VUs, 60 s | `benchmark-results/orders-optimized-20260925-102217/` |
| Order pipeline throughput (outbox → Kafka → execution) | 4.0 → 55.1 executed orders/s (13.8x) after async batch outbox relay | `run.ps1 -Test orders` before/after | 20 VUs, 60 s, ~25k orders | `orders-baseline-*/`, `order-pipeline-final.txt`, `orders-optimized-20260925-102217/order-pipeline.txt` |
| Price update (cache invalidation of 20k holders) | p50 23,903 ms → 18.69 ms after batched Redis DEL + index | `run.ps1 -Test price-update` before/after | 20k holders of one symbol | `price-update-baseline-clean-20260925-093025/`, `price-update-optimized-20260925-102109/` |
| Holder lookup query | Seq Scan 22.2 ms → Index Only Scan 6.3 ms; 205.9 → 540.6 tps | `db-holders.ps1` before/after | 100k positions | `db-holders-baseline-20260925-085548/`, `db-holders-optimized-20260925-102036/` |
| RAG query latency | 240.2 req/s, p50 20.32 ms, p95 27.59 ms | `run.ps1 -Test rag -Label baseline-clean` | 3 synthetic documents, offline providers, 5 VUs | `benchmark-results/rag-baseline-clean-20260925-093143/` |
| Retrieval quality | Recall@5 = 1.0, MRR = 0.9 (precision@5 = 0.2, max possible 0.2) | `python -m app.evaluation.run` | 17 hand-labelled questions (13 answerable) | docs/ai-evaluation.md, locked in `ai-service/tests/test_evaluation.py` |
| Answer safety | abstained on 2/3 unanswerable, declined 1/1 advice request, 10/13 answers contained all expected facts | same | same | same |
| Tests | 116 unit + 22 integration (backend), 76 + 5 (ai-service), all green in CI | `mvn verify`, `pytest`, `pytest -m integration` | - | GitHub Actions run on commit `b3155c1` |

Details, limits and the discarded runs: docs/performance.md.

## Built (verifiable in the code)

- Transactional outbox with at-least-once delivery and idempotent consumers (`processed_events`); duplicate and
  malformed events tested with real Kafka.
- Idempotent order API: concurrent requests with one `Idempotency-Key` create exactly one order (tested).
- Cache-aside with after-commit eviction; API keeps working with Redis down (tested).
- RAG with citations checked against retrieved sources; answers with invented numbers are rejected.
- Request id carried from HTTP through Kafka to both services' logs; Prometheus metrics and a Grafana dashboard.
- CI builds Docker images and runs an end-to-end smoke test of the whole stack.

## Suggested resume lines (each backed by a row above)

- Built an event-driven order platform (Spring Boot, PostgreSQL, Kafka, Redis) with a transactional outbox and
  idempotent consumers; tested duplicate and malformed events against real Kafka with Testcontainers.
- Profiled the order pipeline with k6 and Prometheus, found a synchronous outbox relay limiting it to 4 orders/s,
  and raised it to 55 orders/s (13.8x) with batched async publishing.
- Cut a 20k-user cache invalidation from 23.9 s to 18.7 ms (p50) with batched Redis deletes and a covering index
  (query 22.2 ms to 6.3 ms).
- Added Redis cache-aside for portfolio reads: 4,763 vs 880 req/s and p95 5.7 vs 32.1 ms in a local benchmark.
- Built a FastAPI RAG service on pgvector with citation checking; Recall@5 1.0 and MRR 0.9 on a 17-question
  hand-labelled set.
- Set up GitHub Actions CI: 219 automated tests plus a Docker Compose end-to-end smoke test.

Wording rules: say "local benchmark" and "synthetic data"; do not present these as production traffic.
