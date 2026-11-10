package com.ecommerce.search.index;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param alias      name used by readers and writers. It never changes.
 * @param indexName  physical index behind the alias. A mapping change means: create {@code products-v2},
 *                   fill it, then atomically move the alias (zero-downtime reindex). See docs/adr.
 * @param definition classpath resource with settings + mappings of {@code indexName}
 */
@ConfigurationProperties(prefix = "ecommerce.search.index")
public record SearchIndexProperties(String alias, String indexName, String definition) {

    public SearchIndexProperties {
        alias = alias == null ? "products" : alias;
        indexName = indexName == null ? "products-v1" : indexName;
        definition = definition == null ? "elasticsearch/products-v1.json" : definition;
    }
}
