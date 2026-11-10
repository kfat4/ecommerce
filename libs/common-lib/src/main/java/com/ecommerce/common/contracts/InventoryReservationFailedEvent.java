package com.ecommerce.common.contracts;

import java.util.UUID;

public record InventoryReservationFailedEvent(UUID orderId, String reason) implements SagaMessage {
}
