# API

Base path: `/api/v1`. JSON in and out.

## Endpoints (planned)

| Method | Path | Description |
|---|---|---|
| POST | /users | create user |
| GET | /users/{id} | get user |
| PATCH | /users/{id} | update name/email |
| POST | /orders | place order (needs `Idempotency-Key` header) |
| GET | /orders/{id} | get order |
| GET | /users/{id}/orders | list user's orders (paginated) |
| POST | /orders/{id}/cancel | cancel if not yet executed |
| GET | /portfolio/{userId} | positions + total value |
| GET | /users/{id}/transactions | trade history (paginated) |
| POST | /research/ask | ask the research assistant |

## Place order example

```http
POST /api/v1/orders
Idempotency-Key: 3f1c9a2e-...
Content-Type: application/json

{ "userId": 1, "symbol": "ACME", "side": "BUY", "quantity": 10, "price": 101.50 }
```

- `201 Created` for a new order.
- `200 OK` with the same order if the key was already used with the same body.
- `422` if the key was already used with a different body.

## Errors

```json
{
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "quantity must be greater than zero",
  "path": "/api/v1/orders",
  "requestId": "..."
}
```

Pagination: `?page=0&size=20`, max size 100.
