package com.ecommerce.search.index;

import java.util.List;

/** Write port of the search index. Keeps the projection logic independent of the Elasticsearch client. */
public interface ProductIndex {

    WriteResult upsert(ProductDocument document);

    BulkResult bulkUpsert(List<ProductDocument> documents);

    enum WriteResult {
        /** The document was written. */
        APPLIED,
        /** The index already holds this or a newer version: duplicate or out-of-order event, safely ignored. */
        STALE
    }

    record BulkResult(int applied, int stale, int failed) {

        public BulkResult plus(BulkResult other) {
            return new BulkResult(applied + other.applied, stale + other.stale, failed + other.failed);
        }
    }
}
