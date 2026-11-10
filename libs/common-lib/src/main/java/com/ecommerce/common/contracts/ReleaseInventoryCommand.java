package com.ecommerce.common.contracts;

import java.util.UUID;

/** Compensating command: undo a previous reservation (must be idempotent on the receiver side). */
public record ReleaseInventoryCommand(UUID orderId, String reason) implements SagaMessage {
}
