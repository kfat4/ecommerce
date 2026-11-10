package com.ecommerce.common.idempotency;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

/** Opt-in with {@code ecommerce.idempotency.enabled=true}. */
@AutoConfiguration(afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({StringRedisTemplate.class, MeterRegistry.class})
@ConditionalOnProperty(prefix = "ecommerce.idempotency", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    IdempotencyStore idempotencyStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        return new RedisIdempotencyStore(redis, objectMapper);
    }

    @Bean
    FilterRegistrationBean<IdempotencyFilter> idempotencyFilter(IdempotencyStore store, IdempotencyProperties properties,
                                                                @Value("${spring.application.name}") String serviceName,
                                                                ObjectProvider<MeterRegistry> meterRegistry) {
        var registration = new FilterRegistrationBean<>(new IdempotencyFilter(store, properties, serviceName,
                meterRegistry.getIfAvailable(SimpleMeterRegistry::new)));
        // Must run AFTER Spring Security (order -100) so the authenticated principal is known.
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 100);
        return registration;
    }
}
