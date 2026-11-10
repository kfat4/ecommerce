package com.ecommerce.common.contracts;

import java.util.UUID;

/**
 * Marker for every message that participates in the order saga.
 * The orderId doubles as the Kafka record key, which guarantees per-order ordering inside a partition.
 */
public interface SagaMessage extends IntegrationMessage {
    UUID orderId();
}
