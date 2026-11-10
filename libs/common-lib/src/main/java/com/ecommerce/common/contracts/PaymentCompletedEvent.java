package com.ecommerce.common.contracts;

import java.util.UUID;

public record PaymentCompletedEvent(UUID orderId, UUID paymentId, String providerReference) implements SagaMessage {
}
