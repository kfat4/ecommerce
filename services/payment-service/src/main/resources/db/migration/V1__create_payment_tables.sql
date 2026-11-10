CREATE TABLE payments
(
    id                 UUID PRIMARY KEY,
    order_id           UUID           NOT NULL,
    customer_id        UUID           NOT NULL,
    amount             NUMERIC(12, 2) NOT NULL,
    currency           CHAR(3)        NOT NULL,
    status             VARCHAR(16)    NOT NULL,
    provider_reference VARCHAR(100),
    failure_reason     VARCHAR(255),
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL,
    version            BIGINT         NOT NULL,
    -- Business idempotency: one payment per order, whatever happens (redelivery, saga retry, two replicas).
    CONSTRAINT ux_payments_order UNIQUE (order_id)
);

CREATE TABLE outbox_events
(
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(64)  NOT NULL,
    aggregate_id   VARCHAR(64)  NOT NULL,
    topic          VARCHAR(128) NOT NULL,
    message_type   VARCHAR(128) NOT NULL,
    payload        JSONB        NOT NULL,
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL,
    published_at   TIMESTAMPTZ,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    last_error     VARCHAR(500)
);
CREATE INDEX ix_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
