package com.ecommerce.order.domain;

public enum OrderStatus {
    /** Created, waiting for stock reservation. */
    PENDING,
    /** Stock reserved, waiting for payment. */
    AWAITING_PAYMENT,
    /** Terminal: paid and stock committed. */
    CONFIRMED,
    /** Terminal: rejected or compensated. */
    CANCELLED
}
