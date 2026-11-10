package com.ecommerce.common.messaging;

/** Kafka header names shared by all producers/consumers. */
public final class MessageHeaders {

    /** Unique id of the message; consumers use it for de-duplication (inbox pattern). */
    public static final String MESSAGE_ID = "x-message-id";
    /** Logical message type, see {@link com.ecommerce.common.contracts.MessageTypes}. */
    public static final String MESSAGE_TYPE = "x-message-type";
    /** Correlation id that ties the original HTTP request to every asynchronous saga step. */
    public static final String CORRELATION_ID = "x-correlation-id";

    private MessageHeaders() {
    }
}
