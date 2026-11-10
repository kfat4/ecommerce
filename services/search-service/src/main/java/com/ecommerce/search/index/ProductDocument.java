package com.ecommerce.search.index;

import com.ecommerce.common.contracts.ProductDeletedEvent;
import com.ecommerce.common.contracts.ProductUpsertedEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Shape of a document in the {@code products} index (denormalized, search-optimized; not the domain model).
 * {@code version} is the product-service aggregate version and is also used as the Elasticsearch
 * <b>external version</b> – see {@link ElasticsearchProductIndex}.
 */
public record ProductDocument(
        String id,
        long version,
        String sku,
        String name,
        String description,
        String category,
        BigDecimal price,
        String currency,
        Map<String, String> attributes,
        boolean active,
        boolean deleted,
        Instant updatedAt) {

    public static ProductDocument from(ProductUpsertedEvent e) {
        return new ProductDocument(e.productId(), e.version(), e.sku(), e.name(), e.description(), e.category(),
                e.price(), e.currency(), e.attributes(), e.active(), false, e.occurredAt());
    }

    /**
     * A deleted product is kept as a small tombstone document instead of being removed. A real ES delete only
     * remembers the version for {@code index.gc_deletes} (60s); a late, older upsert could resurrect the product.
     * A tombstone keeps its version forever, so stale events are always rejected.
     */
    public static ProductDocument tombstone(ProductDeletedEvent e) {
        return new ProductDocument(e.productId(), e.version(), null, null, null, null, null, null, null,
                false, true, e.occurredAt());
    }
}
