package com.ecommerce.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import java.time.Duration;

/**
 * Routing table. {@code lb://service-name} resolves instances from Eureka and load-balances (round robin)
 * across every replica, so scaling a service is just "start another container".
 * <p>
 * Only the public API (/api/v1/**) is routed; /internal/** service-to-service endpoints are not reachable from outside.
 */
@Configuration
public class RoutesConfig {

    @Bean
    RouteLocator routes(RouteLocatorBuilder builder, RedisRateLimiter rateLimiter, KeyResolver keyResolver) {
        return builder.routes()
                .route("auth-service", r -> r.path("/api/v1/auth/**")
                        .filters(f -> resilient(f, "auth-service", rateLimiter, keyResolver))
                        .uri("lb://auth-service"))
                .route("product-service", r -> r.path("/api/v1/products/**")
                        .filters(f -> resilient(f, "product-service", rateLimiter, keyResolver)
                                // Retrying is only safe for idempotent methods (GET). Never blindly retry POST.
                                .retry(c -> c.setRetries(2)
                                        .setMethods(HttpMethod.GET)
                                        .setBackoff(Duration.ofMillis(100), Duration.ofSeconds(1), 2, true)))
                        .uri("lb://product-service"))
                .route("search-service", r -> r.path("/api/v1/search/**")
                        .filters(f -> resilient(f, "search-service", rateLimiter, keyResolver)
                                .retry(c -> c.setRetries(2)
                                        .setMethods(HttpMethod.GET)
                                        .setBackoff(Duration.ofMillis(100), Duration.ofSeconds(1), 2, true)))
                        .uri("lb://search-service"))
                .route("order-service", r -> r.path("/api/v1/orders/**")
                        .filters(f -> resilient(f, "order-service", rateLimiter, keyResolver))
                        .uri("lb://order-service"))
                .route("inventory-service", r -> r.path("/api/v1/inventory/**")
                        .filters(f -> resilient(f, "inventory-service", rateLimiter, keyResolver))
                        .uri("lb://inventory-service"))
                .route("payment-service", r -> r.path("/api/v1/payments/**")
                        .filters(f -> resilient(f, "payment-service", rateLimiter, keyResolver))
                        .uri("lb://payment-service"))
                .route("notification-service", r -> r.path("/api/v1/notifications/**")
                        .filters(f -> resilient(f, "notification-service", rateLimiter, keyResolver))
                        .uri("lb://notification-service"))
                .build();
    }

    private static GatewayFilterSpec resilient(GatewayFilterSpec f, String service, RedisRateLimiter rateLimiter,
                                     KeyResolver keyResolver) {
        return f.requestRateLimiter(c -> c.setRateLimiter(rateLimiter).setKeyResolver(keyResolver))
                .circuitBreaker(c -> c.setName(service).setFallbackUri("forward:/fallback/" + service));
    }
}
