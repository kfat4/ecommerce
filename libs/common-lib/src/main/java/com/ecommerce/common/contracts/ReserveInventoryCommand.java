package com.ecommerce.common.contracts;

import java.util.List;
import java.util.UUID;

/** Saga step 1 (command): order-service asks inventory-service to reserve stock. */
public record ReserveInventoryCommand(UUID orderId, List<OrderLine> lines) implements SagaMessage {
}
