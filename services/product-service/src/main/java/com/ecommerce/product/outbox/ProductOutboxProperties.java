package com.ecommerce.product.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param batchSize   messages claimed per relay round
 * @param lease       how long a claimed message is reserved for one relay instance before others may retry it
 * @param sendTimeout max wait for the Kafka acknowledgement
 */
@ConfigurationProperties(prefix = "ecommerce.product-outbox")
public record ProductOutboxProperties(int batchSize, Duration lease, Duration sendTimeout) {

    public ProductOutboxProperties {
        batchSize = batchSize <= 0 ? 100 : batchSize;
        lease = lease == null ? Duration.ofSeconds(30) : lease;
        sendTimeout = sendTimeout == null ? Duration.ofSeconds(10) : sendTimeout;
    }
}
