package com.ecommerce.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.Jackson3JsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.ecommerce.search.index.ProductIndex.WriteResult;
import com.ecommerce.search.query.ProductQueryFactory;
import com.ecommerce.search.query.ProductSearchCriteria;
import com.ecommerce.search.query.ProductSearchService;
import com.ecommerce.search.query.SearchDtos.SearchResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against a real Elasticsearch (Docker). Excluded from {@code mvn test} by naming (*IT);
 * run with {@code mvn -pl services/search-service verify -Dsurefire.includes='**\/*IT.java'} or from the IDE.
 */
@Testcontainers(disabledWithoutDocker = true)
class ElasticsearchProductIndexIT {

    @Container
    static final ElasticsearchContainer ES =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:9.2.8")
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    static Rest5Client restClient;
    static ElasticsearchClient client;
    static ElasticsearchProductIndex index;
    static ProductSearchService search;

    @BeforeAll
    static void setUp() throws Exception {
        restClient = Rest5Client.builder(URI.create("http://" + ES.getHttpHostAddress())).build();
        client = new ElasticsearchClient(new Rest5ClientTransport(restClient, new Jackson3JsonpMapper()));
        var properties = new SearchIndexProperties(null, null, null);
        new IndexBootstrap(client, properties).run(null);
        new IndexBootstrap(client, properties).run(null); // second replica starting: must be a no-op
        index = new ElasticsearchProductIndex(client, properties);
        search = new ProductSearchService(client, new ProductQueryFactory(properties));
    }

    @AfterAll
    static void tearDown() throws Exception {
        restClient.close();
    }

    @Test
    void externalVersioningRejectsDuplicatesAndOlderVersions() {
        assertThat(index.upsert(doc("PRD-1", 3, "Laptop Pro 14", "1499.00"))).isEqualTo(WriteResult.APPLIED);
        assertThat(index.upsert(doc("PRD-1", 3, "Laptop Pro 14", "1499.00"))).isEqualTo(WriteResult.STALE);
        assertThat(index.upsert(doc("PRD-1", 2, "Old name", "999.00"))).isEqualTo(WriteResult.STALE);
        assertThat(index.upsert(doc("PRD-1", 4, "Laptop Pro 14 (2026)", "1399.00"))).isEqualTo(WriteResult.APPLIED);
    }

    @Test
    void fuzzyAndTurkishInsensitiveSearch() throws Exception {
        index.upsert(doc("PRD-2", 0, "Kablosuz Kulaklık", "249.90"));
        index.upsert(doc("PRD-3", 0, "Şık Deri Çanta", "89.00"));
        client.indices().refresh(r -> r.index("products"));

        assertThat(names(search.search(criteria("kulaklik")))).contains("Kablosuz Kulaklık");   // ı -> i folding
        assertThat(names(search.search(criteria("kulaklık")))).contains("Kablosuz Kulaklık");
        assertThat(names(search.search(criteria("kablsuz")))).contains("Kablosuz Kulaklık");     // typo
        assertThat(names(search.search(criteria("canta")))).contains("Şık Deri Çanta");         // ç -> c
        assertThat(search.suggest("kab", 5)).extracting("name").contains("Kablosuz Kulaklık");
    }

    private static ProductSearchCriteria criteria(String text) {
        return new ProductSearchCriteria(text, null, null, null, null, 0, 10);
    }

    private static java.util.List<String> names(SearchResult result) {
        return result.items().stream().map(h -> h.name()).toList();
    }

    private static ProductDocument doc(String id, long version, String name, String price) {
        return new ProductDocument(id, version, id + "-SKU", name, "test product", "electronics",
                new BigDecimal(price), "EUR", Map.of("k", "v"), true, false, Instant.now());
    }
}
