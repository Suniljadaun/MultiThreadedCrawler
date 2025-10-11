# ADR-004: Redis cache for portfolio reads

Status: Accepted

## Context
`GET /portfolio/{userId}` joins positions and prices and is read far more often than it changes.

## Decision
- Cache-aside with Redis.
- Key: `portfolio:v1:{userId}`, value: portfolio JSON, TTL 60 seconds.
- Delete the key after the transaction that changes a position or a price commits.
  (Originally planned via the `portfolio.updated` event; in-process after-commit eviction is simpler and immediate. See docs/caching.md.)
- If Redis is down, log it and read from PostgreSQL. The request still succeeds.

## Alternatives
- Caffeine only (in-process): fast, but each app instance has its own copy and invalidation is harder.
- No cache: simplest; will be compared in Phase 8 benchmarks.

## Consequences
- A read may be up to 60 seconds stale if an invalidation is missed.
- Redis is never the source of truth.
- Hit/miss counts are exposed as metrics.
