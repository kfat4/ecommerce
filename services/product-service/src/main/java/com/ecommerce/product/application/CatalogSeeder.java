package com.ecommerce.product.application;

import com.ecommerce.product.domain.Product;
import com.ecommerce.product.domain.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Demo catalogue. IDs are fixed so inventory-service (seeded by Flyway) refers to the same products.
 * insert + DuplicateKeyException makes it safe when several replicas start simultaneously.
 * Seeding goes through {@link ProductService#seed} so a ProductUpsertedEvent is published and the products
 * also show up in the search index.
 */
@Component
@ConditionalOnProperty(name = "ecommerce.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class CatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogSeeder.class);

    private final ProductRepository repository;
    private final ProductService productService;

    public CatalogSeeder(ProductRepository repository, ProductService productService) {
        this.repository = repository;
        this.productService = productService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Product> products = List.of(
                new Product("PRD-1001", "LAPTOP-PRO-14", "Laptop Pro 14", "14\" developer laptop", "electronics",
                        new BigDecimal("1499.00"), "EUR", Map.of("cpu", "12-core", "ram", "32GB")),
                new Product("PRD-1002", "HEADPHONES-NC", "Noise Cancelling Headphones", "Over-ear, ANC", "electronics",
                        new BigDecimal("249.90"), "EUR", Map.of("battery", "30h")),
                new Product("PRD-1003", "TSHIRT-BASIC", "Basic T-Shirt", "100% organic cotton", "fashion",
                        new BigDecimal("19.99"), "EUR", Map.of("size", "M", "colour", "black")),
                new Product("PRD-1004", "BOOK-DDIA", "Designing Data-Intensive Applications", "Must-read for architects",
                        "books", new BigDecimal("45.00"), "EUR", Map.of("format", "paperback")),
                new Product("PRD-1005", "WATCH-LUX", "Luxury Watch", "Use it to trigger a payment decline (> 10 000)",
                        "accessories", new BigDecimal("12500.00"), "EUR", Map.of("material", "gold")));
        int created = 0;
        for (Product product : products) {
            if (repository.existsById(product.getId())) {
                continue;
            }
            try {
                productService.seed(product);
                created++;
            } catch (DuplicateKeyException raceWithOtherReplica) {
                // another instance inserted it first – fine
            }
        }
        if (created > 0) {
            log.info("Seeded {} demo products", created);
        }
    }
}
