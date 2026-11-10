package com.ecommerce.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Search read model (CQRS query side).
 * <p>
 * product-service (MongoDB) is the source of truth. Every change is published as a {@code ProductEvent}
 * (transactional outbox -> Kafka {@code product.events}); this service projects those events into Elasticsearch
 * and serves full-text search, facets and autocomplete. The index is <b>disposable</b>: it can always be rebuilt
 * from the compacted Kafka topic or from product-service's export API.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
