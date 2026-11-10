package com.ecommerce.common.contracts;

import java.time.Instant;

/** A product was (soft) deleted. The version is higher than every earlier upsert of the same product. */
public record ProductDeletedEvent(String productId, long version, Instant occurredAt) implements ProductEvent {
}
