package com.ecommerce.common.contracts;

import java.util.UUID;

public record InventoryReservedEvent(UUID orderId) implements SagaMessage {
}
