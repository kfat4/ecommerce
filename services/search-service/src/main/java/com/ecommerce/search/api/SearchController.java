package com.ecommerce.search.api;

import com.ecommerce.search.query.ProductSearchCriteria;
import com.ecommerce.search.query.ProductSearchCriteria.SortOption;
import com.ecommerce.search.query.ProductSearchService;
import com.ecommerce.search.query.SearchDtos.SearchResult;
import com.ecommerce.search.query.SearchDtos.Suggestion;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Public, read-only search API. Examples:
 * <pre>
 * GET /api/v1/search/products?q=laptpo                       (typo tolerant)
 * GET /api/v1/search/products?q=kulaklik&amp;sort=PRICE_ASC     (Turkish-insensitive: ı/i, ş/s ...)
 * GET /api/v1/search/products?category=electronics&amp;maxPrice=500
 * GET /api/v1/search/products/suggest?q=noi
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/search/products")
public class SearchController {

    private final ProductSearchService searchService;

    public SearchController(ProductSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    ResponseEntity<SearchResult> search(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) BigDecimal minPrice,
                                        @RequestParam(required = false) BigDecimal maxPrice,
                                        @RequestParam(defaultValue = "RELEVANCE") SortOption sort,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        var criteria = new ProductSearchCriteria(q, category, minPrice, maxPrice, sort, page, size);
        // The index is eventually consistent anyway; a short shared cache costs nothing in correctness.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(10)).cachePublic())
                .body(searchService.search(criteria));
    }

    @GetMapping("/suggest")
    List<Suggestion> suggest(@RequestParam("q") String prefix, @RequestParam(defaultValue = "5") int size) {
        return searchService.suggest(prefix, size);
    }
}
