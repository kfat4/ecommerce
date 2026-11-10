package com.ecommerce.product.outbox;

import com.ecommerce.common.messaging.MessageHeaders;
import com.ecommerce.common.observability.OutboxMetrics;
import com.ecommerce.common.util.UuidV7;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Transactional Outbox for MongoDB – relay side.
 * <p>
 * MongoDB has no {@code SELECT ... FOR UPDATE SKIP LOCKED}. The equivalent is an atomic <b>claim</b>:
 * {@code findAndModify} picks one pending message and moves its {@code availableAt} into the future (a lease) in a
 * single atomic operation, so two replicas can never claim the same message at the same time. If a replica dies
 * mid-send, the lease expires and another replica picks the message up again (at-least-once delivery).
 * <p>
 * Ordering note: two replicas may publish two versions of the same product in the "wrong" order.
 * That is acceptable here because every {@code ProductEvent} carries the aggregate version and the consumer
 * ignores anything older than what it already applied (see search-service).
 */
@Component
public class MongoOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(MongoOutboxRelay.class);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    private final MongoTemplate mongoTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ProductOutboxProperties properties;
    private final OutboxMetrics metrics;
    private final String instanceId = UuidV7.next().toString();

    public MongoOutboxRelay(MongoTemplate mongoTemplate, KafkaTemplate<String, String> kafkaTemplate,
                            ProductOutboxProperties properties, MeterRegistry meterRegistry) {
        this.mongoTemplate = mongoTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.metrics = new OutboxMetrics(meterRegistry, "mongo");
    }

    @Scheduled(fixedDelayString = "${ecommerce.product-outbox.poll-interval-ms:500}")
    public void relay() {
        List<OutboxMessage> batch;
        do {
            batch = claimBatch();
            publish(batch);
        } while (batch.size() == properties.batchSize());
    }

    /** Same gauges as the PostgreSQL relay (store="mongo"), so one dashboard/alert covers both. */
    @Scheduled(fixedDelayString = "${ecommerce.product-outbox.metrics-interval-ms:15000}")
    public void refreshBacklogMetrics() {
        Query pending = new Query(where("publishedAt").is(null));
        long count = mongoTemplate.count(pending, OutboxMessage.class);
        OutboxMessage oldest = count == 0 ? null
                : mongoTemplate.findOne(pending.with(Sort.by("createdAt")).limit(1), OutboxMessage.class);
        metrics.backlog(count, oldest == null ? null : oldest.getCreatedAt());
    }

    private List<OutboxMessage> claimBatch() {
        List<OutboxMessage> claimed = new ArrayList<>();
        while (claimed.size() < properties.batchSize()) {
            Instant now = Instant.now();
            Query pending = new Query(where("publishedAt").is(null).and("availableAt").lte(now))
                    .with(Sort.by("createdAt"));
            Update lease = new Update()
                    .set("availableAt", now.plus(properties.lease()))
                    .set("lockedBy", instanceId);
            OutboxMessage message = mongoTemplate.findAndModify(pending, lease,
                    FindAndModifyOptions.options().returnNew(true), OutboxMessage.class);
            if (message == null) {
                break;
            }
            claimed.add(message);
        }
        return claimed;
    }

    private void publish(List<OutboxMessage> batch) {
        if (batch.isEmpty()) {
            return;
        }
        List<CompletableFuture<SendResult<String, String>>> futures = new ArrayList<>(batch.size());
        for (OutboxMessage message : batch) {
            futures.add(kafkaTemplate.send(toRecord(message)));
        }
        for (int i = 0; i < batch.size(); i++) {
            OutboxMessage message = batch.get(i);
            try {
                futures.get(i).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                // Only the lease owner may complete it (guards against a takeover after an expired lease).
                mongoTemplate.updateFirst(
                        new Query(where("_id").is(message.getId()).and("lockedBy").is(instanceId)),
                        new Update().set("publishedAt", Instant.now()),
                        OutboxMessage.class);
                metrics.published(message.getTopic());
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.warn("Outbox publish failed id={} topic={} attempt={} : {}",
                        message.getId(), message.getTopic(), message.getAttempts() + 1, e.getMessage());
                mongoTemplate.updateFirst(
                        new Query(where("_id").is(message.getId())),
                        new Update().inc("attempts", 1)
                                .set("lastError", truncate(e.getMessage()))
                                .set("availableAt", Instant.now().plus(backoff(message.getAttempts() + 1))),
                        OutboxMessage.class);
                metrics.failed(message.getTopic());
            }
        }
    }

    /** Exponential back-off: 1s, 2s, 4s ... capped at 5 minutes. */
    static Duration backoff(int attempts) {
        long seconds = 1L << Math.min(attempts - 1, 16);
        Duration delay = Duration.ofSeconds(seconds);
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    private static ProducerRecord<String, String> toRecord(OutboxMessage message) {
        // Key = productId: all events of one product land in the same partition (and survive log compaction).
        var record = new ProducerRecord<>(message.getTopic(), message.getAggregateId(), message.getPayload());
        record.headers().add(MessageHeaders.MESSAGE_ID, message.getId().getBytes(StandardCharsets.UTF_8));
        record.headers().add(MessageHeaders.MESSAGE_TYPE, message.getMessageType().getBytes(StandardCharsets.UTF_8));
        if (message.getCorrelationId() != null) {
            record.headers().add(MessageHeaders.CORRELATION_ID,
                    message.getCorrelationId().getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    private static String truncate(String text) {
        return text == null || text.length() <= 500 ? text : text.substring(0, 500);
    }
}
