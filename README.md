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
| 1 | Spring Boot core + PostgreSQL | IN PROGRESS (users API done) |
| 2 | Transactions & idempotency | NOT STARTED |
| 3 | Kafka events | NOT STARTED |
| 4 | Portfolio | NOT STARTED |
| 5 | Redis caching | NOT STARTED |
| 6 | AI / RAG service | NOT STARTED |
| 7 | Observability | NOT STARTED |
| 8 | Performance | NOT STARTED |
| 9 | CI/CD & final docs | NOT STARTED |

## Run locally

Requires JDK 21, Maven and Docker Desktop.

```bash
docker compose up -d          # start PostgreSQL
cd backend
mvn test                      # run tests
mvn spring-boot:run           # start API on http://localhost:8080
```

Available so far: `GET /api/v1/health`, `POST /api/v1/users`, `GET /api/v1/users/{id}`, `PATCH /api/v1/users/{id}`.

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

Exact versions will be pinned and recorded when each component is added.

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
