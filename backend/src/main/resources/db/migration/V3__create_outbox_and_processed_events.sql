-- Transactional outbox: events are written in the same DB transaction as the business change,
-- then a relay publishes them to Kafka (see ADR-002)
CREATE TABLE outbox_events (
    id            BIGSERIAL PRIMARY KEY,
    event_id      UUID         NOT NULL UNIQUE,
    topic         VARCHAR(100) NOT NULL,
    message_key   VARCHAR(100) NOT NULL,
    event_type    VARCHAR(50)  NOT NULL,
    aggregate_id  VARCHAR(50)  NOT NULL,
    payload       TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ,
    attempts      INT          NOT NULL DEFAULT 0,
    last_error    TEXT
);

-- Only unpublished rows are scanned by the relay
CREATE INDEX ix_outbox_unpublished ON outbox_events (id) WHERE published_at IS NULL;

-- Events already handled by a consumer; the primary key makes duplicate delivery harmless
CREATE TABLE processed_events (
    consumer_name VARCHAR(100) NOT NULL,
    event_id      UUID         NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_name, event_id)
);
