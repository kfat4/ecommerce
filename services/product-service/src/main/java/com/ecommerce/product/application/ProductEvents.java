package com.ecommerce.product.application;

import com.ecommerce.common.contracts.ProductDeletedEvent;
import com.ecommerce.common.contracts.ProductUpsertedEvent;
import com.ecommerce.product.domain.Product;

import java.time.Instant;
import java.util.Map;

/** Maps the aggregate to its published events (the public contract is decoupled from the persistence model). */
final class ProductEvents {

    private ProductEvents() {
    }

    static ProductUpsertedEvent upserted(Product p) {
        return new ProductUpsertedEvent(p.getId(), p.getVersion(), p.getSku(), p.getName(), p.getDescription(),
                p.getCategory(), p.getPrice(), p.getCurrency(),
                p.getAttributes() == null ? Map.of() : p.getAttributes(), p.isActive(), Instant.now());
    }

    static ProductDeletedEvent deleted(Product p) {
        return new ProductDeletedEvent(p.getId(), p.getVersion(), Instant.now());
    }
}
