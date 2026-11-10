package com.ecommerce.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Creates the physical index (explicit mapping, never dynamic) and points the alias at it.
 * Safe when several replicas start at once: "resource_already_exists" from a concurrent creator is fine.
 */
@Component
public class IndexBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexBootstrap.class);

    private final ElasticsearchClient client;
    private final SearchIndexProperties properties;

    public IndexBootstrap(ElasticsearchClient client, SearchIndexProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (client.indices().existsAlias(a -> a.name(properties.alias())).value()) {
            log.info("Search alias '{}' already exists", properties.alias());
            return;
        }
        try (InputStream definition = new ClassPathResource(properties.definition()).getInputStream()) {
            client.indices().create(c -> c
                    .index(properties.indexName())
                    .withJson(definition)
                    .aliases(properties.alias(), a -> a.isWriteIndex(true)));
            log.info("Created index '{}' with alias '{}'", properties.indexName(), properties.alias());
        } catch (ElasticsearchException e) {
            if (!"resource_already_exists_exception".equals(e.error().type())) {
                throw e;
            }
            log.info("Index '{}' was created concurrently by another replica", properties.indexName());
        }
    }
}
