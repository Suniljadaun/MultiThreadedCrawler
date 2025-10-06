-- Synthetic market prices (no real market data, see A-004)
CREATE TABLE market_prices (
    symbol      VARCHAR(10)   PRIMARY KEY,
    price       NUMERIC(19,4) NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_market_prices_price CHECK (price > 0)
);

INSERT INTO market_prices (symbol, price) VALUES
    ('ACME', 100.0000),
    ('GLOBEX', 250.0000),
    ('INITECH', 40.0000),
    ('UMBRELLA', 75.0000),
    ('STARK', 300.0000);

-- Current holding per user and symbol
CREATE TABLE positions (
    id          BIGSERIAL     PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users (id),
    symbol      VARCHAR(10)   NOT NULL,
    quantity    BIGINT        NOT NULL,
    avg_cost    NUMERIC(19,4) NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_positions_quantity CHECK (quantity >= 0),
    CONSTRAINT uq_positions_user_symbol UNIQUE (user_id, symbol)
);

-- One fill per executed order (the unique constraint makes a double execution impossible)
CREATE TABLE executions (
    id           BIGSERIAL     PRIMARY KEY,
    order_id     BIGINT        NOT NULL REFERENCES orders (id),
    user_id      BIGINT        NOT NULL REFERENCES users (id),
    symbol       VARCHAR(10)   NOT NULL,
    side         VARCHAR(4)    NOT NULL,
    quantity     BIGINT        NOT NULL,
    price        NUMERIC(19,4) NOT NULL,
    executed_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_executions_order UNIQUE (order_id)
);

-- Supports "trade history for a user, newest first"
CREATE INDEX ix_executions_user_executed ON executions (user_id, executed_at DESC);
