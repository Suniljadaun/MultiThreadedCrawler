# API

Base path: `/api/v1`. JSON in and out.

## Endpoints

| Method | Path | Status | Description |
|---|---|---|---|
| GET | /health | done | liveness check |
| POST | /users | done | create user |
| GET | /users/{id} | done | get user |
| PATCH | /users/{id} | done | update name/email (fields optional) |
| POST | /orders | done | place order (needs `Idempotency-Key` header) |
| GET | /orders/{id} | done | get order |
| POST | /orders/{id}/cancel | done | cancel a CREATED or VALIDATED order |
| GET | /users/{id}/orders | done | list user's orders, newest first |
| GET | /portfolio/{userId} | planned | positions + total value |
| GET | /users/{id}/transactions | planned | trade history |
| POST | /research/ask | planned | ask the research assistant |

## Place order

```http
POST /api/v1/orders
Idempotency-Key: 3f1c9a2e-...
Content-Type: application/json

{ "userId": 1, "symbol": "ACME", "side": "BUY", "quantity": 10, "price": 101.50 }
```

Rules:
- `symbol`: 1-10 letters, stored upper-case
- `side`: `BUY` or `SELL`
- `quantity`: 1 to 1,000,000 whole shares
- `price`: > 0, max 4 decimals

Responses:
- `201 Created` + `Location` header: new order (status `CREATED`)
- `200 OK`: same key + same body was already processed, the stored order is returned
- `400`: missing/blank `Idempotency-Key` (max 100 chars) or invalid body
- `404`: user does not exist
- `422`: key already used with a different body

The key is scoped per user. "Same body" compares userId, symbol (case-insensitive), side, quantity and price (101.5 = 101.50).

## Cancel order

`POST /api/v1/orders/{id}/cancel`

- `200`: order is now `CANCELLED` (cancelling an already-cancelled order also returns 200)
- `404`: order does not exist
- `409`: order is `EXECUTED` or `REJECTED`, or was changed at the same moment by another request (retry)

## Pagination

`GET /api/v1/users/{id}/orders?page=0&size=20`

```json
{ "items": [ ... ], "page": 0, "size": 20, "totalElements": 42, "totalPages": 3 }
```

Negative `page` becomes 0. `size` is clamped to 1-100.

## Errors

```json
{
  "timestamp": "2026-01-01T10:00:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "quantity must be greater than 0",
  "path": "/api/v1/orders",
  "requestId": null
}
```

| status | error |
|---|---|
| 400 | VALIDATION_ERROR, MALFORMED_REQUEST |
| 404 | NOT_FOUND |
| 409 | CONFLICT |
| 422 | UNPROCESSABLE |
| 500 | INTERNAL_ERROR (details only in server logs) |
