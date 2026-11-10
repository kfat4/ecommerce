package com.ecommerce.order.client;

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

    /**
     * Starts from Boot's auto-configured RestClient.Builder (keeps tracing/metrics instrumentation and the
     * spring.http.clients.* timeouts) and adds Spring Cloud's load-balancer interceptor, which resolves the
     * logical host "product-service" to a concrete replica from Eureka on every call.
     */
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
