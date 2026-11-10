package com.ecommerce.gateway;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.time.Duration;
import java.util.Objects;

@Configuration
public class ResilienceConfig {

    /**
     * Token bucket in Redis: 20 requests/second steady, bursts up to 40. Because the bucket lives in Redis,
     * the limit is enforced globally across all gateway replicas, not per instance.
     */
    @Bean
    @Primary
    RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(20, 40, 1);
    }

    /** Rate-limit per authenticated user; anonymous traffic is limited per client IP. */
    @Bean
    @Primary
    KeyResolver userOrIpKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    var address = exchange.getRequest().getRemoteAddress();
                    return "ip:" + (address == null ? "unknown" : Objects.toString(address.getAddress()));
                }));
    }

    /**
     * Circuit breaker per downstream service: when >50% of the last 20 calls fail (or are slower than 3s),
     * the circuit opens for 10s and callers immediately get the fallback instead of piling up on a sick service.
     */
    @Bean
    Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCircuitBreaker() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(10)
                        .failureRateThreshold(50)
                        .slowCallDurationThreshold(Duration.ofSeconds(3))
                        .slowCallRateThreshold(80)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .permittedNumberOfCallsInHalfOpenState(5)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(8)).build())
                .build());
    }
}
