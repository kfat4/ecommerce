package com.ecommerce.search.query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class SearchDtos {

    private SearchDtos() {
    }

    public record ProductHit(String id, String sku, String name, String description, String category,
                             BigDecimal price, String currency, Map<String, String> attributes, Double score,
                             Map<String, List<String>> highlights) {
    }

    public record FacetBucket(String key, long count) {
    }

    public record SearchResult(List<ProductHit> items, long total, int page, int size,
                               Map<String, List<FacetBucket>> facets) {
    }

    public record Suggestion(String id, String name) {
    }
}
