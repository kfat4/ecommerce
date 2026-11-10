package com.ecommerce.search.query;

import java.math.BigDecimal;

/**
 * @param text     free text; typo-tolerant (fuzzy) over name, description, category and SKU
 * @param category exact category filter
 * @param minPrice inclusive lower bound
 * @param maxPrice inclusive upper bound
 */
public record ProductSearchCriteria(String text, String category, BigDecimal minPrice, BigDecimal maxPrice,
                                    SortOption sort, int page, int size) {

    /** Elasticsearch's default index.max_result_window. Deeper paging needs search_after (cursor), not from/size. */
    public static final int MAX_RESULT_WINDOW = 10_000;

    public enum SortOption { RELEVANCE, PRICE_ASC, PRICE_DESC, NEWEST }

    public ProductSearchCriteria {
        text = text == null || text.isBlank() ? null : text.strip();
        category = category == null || category.isBlank() ? null : category.strip();
        sort = sort == null ? SortOption.RELEVANCE : sort;
        page = Math.max(page, 0);
        size = Math.clamp(size, 1, 100);
    }

    public boolean hasText() {
        return text != null;
    }

    public int from() {
        return page * size;
    }
}
