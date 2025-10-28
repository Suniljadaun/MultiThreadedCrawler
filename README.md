# Real-Time Financial Intelligence Platform

An **educational, simulated** fintech platform with two parts:

1. **Order & portfolio backend:** Java, Spring Boot, PostgreSQL, Kafka and Redis. Users place simulated orders, which are validated, executed and applied to their portfolio through domain events.
2. **Financial research assistant:** Python, FastAPI and RAG. It answers questions about financial documents and cites the exact sources it used.

> This is a learning project. It does not execute real trades, connect to brokerage accounts, handle real money or give investment advice. All market and order data is synthetic.

## Project history

This repository began as a small multithreaded web-crawler experiment (see the earliest commits). It has since been repurposed and rebuilt as the platform described here.

## Status

| Phase | Scope | Status |
|---|---|---|
| 0 | Architecture & specification | DONE |
| 1 | Spring Boot core + PostgreSQL | DONE |
| 2 | Transactions & idempotency | DONE |
| 3 | Kafka events | DONE |
| 4 | Portfolio | DONE |
| 5 | Redis caching | DONE |
| 6 | AI / RAG service | DONE (offline baseline; LLM evaluation pending) |
| 7 | Observability | DONE |
| 8 | Performance | DONE (baseline + one optimization round, see docs/performance.md) |
| 9 | CI/CD & final docs | IN PROGRESS (CI + Docker images written, docs next) |

## Run locally

Requires Docker Desktop. For development also JDK 21, Maven and Python 3.11+.

Whole system in Docker (builds the backend and ai-service images from source):

```bash
docker compose --profile app up -d --build
docker compose exec ai-service python -m app.ingestion.cli /data/documents   # load the sample documents
bash scripts/ci/smoke-test.sh                                                 # optional end-to-end check (curl + jq)
```

Development (infrastructure in Docker, apps on the host):

```bash
docker compose up -d          # PostgreSQL, Kafka, Redis, Prometheus :9090, Grafana :3000
cd backend
mvn test                      # unit + web tests
mvn verify                    # + integration tests on real PostgreSQL/Kafka/Redis (needs Docker)
mvn spring-boot:run           # start API on http://localhost:8080
```

Research assistant on the host (Python 3.11+): see [ai-service/README.md](ai-service/README.md).
Do not run the `app` profile and the host apps at the same time; both use ports 8080 and 8000.

PostgreSQL is exposed on host port 5433. Endpoints are listed in [docs/api.md](docs/api.md).
Health and metrics: `/actuator/health`, `/actuator/prometheus` (backend), `/metrics` (ai-service). See [docs/observability.md](docs/observability.md).

## CI

`.github/workflows/ci.yml` runs on every push and pull request:

- backend: `mvn verify` (compile, unit tests, Testcontainers integration tests), jar uploaded as an artifact
- ai-service on Python 3.11 and 3.14: `ruff check`, fast tests, integration tests
- system: builds both Docker images, starts the whole stack and runs `scripts/ci/smoke-test.sh`
  (user -> order -> Kafka -> execution -> portfolio, then RAG ingest + query)

Images are built but not published, and nothing is deployed.

## Planned tech stack

| Area | Technology |
|---|---|
| Backend | Java 21, Spring Boot, Spring Data JPA, Maven |
| Database | PostgreSQL, Flyway migrations |
| Messaging | Apache Kafka |
| Caching | Redis |
| AI service | Python, FastAPI, embeddings, vector search, LLM provider abstraction |
| Testing | JUnit 5, Mockito, Testcontainers, pytest |
| DevOps | Docker Compose, GitHub Actions |
| Observability | Micrometer, Prometheus, Grafana, structured logs |

Pinned so far: Java 21, Spring Boot 4.1.1, PostgreSQL 17 + pgvector (`pgvector/pgvector:pg17`), Kafka 4.0.0, Redis 7.4,
Python 3.11+ (tested on 3.11 and 3.14), FastAPI 0.141.

## Planned repository layout

```text
backend/      Spring Boot backend
ai-service/   FastAPI RAG service
docs/         design docs and architecture decision records (ADRs)
infra/        Docker, Prometheus and Grafana config
scripts/      dev, seed and benchmark scripts
test-data/    synthetic market data and sample documents
```

## Design principles

- Correctness first, then clarity, observability, testability and performance.
- Every technology must solve a documented problem.
- No benchmark or metric is published unless it comes from a reproducible script.
- The research assistant never makes up citations. If the evidence is insufficient, it says so.
