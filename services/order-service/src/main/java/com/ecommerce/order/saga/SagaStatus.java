package com.ecommerce.order.saga;

public enum SagaStatus {
    /** Moving forward. */
    STARTED,
    /** Undoing completed steps with compensating commands. */
    COMPENSATING,
    /** Terminal – business transaction succeeded. */
    COMPLETED,
    /** Terminal – business transaction rolled back by compensation. */
    ABORTED,
    /** Needs a human: retries exhausted in a step that cannot be safely compensated automatically. */
    STUCK
}
