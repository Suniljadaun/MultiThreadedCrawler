# Testing

## Layers

| Layer | Backend (JUnit 5, Mockito) | ai-service (pytest) | Runs |
|---|---|---|---|
| Unit + web slice | 116 tests: services, state machine, relay, cache, controllers (MockMvc), request id | 76 fast tests: cleaning, chunking, embeddings, guards, answer checks, API (TestClient), metrics | `mvn test`, `pytest` |
| Integration | 22 tests on Testcontainers: PostgreSQL (pgvector image), Kafka, Redis | 5 tests on Testcontainers PostgreSQL + pgvector | `mvn verify`, `pytest -m integration` |
| End to end | `scripts/ci/smoke-test.sh` against the whole stack in Docker | same script (ingest + query) | CI `system` job |
| Evaluation | - | retrieval/answer metrics locked in `test_evaluation.py` (docs/ai-evaluation.md) | `pytest` |
| Performance | k6 + pgbench scripts, not pass/fail (docs/performance.md) | k6 RAG script | by hand |

Counts are from the CI run on commit `b3155c1` (2026-09-25).

## Integration tests

| Test | Covers |
|---|---|
| `KafkaOrderFlowIT` | order through real Kafka: executed, rejected (symbol, limit, no shares), sell after buy, duplicate event, malformed message to DLT |
| `OrderIdempotencyIT` | concurrent requests with one key create one order and one event; key reused with other body; keys per user |
| `OrderLifecycleIT` | cancel, cancel after execution, optimistic lock |
| `PortfolioCacheIT` | cache hit, TTL, evict after commit, no evict on rollback, price change evicts holders |
| `PortfolioCacheRedisDownIT` | portfolio works with Redis down |
| `UserRepositoryIT` | generated ids and timestamps, unique email in the DB |
| `test_pgvector_store_it.py` | ingest/search round trip, same ranking as the in-memory store, re-ingest, concurrent ingest, dimension mismatch |

Every test class uses its own containers and cleans its rows, so tests do not depend on order.

## Failure tests (plan section 20)

| Failure | Test |
|---|---|
| Duplicate Kafka message | `KafkaOrderFlowIT.duplicateEventIsProcessedOnlyOnce` |
| Malformed event | `KafkaOrderFlowIT.malformedMessageGoesToDeadLetterTopic`, `EventParserTest` |
| Redis unavailable | `PortfolioCacheRedisDownIT`, `PortfolioCacheTest` |
| LLM timeout | `test_openai_llm.py::test_timeout_becomes_llm_error`, `test_api.py::test_llm_failure_is_503` |
| Kafka unavailable at publish | `OutboxRelayTest` (mocked sends: failure, exception, timeout) |
| DB unavailable | not covered (docs/reliability.md, known gaps) |
| Kafka consumer restart | not covered directly; restart replays are duplicates, which are covered |

## How to run

```bash
cd backend && mvn verify                        # unit + integration, needs Docker
cd ai-service && pytest && pytest -m integration
docker compose --profile app up -d --build && bash scripts/ci/smoke-test.sh
```

CI (`.github/workflows/ci.yml`) runs all of the above on every push and pull request, plus `ruff check`
on Python 3.11 and 3.14.

## Rules followed

- Integration tests use real PostgreSQL, Kafka and Redis in containers, not in-memory substitutes.
- Kafka tests wait for an order state with a timeout instead of fixed sleeps.
- Benchmark numbers are never asserted in tests; they are measured and reported separately.
