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
| 8 | Performance | IN PROGRESS (baseline measured, optimizations under test) |
| 9 | CI/CD & final docs | NOT STARTED |

## Run locally

Requires JDK 21, Maven and Docker Desktop.

```bash
docker compose up -d          # PostgreSQL, Kafka, Redis, Prometheus :9090, Grafana :3000
cd backend
mvn test                      # unit + web tests
mvn verify                    # + integration tests on real PostgreSQL (needs Docker)
mvn spring-boot:run           # start API on http://localhost:8080
```

Research assistant (Python 3.11+): see [ai-service/README.md](ai-service/README.md).

PostgreSQL is exposed on host port 5433. Endpoints are listed in [docs/api.md](docs/api.md).
Health and metrics: `/actuator/health`, `/actuator/prometheus` (backend), `/metrics` (ai-service). See [docs/observability.md](docs/observability.md).

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
