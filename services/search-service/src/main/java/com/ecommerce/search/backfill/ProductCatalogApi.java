package com.ecommerce.search.backfill;

import com.ecommerce.common.web.PageResponse;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.math.BigDecimal;
import java.util.Map;

/** Consumer-side view of product-service's internal export API (only the fields this service needs). */
@HttpExchange("/internal/products")
public interface ProductCatalogApi {

    @GetExchange("/export")
    PageResponse<ExportedProduct> export(@RequestParam int page, @RequestParam int size);

    record ExportedProduct(String id, long version, String sku, String name, String description, String category,
                           BigDecimal price, String currency, Map<String, String> attributes, boolean active,
                           boolean deleted) {
    }
}
