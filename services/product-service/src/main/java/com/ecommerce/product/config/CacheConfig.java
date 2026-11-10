package com.ecommerce.product.config;

import com.ecommerce.product.api.ProductDtos.ProductResponse;
import com.ecommerce.product.application.ProductService;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

@Configuration
public class CacheConfig {

    /**
     * JSON values (readable with redis-cli, language neutral) bound to a concrete type.
     * We deliberately avoid "default typing" (class names inside JSON) – it is a known deserialization attack vector.
     * TTL bounds staleness even if an eviction is ever missed.
     * transactionAware(): puts/evicts are deferred until the MongoDB transaction commits, so a rolled-back
     * update can never leave its value in the cache.
     */
    @Bean
    RedisCacheManagerBuilderCustomizer productCacheCustomizer() {
        return builder -> builder.transactionAware().withCacheConfiguration(ProductService.CACHE,
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(10))
                        .disableCachingNullValues()
                        .prefixCacheNameWith("product-service:")
                        .serializeValuesWith(SerializationPair.fromSerializer(
                                new JacksonJsonRedisSerializer<>(ProductResponse.class))));
    }
}
