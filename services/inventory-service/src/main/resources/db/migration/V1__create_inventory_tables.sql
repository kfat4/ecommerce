CREATE TABLE inventory_items
(
    product_id         VARCHAR(40) PRIMARY KEY,
    available_quantity INTEGER     NOT NULL CHECK (available_quantity >= 0), -- last line of defence against overselling
    reserved_quantity  INTEGER     NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE stock_reservations
(
    id         UUID PRIMARY KEY,
    order_id   UUID        NOT NULL,
    product_id VARCHAR(40) NOT NULL REFERENCES inventory_items (product_id),
    quantity   INTEGER     NOT NULL CHECK (quantity > 0),
    status     VARCHAR(16) NOT NULL CHECK (status IN ('RESERVED', 'RELEASED', 'COMMITTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_reservation_order_product UNIQUE (order_id, product_id)
);

CREATE INDEX ix_reservations_order ON stock_reservations (order_id);

-- Tombstones: orders whose stock was released (compensated). Protects against a *late* ReserveInventory
-- command that arrives after the order-service already timed out and compensated the order.
CREATE TABLE released_orders
(
    order_id    UUID PRIMARY KEY,
    released_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Transactional outbox (written by OutboxPublisher, drained by OutboxRelay)
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
-- Partial index: the relay only ever scans unpublished rows, so the index stays tiny.
CREATE INDEX ix_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

-- Idempotent consumer (inbox)
CREATE TABLE processed_messages
(
    message_id     VARCHAR(64)  NOT NULL,
    consumer_group VARCHAR(128) NOT NULL,
    processed_at   TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (message_id, consumer_group)
);
