package com.ecommerce.search.backfill;

import com.ecommerce.common.web.BusinessException;
import com.ecommerce.common.web.PageResponse;
import com.ecommerce.search.backfill.ProductCatalogApi.ExportedProduct;
import com.ecommerce.search.index.ProductDocument;
import com.ecommerce.search.index.ProductIndex;
import com.ecommerce.search.index.ProductIndex.BulkResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Re-synchronises the index with the source of truth by paging through product-service's export API and
 * bulk-indexing every product. Use it after an Elasticsearch data loss, or to fill the index for products that
 * existed before events were introduced.
 * <p>
 * Runs safely <b>while</b> the Kafka projection keeps writing: both use external versioning, so whichever write
 * carries the newer version wins and nothing is ever overwritten with older data.
 */
@Service
public class BackfillService {

    private static final Logger log = LoggerFactory.getLogger(BackfillService.class);
    private static final int PAGE_SIZE = 200;

    private final ProductCatalogApi catalog;
    private final ProductIndex index;
    /** One backfill per instance at a time; the operation is idempotent, so a parallel run elsewhere is harmless. */
    private final AtomicBoolean running = new AtomicBoolean();

    public BackfillService(ProductCatalogApi catalog, ProductIndex index) {
        this.catalog = catalog;
        this.index = index;
    }

    public BackfillReport run() {
        if (!running.compareAndSet(false, true)) {
            throw new BusinessException("backfill-running", "A backfill is already running on this instance.");
        }
        Instant started = Instant.now();
        try {
            BulkResult total = new BulkResult(0, 0, 0);
            int page = 0;
            int totalPages;
            do {
                PageResponse<ExportedProduct> batch = catalog.export(page, PAGE_SIZE);
                total = total.plus(index.bulkUpsert(toDocuments(batch.content(), started)));
                totalPages = batch.totalPages();
                page++;
            } while (page < totalPages);
            var report = new BackfillReport(page, total.applied(), total.stale(), total.failed(),
                    Duration.between(started, Instant.now()).toMillis());
            log.info("Backfill finished: {}", report);
            return report;
        } finally {
            running.set(false);
        }
    }

    static List<ProductDocument> toDocuments(List<ExportedProduct> products, Instant now) {
        return products.stream().map(p -> new ProductDocument(p.id(), p.version(),
                p.deleted() ? null : p.sku(),
                p.deleted() ? null : p.name(),
                p.deleted() ? null : p.description(),
                p.deleted() ? null : p.category(),
                p.deleted() ? null : p.price(),
                p.deleted() ? null : p.currency(),
                p.deleted() ? null : p.attributes(),
                p.active() && !p.deleted(), p.deleted(), now)).toList();
    }

    public record BackfillReport(int pages, int applied, int stale, int failed, long tookMs) {
    }
}
