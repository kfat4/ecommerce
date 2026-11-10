package com.ecommerce.product.outbox;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Outbox entry stored in MongoDB, next to the business documents (same database, same transaction).
 * <ul>
 *   <li>{@code availableAt} – not-before timestamp. Used both as the claim lease (another relay instance may take
 *       the message over once it expires) and as the retry back-off after a failed send.</li>
 *   <li>{@code publishedAt} – carries a TTL index: MongoDB deletes published messages by itself after 7 days.</li>
 * </ul>
 */
@Document(collection = "outbox_events")
@CompoundIndex(name = "pending_idx", def = "{'publishedAt': 1, 'availableAt': 1, 'createdAt': 1}")
public class OutboxMessage {

    @Id
    private String id;
    private String aggregateId;
    private String topic;
    private String messageType;
    /** Serialized JSON exactly as it will be sent to Kafka. */
    private String payload;
    private String correlationId;
    private Instant createdAt;
    private Instant availableAt;
    private String lockedBy;
    private int attempts;
    private String lastError;

    @Indexed(name = "published_ttl_idx", expireAfter = "7d")
    private Instant publishedAt;

    protected OutboxMessage() {
    }

    public OutboxMessage(String id, String aggregateId, String topic, String messageType, String payload,
                         String correlationId, Instant createdAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.messageType = messageType;
        this.payload = payload;
        this.correlationId = correlationId;
        this.createdAt = createdAt;
        this.availableAt = createdAt;
    }

    public String getId() { return id; }
    public String getAggregateId() { return aggregateId; }
    public String getTopic() { return topic; }
    public String getMessageType() { return messageType; }
    public String getPayload() { return payload; }
    public String getCorrelationId() { return correlationId; }
    public int getAttempts() { return attempts; }
    public Instant getCreatedAt() { return createdAt; }
}
