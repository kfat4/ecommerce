package com.ecommerce.common.contracts;

import java.math.BigDecimal;
import java.util.UUID;

/** Domain event published when the saga completes successfully (choreography consumers listen to it). */
public record OrderConfirmedEvent(UUID orderId, UUID customerId, BigDecimal totalAmount, String currency)
        implements SagaMessage {
}
