package com.ecommerce.common.contracts;

import java.util.UUID;

public record OrderCancelledEvent(UUID orderId, UUID customerId, String reason) implements SagaMessage {
}
