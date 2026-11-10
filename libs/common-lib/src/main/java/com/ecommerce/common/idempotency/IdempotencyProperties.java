package com.ecommerce.common.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * @param paths     path patterns (e.g. {@code /api/v1/orders}) whose POST/PUT/PATCH requests require an Idempotency-Key
 * @param ttl       how long a completed response is remembered and replayed
 * @param lockTtl   how long an in-flight request holds the key (protects against crashed instances keeping it forever)
 */
@ConfigurationProperties(prefix = "ecommerce.idempotency")
public record IdempotencyProperties(boolean enabled, List<String> paths, Duration ttl, Duration lockTtl) {

    public IdempotencyProperties {
        paths = paths == null ? List.of() : List.copyOf(paths);
        ttl = ttl == null ? Duration.ofHours(24) : ttl;
        lockTtl = lockTtl == null ? Duration.ofSeconds(30) : lockTtl;
    }
}
