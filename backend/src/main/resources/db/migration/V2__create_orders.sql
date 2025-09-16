CREATE TABLE orders (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT        NOT NULL REFERENCES users (id),
    symbol           VARCHAR(10)   NOT NULL,
    side             VARCHAR(4)    NOT NULL,
    quantity         BIGINT        NOT NULL,
    requested_price  NUMERIC(19,4) NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    idempotency_key  VARCHAR(100)  NOT NULL,
    -- SHA-256 of the request body, to detect a key reused with a different body
    request_hash     CHAR(64)      NOT NULL,
    -- Optimistic locking for status changes
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_orders_side     CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT ck_orders_quantity CHECK (quantity > 0),
    CONSTRAINT ck_orders_price    CHECK (requested_price > 0),
    CONSTRAINT ck_orders_status   CHECK (status IN ('CREATED', 'VALIDATED', 'EXECUTED', 'REJECTED', 'CANCELLED')),
    -- Same key from the same user can never create a second order
    CONSTRAINT uq_orders_user_idempotency UNIQUE (user_id, idempotency_key)
);

-- Supports "list a user's orders, newest first"
CREATE INDEX ix_orders_user_created ON orders (user_id, created_at DESC);
