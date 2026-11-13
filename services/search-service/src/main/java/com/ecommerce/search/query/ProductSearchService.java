package com.ecommerce.search.query;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.ecommerce.common.web.ServiceUnavailableException;
import com.ecommerce.search.index.ProductDocument;
import com.ecommerce.search.query.SearchDtos.FacetBucket;
import com.ecommerce.search.query.SearchDtos.ProductHit;
import com.ecommerce.search.query.SearchDtos.SearchResult;
import com.ecommerce.search.query.SearchDtos.Suggestion;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ProductSearchService {

    private final ElasticsearchClient client;
    private final ProductQueryFactory queries;

    public ProductSearchService(ElasticsearchClient client, ProductQueryFactory queries) {
        this.client = client;
        this.queries = queries;
    }

    public SearchResult search(ProductSearchCriteria criteria) {
        SearchResponse<ProductDocument> response = execute(() ->
                client.search(queries.search(criteria), ProductDocument.class));
        List<ProductHit> items = response.hits().hits().stream().map(ProductSearchService::toHit).toList();
        long total = response.hits().total() == null ? items.size() : response.hits().total().value();

        Map<String, List<FacetBucket>> facets = new LinkedHashMap<>();
        Aggregate categories = response.aggregations().get(ProductQueryFactory.CATEGORY_FACET);
        if (categories != null) {
            facets.put("category", categories.sterms().buckets().array().stream()
                    .map(b -> new FacetBucket(b.key().stringValue(), b.docCount())).toList());
        }
        Aggregate prices = response.aggregations().get(ProductQueryFactory.PRICE_FACET);
        if (prices != null) {
            facets.put("price", prices.range().buckets().array().stream()
                    .map(b -> new FacetBucket(b.key(), b.docCount())).toList());
        }
        return new SearchResult(items, total, criteria.page(), criteria.size(), facets);
    }

    public List<Suggestion> suggest(String prefix, int size) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        // The suggest query fetches only `id` and `name` (source filtering). Deserialize into a type that has
        // exactly those fields: mapping the partial _source to ProductDocument fails, because Jackson 3 refuses
        // to put a missing value into a primitive (`long version`, `boolean active`).
        SearchResponse<Suggestion> response = execute(() ->
                client.search(queries.suggest(prefix.strip(), size), Suggestion.class));
        return response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .toList();
    }

    private static ProductHit toHit(Hit<ProductDocument> hit) {
        ProductDocument d = hit.source();
        return new ProductHit(d.id(), d.sku(), d.name(), d.description(), d.category(), d.price(), d.currency(),
                d.attributes(), hit.score(), hit.highlight());
    }

    /** Elasticsearch down -> 503 + Retry-After (handled by the shared ApiExceptionHandler), not a raw 500. */
    private static <T> T execute(EsCall<T> call) {
        try {
            return call.run();
        } catch (IOException e) {
            throw new ServiceUnavailableException("Search is temporarily unavailable", e);
        }
    }

    @FunctionalInterface
    private interface EsCall<T> {
        T run() throws IOException;
    }
}
