package com.ecommerce.search.query;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.util.NamedValue;
import com.ecommerce.common.web.BusinessException;
import com.ecommerce.search.index.SearchIndexProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the Elasticsearch requests. Kept free of I/O so the query shape can be unit tested.
 * <p>
 * Relevance vs. filtering: free text goes into {@code must} (affects the score); exact constraints go into
 * {@code filter} (yes/no, not scored, cached by Elasticsearch).
 */
@Component
public class ProductQueryFactory {

    public static final String CATEGORY_FACET = "categories";
    public static final String PRICE_FACET = "price_ranges";

    private final SearchIndexProperties properties;

    public ProductQueryFactory(SearchIndexProperties properties) {
        this.properties = properties;
    }

    public SearchRequest search(ProductSearchCriteria criteria) {
        if (criteria.from() + criteria.size() > ProductSearchCriteria.MAX_RESULT_WINDOW) {
            throw new BusinessException("result-window-too-large",
                    "Paging is limited to the first %d results; refine the search instead."
                            .formatted(ProductSearchCriteria.MAX_RESULT_WINDOW));
        }
        return SearchRequest.of(s -> s
                .index(properties.alias())
                .query(buildQuery(criteria))
                .from(criteria.from())
                .size(criteria.size())
                .sort(sortOptions(criteria))
                .trackTotalHits(t -> t.enabled(true))
                .highlight(h -> h
                        .preTags("<em>").postTags("</em>")
                        .fields(NamedValue.of("name", HighlightField.of(f -> f)),
                                NamedValue.of("description", HighlightField.of(f -> f))))
                .aggregations(CATEGORY_FACET, a -> a.terms(t -> t.field("category").size(20)))
                .aggregations(PRICE_FACET, a -> a.range(r -> r
                        .field("price")
                        .ranges(x -> x.key("0-50").to(50.0))
                        .ranges(x -> x.key("50-250").from(50.0).to(250.0))
                        .ranges(x -> x.key("250-1000").from(250.0).to(1000.0))
                        .ranges(x -> x.key("1000+").from(1000.0)))));
    }

    /** Autocomplete over the search_as_you_type sub-fields (edge n-grams + shingles). */
    public SearchRequest suggest(String prefix, int size) {
        return SearchRequest.of(s -> s
                .index(properties.alias())
                .size(Math.clamp(size, 1, 10))
                .source(src -> src.filter(f -> f.includes("id", "name")))
                .query(q -> q.bool(b -> b
                        .must(m -> m.multiMatch(mm -> mm
                                .query(prefix)
                                .type(TextQueryType.BoolPrefix)
                                .fields("name.suggest", "name.suggest._2gram", "name.suggest._3gram")))
                        .filter(onlyVisible()))));
    }

    private Query buildQuery(ProductSearchCriteria c) {
        return Query.of(q -> q.bool(b -> {
            if (c.hasText()) {
                b.must(m -> m.multiMatch(mm -> mm
                        .query(c.text())
                        .fields("name^3", "sku^5", "category^2", "description")
                        .type(TextQueryType.BestFields)
                        .operator(Operator.And)
                        .fuzziness("AUTO")          // "laptpo" still finds "laptop"
                        .prefixLength(1)            // first letter must match: faster, fewer odd hits
                        .lenient(true)));
            } else {
                b.must(m -> m.matchAll(all -> all));
            }
            b.filter(onlyVisible());
            if (c.category() != null) {
                b.filter(f -> f.term(t -> t.field("category").value(c.category())));
            }
            if (c.minPrice() != null || c.maxPrice() != null) {
                b.filter(f -> f.range(r -> r.number(n -> {
                    n.field("price");
                    if (c.minPrice() != null) {
                        n.gte(c.minPrice().doubleValue());
                    }
                    if (c.maxPrice() != null) {
                        n.lte(c.maxPrice().doubleValue());
                    }
                    return n;
                })));
            }
            return b;
        }));
    }

    /** Inactive products and deletion tombstones are never returned. */
    private static Query onlyVisible() {
        return Query.of(q -> q.term(t -> t.field("active").value(true)));
    }

    private static List<SortOptions> sortOptions(ProductSearchCriteria c) {
        List<SortOptions> sort = new ArrayList<>();
        switch (c.sort()) {
            case PRICE_ASC -> sort.add(SortOptions.of(s -> s.field(f -> f.field("price").order(SortOrder.Asc))));
            case PRICE_DESC -> sort.add(SortOptions.of(s -> s.field(f -> f.field("price").order(SortOrder.Desc))));
            case NEWEST -> sort.add(SortOptions.of(s -> s.field(f -> f.field("updatedAt").order(SortOrder.Desc))));
            case RELEVANCE -> sort.add(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))));
        }
        // Deterministic tie-breaker: stable paging when scores/prices are equal.
        sort.add(SortOptions.of(s -> s.field(f -> f.field("id").order(SortOrder.Asc))));
        return sort;
    }
}
