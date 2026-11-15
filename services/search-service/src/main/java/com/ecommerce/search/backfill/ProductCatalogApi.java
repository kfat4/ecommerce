package com.ecommerce.search.backfill;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Consumer-side view of product-service's internal export API (only the fields this service needs). */
@HttpExchange("/internal/products")
public interface ProductCatalogApi {

    @GetExchange("/export")
    ExportPage export(@RequestParam int page, @RequestParam int size);

    /**
     * Own DTO instead of common-lib's PageResponse: that record has a factory taking Spring Data's {@code Page},
     * and Jackson's introspection of it fails with NoClassDefFoundError in a service without Spring Data.
     * A consumer should not depend on the provider's classes anyway; unknown fields are ignored.
     */
    record ExportPage(List<ExportedProduct> content, int totalPages) {
    }

    record ExportedProduct(String id, long version, String sku, String name, String description, String category,
                           BigDecimal price, String currency, Map<String, String> attributes, boolean active,
                           boolean deleted) {
    }
}
