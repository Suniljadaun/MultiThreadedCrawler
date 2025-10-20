# Assumptions

| ID | Assumption | Why | Status |
|---|---|---|---|
| A-001 | Quantity is whole shares (no fractions) | keeps position math simple | Accepted |
| A-002 | No cash balance check on BUY orders | plan does not require a cash account | Accepted |
| A-003 | SELL requires enough quantity in the position | cannot sell what you don't hold | Accepted |
| A-004 | Execution price comes from synthetic market data | no real market feeds | Accepted |
| A-005 | Supported symbols are a fixed list in config | avoids external symbol lookup | Accepted |
| A-006 | No authentication in the first MVP | plan says avoid auth complexity early | Accepted |
| A-007 | Per-user event ordering is enough (no global order) | portfolio state is per user | Accepted |
| A-008 | Build targets Java 21 (Temurin 21 installed) | plan asks for an LTS target | Accepted |
| A-009 | Idempotency keys are scoped per user and kept forever | simple and safe for a learning project; no expiry job yet | Accepted |
| A-010 | Order list page size is clamped to 1-100 instead of rejected | friendlier for clients, protects the DB | Accepted |
| A-011 | Cancelling an already-cancelled order returns 200 | cancel is safe to retry | Accepted |
| A-012 | SELL holdings are checked at execution, with the position row locked | a validation-time check could race | Accepted |
| A-013 | One outbox relay instance; per-user order is only guaranteed with a single relay | SKIP LOCKED allows more, but could reorder a user's events | Accepted |
| A-014 | Orders fill completely at the synthetic market price; `price` is a limit | no order book or partial fills in this project | Accepted |
| A-015 | Rejected orders keep their reason only in the OrderRejected event | avoids a schema change; can be added to orders later | Accepted |
| A-016 | A portfolio may be up to 60 s stale after a rare cache-aside race | read-only view; eviction after commit covers the normal case | Accepted |
| A-017 | Research documents are markdown/plain text only; PDF parsing comes later | enough to build and measure the pipeline | Accepted |
| A-018 | Document locations are section paths, never page numbers | markdown has no pages; plan forbids invented pages | Accepted |
| A-019 | The AI service shares the backend's PostgreSQL but only touches the `research` schema | one less container to run (ADR-005) | Accepted |
| A-020 | Offline defaults (hash embeddings, extractive answers) are a baseline, not the target quality | tests and demos must run with no API key | Accepted |
| A-021 | A client-supplied X-Request-ID is reused if it matches `[A-Za-z0-9._-]{1,100}` | lets callers correlate; the pattern keeps log injection out | Accepted |
