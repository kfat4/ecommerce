package com.ecommerce.product.api;

import com.ecommerce.common.web.PageResponse;
import com.ecommerce.product.api.ProductDtos.ProductExport;
import com.ecommerce.product.api.ProductDtos.ProductResponse;
import com.ecommerce.product.application.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Service-to-service API (not routed by the gateway).
 * Consumed by order-service (to price an order) and search-service (to backfill the search index).
 */
@RestController
@RequestMapping("/internal/products")
public class InternalProductController {

    private final ProductService productService;

    public InternalProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    List<ProductResponse> byIds(@RequestParam List<String> ids) {
        return productService.findAll(ids);
    }

    @GetMapping("/export")
    PageResponse<ProductExport> export(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "200") int size) {
        return productService.export(page, size);
    }
}
