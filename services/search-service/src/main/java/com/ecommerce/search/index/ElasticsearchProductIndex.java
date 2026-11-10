package com.ecommerce.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Idempotent, order-independent writes with Elasticsearch <b>external versioning</b>.
 * <p>
 * With {@code version_type=external} Elasticsearch accepts a write only if its version is <i>greater</i> than the
 * stored one, otherwise it answers 409 (version conflict). That single rule gives us:
 * <ul>
 *   <li><b>Idempotency</b> – a redelivered event (same version) is rejected, no inbox table needed;</li>
 *   <li><b>Ordering</b> – an older event arriving after a newer one is rejected;</li>
 *   <li><b>Safe concurrency</b> – the Kafka projection and a backfill can write at the same time.</li>
 * </ul>
 */
@Component
public class ElasticsearchProductIndex implements ProductIndex {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchProductIndex.class);
    private static final int VERSION_CONFLICT = 409;

    private final ElasticsearchClient client;
    private final SearchIndexProperties properties;

    public ElasticsearchProductIndex(ElasticsearchClient client, SearchIndexProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public WriteResult upsert(ProductDocument document) {
        try {
            client.index(i -> i
                    .index(properties.alias())
                    .id(document.id())
                    .version(document.version())
                    .versionType(VersionType.External)
                    .document(document));
            return WriteResult.APPLIED;
        } catch (ElasticsearchException e) {
            if (e.status() == VERSION_CONFLICT) {
                return WriteResult.STALE;
            }
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("Elasticsearch unreachable", e);
        }
    }

    @Override
    public BulkResult bulkUpsert(List<ProductDocument> documents) {
        if (documents.isEmpty()) {
            return new BulkResult(0, 0, 0);
        }
        BulkRequest.Builder bulk = new BulkRequest.Builder();
        for (ProductDocument document : documents) {
            bulk.operations(op -> op.index(i -> i
                    .index(properties.alias())
                    .id(document.id())
                    .version(document.version())
                    .versionType(VersionType.External)
                    .document(document)));
        }
        try {
            BulkResponse response = client.bulk(bulk.build());
            int applied = 0;
            int stale = 0;
            int failed = 0;
            for (BulkResponseItem item : response.items()) {
                if (item.error() == null) {
                    applied++;
                } else if (item.status() == VERSION_CONFLICT) {
                    stale++;
                } else {
                    failed++;
                    log.warn("Bulk index failed id={} status={} reason={}", item.id(), item.status(),
                            item.error().reason());
                }
            }
            return new BulkResult(applied, stale, failed);
        } catch (IOException e) {
            throw new UncheckedIOException("Elasticsearch unreachable", e);
        }
    }
}
