package com.ecommerce.notification;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * _id = Kafka message id → natural idempotency: inserting the same message twice fails with a duplicate key,
 * no separate inbox collection needed.
 */
@Document(collection = "notifications")
public record Notification(
        @Id String id,
        @Indexed UUID customerId,
        UUID orderId,
        String channel,
        String subject,
        String body,
        Instant createdAt) {
}
