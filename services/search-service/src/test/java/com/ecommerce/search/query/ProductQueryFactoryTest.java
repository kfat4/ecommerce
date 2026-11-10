package com.ecommerce.search.query;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.json.JsonpUtils;
import co.elastic.clients.json.jackson.Jackson3JsonpMapper;
import com.ecommerce.common.web.BusinessException;
import com.ecommerce.search.index.SearchIndexProperties;
import com.ecommerce.search.query.ProductSearchCriteria.SortOption;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the generated Elasticsearch query DSL without a running cluster. */
class ProductQueryFactoryTest {

    private final ProductQueryFactory factory =
            new ProductQueryFactory(new SearchIndexProperties("products", "products-v1", null));

    @Test
    void fullTextSearchIsFuzzyAndFiltersAreNotScored() {
        JsonNode body = json(factory.search(new ProductSearchCriteria("laptpo", "electronics",
                new BigDecimal("100"), new BigDecimal("2000"), SortOption.PRICE_ASC, 2, 10)));

        JsonNode bool = body.at("/query/bool");
        JsonNode multiMatch = bool.at("/must/0/multi_match");
        assertThat(multiMatch.at("/query").asString()).isEqualTo("laptpo");
        assertThat(multiMatch.at("/fuzziness").asString()).isEqualTo("AUTO");
        assertThat(multiMatch.at("/operator").asString()).isEqualToIgnoringCase("and");

        assertThat(bool.at("/filter").toString())
                .contains("\"active\"")
                .contains("\"electronics\"")
                .contains("\"price\"");

        assertThat(body.at("/from").asInt()).isEqualTo(20);
        assertThat(body.at("/size").asInt()).isEqualTo(10);
        assertThat(body.at("/sort/0").toString()).contains("price").containsIgnoringCase("asc");
        assertThat(body.at("/sort/1").toString()).contains("id");
        assertThat(body.at("/aggregations").has(ProductQueryFactory.CATEGORY_FACET)).isTrue();
        assertThat(body.at("/aggregations").has(ProductQueryFactory.PRICE_FACET)).isTrue();
    }

    @Test
    void emptyQueryMatchesAllVisibleProducts() {
        JsonNode body = json(factory.search(new ProductSearchCriteria(" ", null, null, null, null, 0, 20)));

        assertThat(body.at("/query/bool/must/0").has("match_all")).isTrue();
        assertThat(body.at("/query/bool/filter").size()).isEqualTo(1); // only the visibility filter
        assertThat(body.at("/sort/0").has("_score")).isTrue();
    }

    @Test
    void deepPagingBeyondResultWindowIsRejected() {
        var criteria = new ProductSearchCriteria("x", null, null, null, null, 100, 100);

        assertThatThrownBy(() -> factory.search(criteria)).isInstanceOf(BusinessException.class);
    }

    @Test
    void suggestUsesSearchAsYouTypeSubfields() {
        JsonNode body = json(factory.suggest("noi", 5));

        JsonNode multiMatch = body.at("/query/bool/must/0/multi_match");
        assertThat(multiMatch.at("/type").asString()).isEqualTo("bool_prefix");
        assertThat(multiMatch.at("/fields").toString()).contains("name.suggest._2gram");
    }

    private static JsonNode json(SearchRequest request) {
        String rendered = JsonpUtils.toString(request, new Jackson3JsonpMapper(), new StringBuilder()).toString();
        return JsonMapper.builder().build().readTree(rendered);
    }
}
