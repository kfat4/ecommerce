package com.ecommerce.common.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ecommerce.outbox")
public record OutboxProperties(
        boolean enabled,
        Integer batchSize,
        Duration sendTimeout,
        Duration retention) {

    public OutboxProperties {
        batchSize = batchSize == null ? 100 : batchSize;
        sendTimeout = sendTimeout == null ? Duration.ofSeconds(10) : sendTimeout;
        retention = retention == null ? Duration.ofDays(7) : retention;
    }
}
