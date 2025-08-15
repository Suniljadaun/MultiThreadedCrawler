# ADR-001: Modular monolith + separate AI service

Status: Accepted

## Context
The project needs orders, portfolio, events and a RAG assistant. Splitting every module into its own service would add a lot of deployment work without a real need.

## Decision
- One Spring Boot backend with clear internal modules.
- One separate Python FastAPI service for AI, since it uses a different language and libraries.
- Modules talk through Kafka events where async processing is useful.

## Alternatives
- Full microservices: more to deploy and debug, no real benefit at this size.
- Everything in Java: Python has better AI/RAG tooling.

## Consequences
- Simple local setup: two apps + Postgres, Kafka, Redis.
- A module can be split out later if there is a reason.
