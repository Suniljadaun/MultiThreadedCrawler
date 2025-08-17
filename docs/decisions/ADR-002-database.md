# ADR-002: PostgreSQL with Flyway

Status: Accepted

## Context
Orders and positions need transactions, constraints and reliable locking.

## Decision
- PostgreSQL as the main database.
- Flyway for migrations (plain SQL files, easy to read).
- Add an `outbox_events` table: an order and its event are saved in the same transaction, and a separate publisher sends the event to Kafka. This avoids losing events if Kafka is down.

## Alternatives
- MySQL: fine too, but PostgreSQL also supports pgvector for the AI service.
- Liquibase: more features, but XML/YAML changelogs are harder to read than SQL.
- Publishing to Kafka directly in the request: the DB write can succeed and the Kafka send fail, leaving them out of sync.

## Consequences
- The outbox needs a small publisher job and adds a little delay before events go out.
- The `outbox_events` table is not in the original plan; this ADR documents why it was added.
