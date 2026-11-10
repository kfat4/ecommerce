package com.ecommerce.order.saga;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ecommerce.saga")
public record SagaProperties(Duration stepTimeout, int maxPaymentRetries) {

    public SagaProperties {
        stepTimeout = stepTimeout == null ? Duration.ofSeconds(30) : stepTimeout;
        maxPaymentRetries = maxPaymentRetries <= 0 ? 3 : maxPaymentRetries;
    }
}
