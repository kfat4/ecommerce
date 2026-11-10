package com.ecommerce.common.contracts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** A product was created or changed. Carries the full current state (not a diff). */
public record ProductUpsertedEvent(
        String productId,
        long version,
        String sku,
        String name,
        String description,
        String category,
        BigDecimal price,
        String currency,
        Map<String, String> attributes,
        boolean active,
        Instant occurredAt) implements ProductEvent {
}
