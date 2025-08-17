# ADR-003: Kafka for order and portfolio events

Status: Accepted

## Context
After an order is placed, validation, execution and portfolio updates can happen asynchronously. Other consumers (analytics, notifications) may be added later.

## Decision
- Use Kafka with one topic per event type (see docs/kafka.md).
- Key every message by `userId` so one user's events stay in order.
- At-least-once delivery. Consumers are idempotent using `processed_events`.
- Failed messages: 3 retries with backoff, then a dead-letter topic.

## Alternatives
- RabbitMQ: simpler, but no log replay and weaker per-key ordering.
- Plain Spring events: no durability, lost on restart.
- Direct DB polling: works, but no fan-out to multiple consumers.

## Consequences
- Kafka must run locally (Docker).
- Order status updates are eventually consistent: `POST /orders` returns CREATED, not EXECUTED.
- No exactly-once claims. Duplicates are handled by idempotent consumers.
