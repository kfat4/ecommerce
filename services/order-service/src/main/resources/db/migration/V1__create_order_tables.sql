CREATE TABLE orders
(
    id              UUID PRIMARY KEY,
    customer_id     UUID           NOT NULL,
    status          VARCHAR(32)    NOT NULL,
    total_amount    NUMERIC(12, 2) NOT NULL,
    currency        CHAR(3)        NOT NULL,
    idempotency_key VARCHAR(100)   NOT NULL,
    failure_reason  VARCHAR(255),
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    version         BIGINT         NOT NULL,
    -- Second idempotency guard (first one is Redis): even if Redis lost the key, the same customer can never
    -- create two orders with the same Idempotency-Key.
    CONSTRAINT ux_orders_customer_idempotency UNIQUE (customer_id, idempotency_key)
);
CREATE INDEX ix_orders_customer_created ON orders (customer_id, created_at DESC);

CREATE TABLE order_items
(
    order_id     UUID           NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    line_no      INTEGER        NOT NULL,
    product_id   VARCHAR(40)    NOT NULL,
    product_name VARCHAR(200)   NOT NULL,
    quantity     INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price   NUMERIC(12, 2) NOT NULL,
    PRIMARY KEY (order_id, line_no)
);

-- Saga log: explicit, queryable state of every distributed transaction.
CREATE TABLE order_sagas
(
    order_id   UUID PRIMARY KEY REFERENCES orders (id),
    step       VARCHAR(32) NOT NULL,
    status     VARCHAR(32) NOT NULL,
    attempts   INTEGER     NOT NULL DEFAULT 0,
    deadline   TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL
);
CREATE INDEX ix_sagas_active_deadline ON order_sagas (deadline) WHERE status IN ('STARTED', 'COMPENSATING');

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

CREATE TABLE processed_messages
(
    message_id     VARCHAR(64)  NOT NULL,
    consumer_group VARCHAR(128) NOT NULL,
    processed_at   TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (message_id, consumer_group)
);
