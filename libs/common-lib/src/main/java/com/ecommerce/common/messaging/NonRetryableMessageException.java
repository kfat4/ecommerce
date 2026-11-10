package com.ecommerce.common.messaging;

/** Thrown for poison messages: retrying will never help, so the error handler sends them straight to the DLT. */
public class NonRetryableMessageException extends RuntimeException {

    public NonRetryableMessageException(String message) {
        super(message);
    }

    public NonRetryableMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
