package com.ecommerce.search.projection;

import com.ecommerce.common.contracts.ProductDeletedEvent;
import com.ecommerce.common.contracts.ProductEvent;
import com.ecommerce.common.contracts.ProductUpsertedEvent;
import com.ecommerce.search.index.ProductDocument;
import com.ecommerce.search.index.ProductIndex;
import com.ecommerce.search.index.ProductIndex.WriteResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Turns catalogue events into search documents (the "projection" of a CQRS read model). */
@Component
public class ProductProjection {

    private static final Logger log = LoggerFactory.getLogger(ProductProjection.class);

    private final ProductIndex index;
    private final MeterRegistry meterRegistry;

    public ProductProjection(ProductIndex index, MeterRegistry meterRegistry) {
        this.index = index;
        this.meterRegistry = meterRegistry;
    }

    public WriteResult apply(ProductEvent event) {
        // ProductEvent is sealed: the compiler guarantees every event type is handled.
        ProductDocument document = switch (event) {
            case ProductUpsertedEvent upserted -> ProductDocument.from(upserted);
            case ProductDeletedEvent deleted -> ProductDocument.tombstone(deleted);
        };
        WriteResult result = index.upsert(document);
        // ecommerce_search_projection_total{event,result}: a high "stale" share means many duplicates/reorderings.
        Counter.builder("ecommerce.search.projection")
                .description("Catalogue events applied to (or rejected by) the search index")
                .tag("event", event instanceof ProductDeletedEvent ? "deleted" : "upserted")
                .tag("result", result.name().toLowerCase())
                .register(meterRegistry)
                .increment();
        if (result == WriteResult.STALE) {
            log.info("Ignored stale/duplicate {} for product={} version={}",
                    event.getClass().getSimpleName(), event.productId(), event.version());
        } else {
            log.debug("Indexed product={} version={}", event.productId(), event.version());
        }
        return result;
    }
}
