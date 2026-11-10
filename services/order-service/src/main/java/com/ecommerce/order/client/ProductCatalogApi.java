package com.ecommerce.order.client;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.math.BigDecimal;
import java.util.List;

/** Declarative HTTP client (Spring HTTP Interface) for product-service's internal API. */
@HttpExchange("/internal/products")
public interface ProductCatalogApi {

    @GetExchange
    List<ProductSnapshot> findByIds(@RequestParam("ids") List<String> ids);

    /** Consumer-side DTO: only the fields we need (tolerant reader – unknown fields are ignored). */
    record ProductSnapshot(String id, String name, BigDecimal price, String currency, boolean active) {
    }
}
