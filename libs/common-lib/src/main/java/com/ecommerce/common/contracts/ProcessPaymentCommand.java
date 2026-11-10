package com.ecommerce.common.contracts;

import java.math.BigDecimal;
import java.util.UUID;

/** Saga step 2 (command): charge the customer. orderId is the business idempotency key. */
public record ProcessPaymentCommand(UUID orderId, UUID customerId, BigDecimal amount, String currency)
        implements SagaMessage {
}
