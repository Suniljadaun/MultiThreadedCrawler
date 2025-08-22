# Real-Time Financial Intelligence Platform

## Implementation Plan v1.0

> **Purpose:** This document is the source of truth for implementing the
> project.\
> **Target:** A production-style, interview-defensible fintech backend +
> AI/RAG system suitable for an M.Tech SDE/AI resume.\
> **Important:** This is an educational/simulated financial system. It
> must not execute real-money trades or present investment advice.

------------------------------------------------------------------------

# 1. Project Objective

Build a complete, production-oriented **Real-Time Financial Intelligence
Platform** that demonstrates:

-   Java and Spring Boot backend engineering
-   REST API design
-   relational database design and SQL optimization
-   Kafka-based event-driven architecture
-   Redis/Caffeine caching
-   transaction management and concurrency
-   idempotency and failure handling
-   observability
-   automated testing
-   Docker-based local deployment
-   CI/CD
-   Python/FastAPI AI service
-   embeddings and vector retrieval
-   RAG
-   LLM integration
-   evaluation of retrieval quality
-   integration between a JVM backend and AI service
-   system-design tradeoffs

The project should be deep enough that the developer can explain every
major architectural decision in an SDE interview.

------------------------------------------------------------------------

# 2. Core Product

The system simulates a fintech platform with two major capabilities.

## A. Real-Time Portfolio/Order Platform

Users can:

1.  create accounts
2.  create simulated orders
3.  validate orders
4.  execute simulated orders
5.  maintain positions
6.  calculate portfolio state
7.  retrieve portfolio information
8.  inspect transaction/order history

The system uses Kafka to propagate domain events.

## B. Financial Intelligence / Research Assistant

Users can ask questions about supported financial documents.

Example:

> "What were the major revenue drivers mentioned in the company's latest
> annual report?"

The AI pipeline should:

1.  accept a question
2.  retrieve relevant document chunks
3.  rank/filter retrieved context
4.  generate an answer using an LLM
5.  provide source/citation references
6.  expose retrieval/evaluation metadata where appropriate
7.  clearly distinguish retrieved facts from model-generated
    explanations

The AI service must not give personalized investment recommendations.

------------------------------------------------------------------------

# 3. Architectural Principle

Start with a **modular production-style architecture**, not an
unnecessarily large microservice zoo.

Recommended logical components:

``` text
                           ┌───────────────────────┐
                           │      Client/API       │
                           └───────────┬───────────┘
                                       │
                                       ▼
                           ┌───────────────────────┐
                           │ API Gateway / BFF      │
                           │ Spring Boot            │
                           └───────────┬───────────┘
                                       │
              ┌────────────────────────┼────────────────────────┐
              │                        │                        │
              ▼                        ▼                        ▼
       ┌──────────────┐        ┌──────────────┐        ┌──────────────┐
       │ Order Module │        │ Portfolio    │        │ User Module  │
       │              │        │ Module       │        │              │
       └──────┬───────┘        └──────┬───────┘        └──────────────┘
              │                       │
              └──────────────┬────────┘
                             ▼
                      ┌─────────────┐
                      │    Kafka    │
                      │ Event Bus   │
                      └──────┬──────┘
                             │
             ┌───────────────┼─────────────────┐
             ▼               ▼                 ▼
       ┌───────────┐   ┌────────────┐   ┌─────────────┐
       │ Portfolio │   │ Analytics  │   │ Notification│
       │ Consumer  │   │ Consumer   │   │ Consumer    │
       └─────┬─────┘   └─────┬──────┘   └─────────────┘
             │               │
             ▼               ▼
       ┌───────────┐    ┌─────────────┐
       │ PostgreSQL│    │ Redis       │
       └───────────┘    │ / Caffeine  │
                        └─────────────┘

                             AI boundary
                                 │
                                 ▼
                      ┌─────────────────────┐
                      │ Python FastAPI      │
                      │ AI/RAG Service      │
                      └──────────┬──────────┘
                                 │
                    ┌────────────┼─────────────┐
                    ▼            ▼             ▼
              Embedding      Vector DB       LLM
               Model       pgvector/Qdrant
```

The final implementation may refine this architecture after explicit
technical analysis. Do not create separate deployable services merely to
make the architecture look impressive.

------------------------------------------------------------------------

# 4. Technology Stack

## Backend

Required:

-   Java 21 LTS or another explicitly justified supported LTS version
-   Spring Boot
-   Spring Web
-   Spring Data JPA
-   Hibernate
-   Maven
-   Bean Validation
-   Spring Boot Actuator

Optional only when justified:

-   Spring Security
-   Spring Kafka
-   Resilience4j

## Database

Primary:

-   PostgreSQL

Required concepts:

-   schema design
-   primary/foreign keys
-   constraints
-   transactions
-   isolation
-   indexes
-   composite indexes
-   query plans
-   EXPLAIN / EXPLAIN ANALYZE
-   pagination
-   optimistic locking where appropriate

Optional extension:

-   CockroachDB, only after the PostgreSQL implementation is stable and
    the tradeoff is documented.

## Messaging

-   Apache Kafka
-   Kafka producers
-   consumer groups
-   partitions
-   offsets
-   retries
-   dead-letter topics
-   idempotent consumers
-   event schemas

## Caching

-   Redis
-   Caffeine where an in-process cache is useful

Required concepts:

-   cache-aside
-   TTL
-   invalidation
-   stale data
-   cache stampede considerations
-   cache hit/miss metrics

## AI Service

-   Python 3.x
-   FastAPI
-   Pydantic
-   embedding model
-   vector database: choose Qdrant or PostgreSQL + pgvector and document
    the decision
-   RAG
-   LLM API/provider abstraction

The LLM provider must be configurable and must not be hardcoded into
business logic.

## Testing

-   JUnit 5
-   Mockito
-   Spring Boot test framework
-   Testcontainers
-   API/integration tests
-   Kafka integration tests
-   database integration tests

## DevOps

-   Docker
-   Docker Compose
-   GitHub Actions

## Observability

Prefer:

-   structured JSON logs
-   correlation/request IDs
-   Micrometer
-   Prometheus
-   Grafana
-   OpenTelemetry where practical

------------------------------------------------------------------------

# 5. Functional Requirements

## 5.1 Users

Minimum:

-   create user
-   retrieve user
-   update basic user information
-   retrieve portfolio

Do not implement unnecessary authentication complexity during the first
MVP unless required.

------------------------------------------------------------------------

## 5.2 Orders

Order fields should include at least:

``` text
order_id
user_id
symbol
side
quantity
requested_price
status
idempotency_key
created_at
updated_at
```

Supported simulated order states:

``` text
CREATED
VALIDATED
EXECUTED
REJECTED
CANCELLED
```

The exact state machine must be documented.

Example:

``` text
CREATED
   │
   ▼
VALIDATED
   │
   ├──────────────► REJECTED
   │
   ▼
EXECUTED
   │
   ▼
Portfolio Update
```

------------------------------------------------------------------------

# 6. Idempotency

The order API must support an idempotency key.

Example:

``` http
POST /api/v1/orders
Idempotency-Key: 8a2d...
```

If the same logical request is submitted twice:

``` text
Request 1 ─────► create order
Request 2 ─────► return existing result
```

It must not create duplicate orders.

Document:

-   where the idempotency key is stored
-   uniqueness constraint
-   race conditions
-   transaction boundaries
-   behavior after partial failures

------------------------------------------------------------------------

# 7. Event Model

Define explicit domain events.

Initial events:

``` text
OrderCreated
OrderValidated
OrderRejected
OrderExecuted
PositionUpdated
PortfolioUpdated
```

Every event should have a documented envelope.

Example:

``` json
{
  "eventId": "uuid",
  "eventType": "OrderExecuted",
  "aggregateId": "order-id",
  "occurredAt": "timestamp",
  "version": 1,
  "payload": {}
}
```

Do not silently invent event fields. Maintain an event schema document.

------------------------------------------------------------------------

# 8. Kafka Topics

Initial topic design may be:

``` text
orders.created
orders.validated
orders.executed
orders.rejected
portfolio.updated
```

For every topic document:

-   producer
-   consumers
-   key
-   partitioning strategy
-   ordering requirements
-   retention assumption
-   retry strategy
-   dead-letter topic
-   schema versioning

------------------------------------------------------------------------

# 9. Idempotent Consumers

Every consumer that mutates state must be safe against duplicate
delivery.

Concept:

``` text
Kafka message
     │
     ▼
check eventId
     │
 ┌───┴────┐
 │        │
seen    unseen
 │        │
ignore   process
          │
          ▼
      commit state
```

Document the consistency model.

Do not claim Kafka gives exactly-once business semantics unless the
implementation genuinely supports the required guarantees.

------------------------------------------------------------------------

# 10. Database Model

Initial relational model:

``` text
users
orders
executions
positions
portfolio_snapshots
transactions
processed_events
documents
document_chunks
```

Generate:

-   ER diagram
-   SQL schema
-   migration scripts
-   index documentation
-   data dictionary

Example relationship:

``` text
User
 │
 ├──< Orders
 │       │
 │       └──< Executions
 │
 └──< Positions
          │
          └── Portfolio
```

Use migrations rather than manually modifying production schema.

Choose and document one migration strategy, such as Flyway or Liquibase.

------------------------------------------------------------------------

# 11. Database Performance Work

At least one query should be intentionally optimized.

Workflow:

``` text
Baseline query
      ↓
EXPLAIN ANALYZE
      ↓
identify bottleneck
      ↓
add/change index
      ↓
EXPLAIN ANALYZE again
      ↓
benchmark
      ↓
document result
```

Never fabricate benchmark results.

If the improvement is negligible, report that honestly and explain why.

------------------------------------------------------------------------

# 12. Redis

Use Redis for a read-heavy endpoint such as:

``` http
GET /api/v1/portfolio/{userId}
```

Pattern:

``` text
Request
   │
   ▼
Redis?
 ┌─┴─────────────┐
 │               │
 HIT             MISS
 │               │
return          PostgreSQL
                 │
                 ▼
               Redis
                 │
                 ▼
               return
```

Document:

-   key format
-   TTL
-   serialization
-   invalidation trigger
-   stale data behavior
-   cache failure behavior

The application must remain functionally correct if Redis becomes
unavailable.

------------------------------------------------------------------------

# 13. AI/RAG Pipeline

## Ingestion

``` text
Source document
      ↓
Parser
      ↓
Cleaner
      ↓
Chunker
      ↓
Metadata extraction
      ↓
Embedding
      ↓
Vector DB
```

Each chunk should retain metadata such as:

``` text
document_id
source
title
section
page/section identifier where available
chunk_id
created_at
```

Never fabricate page numbers or citations.

------------------------------------------------------------------------

# 14. Retrieval

Initial pipeline:

``` text
User Query
    ↓
Query preprocessing
    ↓
Embedding
    ↓
Vector search
    ↓
Top-K candidates
    ↓
Optional reranking
    ↓
Context selection
    ↓
LLM
```

Measure at least:

-   Recall@K
-   Precision@K where ground truth exists
-   MRR where appropriate
-   retrieval latency
-   generation latency

Do not invent a benchmark dataset. Build or identify a small explicit
evaluation set and document its construction.

------------------------------------------------------------------------

# 15. RAG Answer Contract

AI response should contain:

``` json
{
  "answer": "...",
  "sources": [
    {
      "documentId": "...",
      "chunkId": "...",
      "title": "...",
      "location": "..."
    }
  ],
  "retrieval": {
    "topK": 5,
    "latencyMs": 123
  }
}
```

The exact schema can be changed if technically justified.

The model must never create fake citations.

If evidence is insufficient:

``` text
"I don't have enough retrieved evidence to answer this reliably."
```

------------------------------------------------------------------------

# 16. AI Safety / Product Boundary

The application is a research/education platform.

It must:

-   not execute real trades
-   not connect to brokerage accounts
-   not handle real money
-   not expose real credentials
-   not present personalized investment advice as fact
-   distinguish source facts from generated explanations
-   indicate uncertainty when retrieval is insufficient

------------------------------------------------------------------------

# 17. API Design

Version APIs:

``` text
/api/v1/users
/api/v1/orders
/api/v1/portfolio
/api/v1/transactions
/api/v1/research
/api/v1/health
```

Use:

-   consistent HTTP status codes
-   validation errors
-   stable response envelopes
-   pagination
-   request IDs
-   API documentation/OpenAPI

Example error:

``` json
{
  "timestamp": "...",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "quantity must be greater than zero",
  "path": "/api/v1/orders",
  "requestId": "..."
}
```

------------------------------------------------------------------------

# 18. Observability

Every request should be traceable.

Minimum:

``` text
requestId
traceId
service
operation
duration
status
error
```

Metrics should include:

``` text
HTTP request count
HTTP error rate
HTTP latency
Kafka consumer lag
Kafka processing failures
DB query latency
Redis hit/miss rate
AI retrieval latency
LLM latency
RAG request count
```

Create a dashboard only after the underlying metrics work.

------------------------------------------------------------------------

# 19. Reliability

Implement and test:

-   timeout handling
-   bounded retries
-   exponential backoff
-   dead-letter handling
-   duplicate events
-   database failures
-   Redis unavailable
-   Kafka unavailable
-   LLM unavailable
-   malformed messages
-   invalid API input

Do not add retries everywhere.

Every retry must have:

``` text
what is retried?
why?
maximum attempts?
backoff?
what happens after failure?
```

------------------------------------------------------------------------

# 20. Testing Strategy

Testing pyramid:

``` text
                 /\
                /  \
               / E2E\
              /------\
             /Integr. \
            /----------\
           /   Unit     \
          /--------------\
```

Target:

### Unit tests

-   business logic
-   validators
-   state transitions
-   ranking logic
-   cache logic

### Integration tests

-   PostgreSQL
-   Kafka
-   Redis
-   Spring context
-   repository behavior

Use Testcontainers where practical.

### API tests

-   successful requests
-   invalid requests
-   authorization if implemented
-   idempotency
-   error responses

### Failure tests

Explicitly test:

``` text
duplicate Kafka message
DB unavailable
Redis unavailable
Kafka consumer restart
LLM timeout
malformed event
```

------------------------------------------------------------------------

# 21. Performance Testing

Use a load-testing tool such as:

-   k6
-   Gatling
-   JMeter

Choose one and document why.

Measure:

``` text
RPS
P50
P95
P99
error rate
CPU
memory
database utilization
cache hit rate
Kafka lag
```

Do not put numbers in the README or resume until the numbers have
actually been measured.

------------------------------------------------------------------------

# 22. Docker Environment

Docker Compose should provide the local development environment.

Minimum services:

``` text
postgres
redis
kafka
kafka-ui (optional)
backend
ai-service
prometheus (optional initially)
grafana (optional initially)
```

The developer should be able to start the system with a documented
command.

------------------------------------------------------------------------

# 23. CI/CD

GitHub Actions pipeline:

``` text
Push / PR
   │
   ├── compile
   ├── unit tests
   ├── integration tests
   ├── static checks
   ├── build Docker image
   └── publish artifact/image if configured
```

Do not add deployment to a paid cloud service unless explicitly
requested.

------------------------------------------------------------------------

# 24. Required Documentation

The repository must contain:

``` text
README.md
docs/
  architecture.md
  requirements.md
  api.md
  database.md
  kafka.md
  caching.md
  consistency.md
  reliability.md
  observability.md
  testing.md
  performance.md
  rag.md
  ai-evaluation.md
  security.md
  deployment.md
  decisions/
    ADR-001-architecture.md
    ADR-002-database.md
    ADR-003-kafka.md
    ADR-004-cache.md
    ADR-005-rag-vector-store.md
```

Every significant design decision gets an ADR.

------------------------------------------------------------------------

# 25. Required Diagrams

Use Mermaid in Markdown.

Required:

1.  system architecture
2.  request sequence
3.  order state machine
4.  Kafka event flow
5.  database ER diagram
6.  cache flow
7.  RAG pipeline
8.  deployment diagram
9.  failure/retry flow

Example:

``` mermaid
sequenceDiagram
    Client->>Order API: POST /orders
    Order API->>PostgreSQL: Create order
    Order API->>Kafka: OrderCreated
    Kafka->>Portfolio Consumer: OrderCreated
    Portfolio Consumer->>PostgreSQL: Update position
```

Diagrams must match the implementation.

------------------------------------------------------------------------

# 26. Repository Structure

Use this as the starting structure:

``` text
real-time-financial-intelligence/
│
├── README.md
├── LICENSE
├── .gitignore
├── .env.example
├── docker-compose.yml
├── Makefile
│
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/sunil/finintel/
│       │   │   ├── FinIntelApplication.java
│       │   │   ├── config/
│       │   │   ├── common/
│       │   │   ├── user/
│       │   │   ├── order/
│       │   │   ├── portfolio/
│       │   │   ├── transaction/
│       │   │   ├── messaging/
│       │   │   ├── cache/
│       │   │   └── observability/
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/
│       └── test/
│           └── java/com/sunil/finintel/
│
├── ai-service/
│   ├── pyproject.toml
│   ├── README.md
│   ├── app/
│   │   ├── main.py
│   │   ├── api/
│   │   ├── config/
│   │   ├── ingestion/
│   │   ├── retrieval/
│   │   ├── embeddings/
│   │   ├── generation/
│   │   ├── evaluation/
│   │   └── schemas/
│   └── tests/
│
├── docs/
│   ├── architecture.md
│   ├── requirements.md
│   ├── api.md
│   ├── database.md
│   ├── kafka.md
│   ├── caching.md
│   ├── consistency.md
│   ├── reliability.md
│   ├── observability.md
│   ├── testing.md
│   ├── performance.md
│   ├── rag.md
│   ├── ai-evaluation.md
│   ├── security.md
│   ├── deployment.md
│   └── decisions/
│
├── infra/
│   ├── docker/
│   ├── prometheus/
│   └── grafana/
│
├── scripts/
│   ├── dev/
│   ├── benchmark/
│   └── seed/
│
├── test-data/
│   ├── documents/
│   └── synthetic-market-data/
│
└── .github/
    └── workflows/
        └── ci.yml
```

The structure may evolve, but changes must be documented.

------------------------------------------------------------------------

# 27. Development Phases

## Phase 0 --- Architecture and Specification

Deliver:

-   requirements
-   assumptions
-   architecture
-   domain model
-   database model
-   API specification
-   event specification
-   repository skeleton
-   ADRs

No implementation yet.

**Gate:** Review architecture for contradictions.

------------------------------------------------------------------------

## Phase 1 --- Spring Boot Core

Implement:

-   project setup
-   user domain
-   order domain
-   REST APIs
-   PostgreSQL
-   migrations
-   validation
-   error handling
-   unit tests
-   integration tests

**Gate:** all tests pass.

------------------------------------------------------------------------

## Phase 2 --- Transactions and Idempotency

Implement:

-   order state machine
-   transaction boundaries
-   idempotency
-   optimistic locking where needed
-   concurrent-request tests

**Gate:** duplicate/concurrent requests behave correctly.

------------------------------------------------------------------------

## Phase 3 --- Kafka

Implement:

-   event schemas
-   producers
-   consumers
-   consumer groups
-   retry strategy
-   DLT
-   idempotent consumers

**Gate:** failure/replay tests pass.

------------------------------------------------------------------------

## Phase 4 --- Portfolio

Implement:

-   positions
-   executions
-   portfolio snapshots
-   portfolio APIs
-   event-driven updates

**Gate:** event-driven state remains consistent.

------------------------------------------------------------------------

## Phase 5 --- Redis

Implement:

-   cache-aside
-   TTL
-   invalidation
-   metrics
-   Redis failure fallback

**Gate:** application remains correct when Redis is unavailable.

------------------------------------------------------------------------

## Phase 6 --- AI/RAG

Implement:

-   ingestion
-   chunking
-   embeddings
-   vector search
-   retrieval
-   LLM adapter
-   citations
-   evaluation

**Gate:** AI service has deterministic tests around retrieval and schema
behavior.

------------------------------------------------------------------------

## Phase 7 --- Observability

Implement:

-   structured logs
-   request IDs
-   metrics
-   traces where practical
-   dashboards

**Gate:** a request can be followed across major components.

------------------------------------------------------------------------

## Phase 8 --- Performance

Implement:

-   baseline benchmark
-   DB optimization
-   cache benchmark
-   API load test
-   Kafka consumer throughput test
-   RAG latency measurement

**Gate:** all published performance numbers come from reproducible
scripts.

------------------------------------------------------------------------

## Phase 9 --- CI/CD and Documentation

Implement:

-   GitHub Actions
-   Docker
-   test pipeline
-   README
-   architecture docs
-   ADRs
-   runbooks

**Gate:** clean clone → documented setup → working system.

------------------------------------------------------------------------

# 28. Definition of Done

The project is complete only when:

-   clean clone works
-   documented setup works
-   backend starts
-   AI service starts
-   PostgreSQL works
-   Redis works
-   Kafka works
-   APIs work
-   tests pass
-   integration tests pass
-   Kafka failure scenarios are tested
-   cache failure is tested
-   RAG pipeline works
-   citations are traceable
-   metrics work
-   logs are structured
-   diagrams match implementation
-   documentation matches implementation
-   no secret is committed
-   no benchmark is fabricated
-   no undocumented major architecture decision exists

------------------------------------------------------------------------

# 29. Resume Evidence

At the end, produce a separate document:

``` text
docs/resume-evidence.md
```

It should contain only verified facts:

``` text
Technology:
Java, Spring Boot, Kafka, PostgreSQL, Redis, FastAPI, RAG, Docker

Measured:
P95 latency = X ms
Throughput = Y requests/sec
Cache hit rate = Z%
Retrieval Recall@5 = X
MRR = Y
```

Every number must link to:

-   benchmark command
-   dataset
-   environment
-   date
-   result file

This prevents accidental resume exaggeration.

------------------------------------------------------------------------

# 30. Interview Preparation Output

Create:

``` text
docs/interview/
  java.md
  spring.md
  sql.md
  kafka.md
  redis.md
  distributed-systems.md
  system-design.md
  ai-rag.md
  project-walkthrough.md
  failure-scenarios.md
```

Each should contain:

-   likely interviewer question
-   concise answer
-   deep answer
-   implementation location
-   relevant diagram
-   tradeoff
-   failure case

The developer should be able to explain the project without reading the
source code live.

------------------------------------------------------------------------

# 31. Final Principle

Optimize for:

``` text
Correctness
   >
Understanding
   >
Observability
   >
Testability
   >
Performance
   >
Complexity
```

Do not add technology merely because it looks impressive on a resume.

Every technology must solve a documented problem.
