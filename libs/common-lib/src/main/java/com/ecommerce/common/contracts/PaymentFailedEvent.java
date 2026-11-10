package com.ecommerce.common.contracts;

import java.util.UUID;

public record PaymentFailedEvent(UUID orderId, String reason) implements SagaMessage {
}
