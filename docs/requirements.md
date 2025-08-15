# Requirements

Simulated platform for learning. No real trades, no real money, no investment advice.

## Users
- Create a user
- Get a user
- Update basic user info

## Orders
- Place an order: symbol, side (BUY/SELL), quantity, price
- Every order request needs an `Idempotency-Key` header
- Same key sent twice returns the first order and does not create a new one
- Order states: CREATED, VALIDATED, EXECUTED, REJECTED, CANCELLED
- Get an order by id
- List a user's orders (paginated)

## Portfolio
- Positions update when an order is executed
- Get a user's portfolio (positions + total value)
- Get a user's transaction history (paginated)

## Events
- Order and portfolio changes are published as Kafka events
- Consumers must handle duplicate events safely

## Cache
- Portfolio reads are cached in Redis
- App keeps working if Redis is down

## Research assistant
- Ask a question about stored financial documents
- Answer includes sources (document + chunk)
- If there is not enough evidence, say so instead of guessing

## Non-functional
- Config from environment variables, no secrets in git
- Request id in every log line
- Tests for every feature
- Runs locally with Docker Compose
