# Events and Kafka

Events are written to `outbox_events` in the same DB transaction as the change, then published to Kafka (see ADR-002).

Delivery is at-least-once. Consumers skip duplicates using `processed_events`.

## Event envelope

```json
{
  "eventId": "uuid",
  "eventType": "OrderExecuted",
  "aggregateId": "order id",
  "userId": 1,
  "occurredAt": "2026-01-01T10:00:00Z",
  "version": 1,
  "payload": {}
}
```

## Topics (planned)

| Topic | Producer | Consumer | Key |
|---|---|---|---|
| orders.created | order module | validation consumer | userId |
| orders.validated | validation consumer | execution consumer | userId |
| orders.rejected | validation consumer | none yet | userId |
| orders.executed | execution consumer | portfolio consumer | userId |
| portfolio.updated | portfolio consumer | cache invalidation | userId |

Key is `userId` so all events for one user stay in order on one partition.

## Failures
- Retry a failed message 3 times with backoff.
- After that, send it to `<topic>.DLT` and log it.
- Bad JSON goes straight to the DLT, no retry.
