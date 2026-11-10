package com.ecommerce.order.saga;

public enum SagaStep {
    RESERVE_INVENTORY,
    PROCESS_PAYMENT,
    RELEASE_INVENTORY,
    DONE
}
