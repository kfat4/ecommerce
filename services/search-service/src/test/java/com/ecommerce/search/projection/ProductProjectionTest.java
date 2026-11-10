package com.ecommerce.search.projection;

import com.ecommerce.common.contracts.ProductDeletedEvent;
import com.ecommerce.common.contracts.ProductUpsertedEvent;
import com.ecommerce.search.index.ProductDocument;
import com.ecommerce.search.index.ProductIndex;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductProjectionTest {

    /** In-memory index with the same external-versioning rule as Elasticsearch: only strictly newer versions win. */
    static class VersionedInMemoryIndex implements ProductIndex {
        final Map<String, ProductDocument> docs = new HashMap<>();
        final List<ProductDocument> writes = new ArrayList<>();

        @Override
        public WriteResult upsert(ProductDocument document) {
            ProductDocument current = docs.get(document.id());
            if (current != null && current.version() >= document.version()) {
                return WriteResult.STALE;
            }
            docs.put(document.id(), document);
            writes.add(document);
            return WriteResult.APPLIED;
        }

        @Override
        public BulkResult bulkUpsert(List<ProductDocument> documents) {
            int applied = 0;
            int stale = 0;
            for (ProductDocument d : documents) {
                if (upsert(d) == WriteResult.APPLIED) {
                    applied++;
                } else {
                    stale++;
                }
            }
            return new BulkResult(applied, stale, 0);
        }
    }

    private final VersionedInMemoryIndex index = new VersionedInMemoryIndex();
    private final ProductProjection projection = new ProductProjection(index, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

    @Test
    void upsertEventBecomesSearchDocument() {
        assertThat(projection.apply(upserted(0, "Laptop Pro 14", "1499.00")))
                .isEqualTo(ProductIndex.WriteResult.APPLIED);

        ProductDocument doc = index.docs.get("PRD-1001");
        assertThat(doc.name()).isEqualTo("Laptop Pro 14");
        assertThat(doc.price()).isEqualByComparingTo("1499.00");
        assertThat(doc.active()).isTrue();
        assertThat(doc.deleted()).isFalse();
        assertThat(doc.version()).isZero();
    }

    @Test
    void duplicateDeliveryIsIgnored() {
        projection.apply(upserted(1, "Laptop Pro 14", "1499.00"));

        assertThat(projection.apply(upserted(1, "Laptop Pro 14", "1499.00")))
                .isEqualTo(ProductIndex.WriteResult.STALE);
        assertThat(index.writes).hasSize(1);
    }

    @Test
    void olderEventArrivingLateDoesNotOverwriteNewerState() {
        projection.apply(upserted(2, "Laptop Pro 14 (2026)", "1399.00"));

        assertThat(projection.apply(upserted(1, "Laptop Pro 14", "1499.00")))
                .isEqualTo(ProductIndex.WriteResult.STALE);
        assertThat(index.docs.get("PRD-1001").price()).isEqualByComparingTo("1399.00");
    }

    @Test
    void deleteLeavesTombstoneThatBlocksResurrection() {
        projection.apply(upserted(1, "Laptop Pro 14", "1499.00"));
        projection.apply(new ProductDeletedEvent("PRD-1001", 2, Instant.now()));

        ProductDocument tombstone = index.docs.get("PRD-1001");
        assertThat(tombstone.deleted()).isTrue();
        assertThat(tombstone.active()).isFalse();
        assertThat(tombstone.name()).isNull();

        // a delayed upsert from before the delete must not bring the product back
        assertThat(projection.apply(upserted(1, "Laptop Pro 14", "1499.00")))
                .isEqualTo(ProductIndex.WriteResult.STALE);
        assertThat(index.docs.get("PRD-1001").deleted()).isTrue();
    }

    private static ProductUpsertedEvent upserted(long version, String name, String price) {
        return new ProductUpsertedEvent("PRD-1001", version, "LAPTOP-PRO-14", name, "14\" developer laptop",
                "electronics", new BigDecimal(price), "EUR", Map.of("ram", "32GB"), true, Instant.now());
    }
}
