package com.ecommerce.common.contracts;

import java.util.UUID;

public record InventoryReleasedEvent(UUID orderId) implements SagaMessage {
}
