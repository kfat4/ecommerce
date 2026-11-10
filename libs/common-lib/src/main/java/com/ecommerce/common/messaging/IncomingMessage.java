package com.ecommerce.common.messaging;

import com.ecommerce.common.contracts.IntegrationMessage;
import com.ecommerce.common.contracts.SagaMessage;

/** A deserialized Kafka message together with the metadata needed for idempotent processing. */
public record IncomingMessage(String messageId, String type, IntegrationMessage payload) {

    /** For saga participants: the payload must be a saga message, anything else is a poison message. */
    public SagaMessage sagaPayload() {
        if (payload instanceof SagaMessage saga) {
            return saga;
        }
        throw new NonRetryableMessageException("Not a saga message: " + type);
    }
}
