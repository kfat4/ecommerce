package com.ecommerce.search.backfill;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.loadbalancer.BlockingLoadBalancerInterceptor;
import org.springframework.cloud.client.loadbalancer.DeferringLoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpClientConfig {

    /** Same pattern as order-service: Boot's instrumented RestClient.Builder + Eureka client-side load balancing. */
    @Bean
    ProductCatalogApi productCatalogApi(RestClient.Builder builder,
                                       ObjectProvider<BlockingLoadBalancerInterceptor> loadBalancer) {
        RestClient restClient = builder
                .baseUrl("http://product-service")
                .requestInterceptor(new DeferringLoadBalancerInterceptor(loadBalancer))
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(ProductCatalogApi.class);
    }
}
