package com.ecommerce.common.outbox;

import com.ecommerce.common.messaging.MessageHeaders;
import com.ecommerce.common.observability.OutboxMetrics;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Transactional Outbox – relay side (polling publisher).
 * <p>
 * Horizontal scaling: every instance of a service runs this relay. {@code FOR UPDATE SKIP LOCKED}
 * lets instances work on disjoint batches without blocking each other and without a leader election.
 * <p>
 * Delivery guarantee is <b>at-least-once</b>: if the process dies after Kafka acknowledged a record but before
 * {@code published_at} is committed, the record is sent again. That is why every consumer is idempotent
 * (see {@code ProcessedMessageStore}).
 * <p>
 * Production alternative: Change Data Capture (Debezium) tails the WAL instead of polling – see ADR-0003.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private static final String SELECT_BATCH_SQL = """
            SELECT id, aggregate_id, topic, message_type, payload::text AS payload, correlation_id
            FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """;
    private static final String MARK_PUBLISHED_SQL = "UPDATE outbox_events SET published_at = ? WHERE id = ?";
    private static final String MARK_FAILED_SQL =
            "UPDATE outbox_events SET attempts = attempts + 1, last_error = ? WHERE id = ?";
    private static final String CLEANUP_SQL = "DELETE FROM outbox_events WHERE published_at < ?";
    private static final String BACKLOG_SQL =
            "SELECT count(*) AS pending, min(created_at) AS oldest FROM outbox_events WHERE published_at IS NULL";

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final OutboxProperties properties;
    private final OutboxMetrics metrics;

    public OutboxRelay(JdbcTemplate jdbcTemplate, KafkaTemplate<String, String> kafkaTemplate,
                       TransactionTemplate transactionTemplate, OutboxProperties properties, OutboxMetrics metrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${ecommerce.outbox.poll-interval-ms:500}")
    public void relay() {
        Integer processed;
        do {
            processed = transactionTemplate.execute(status -> relayBatch());
        } while (processed != null && processed == properties.batchSize());
    }

    @Scheduled(cron = "${ecommerce.outbox.cleanup-cron:0 0 * * * *}")
    public void cleanup() {
        int deleted = jdbcTemplate.update(CLEANUP_SQL, Timestamp.from(Instant.now().minus(properties.retention())));
        if (deleted > 0) {
            log.info("Outbox cleanup removed {} published messages", deleted);
        }
    }

    /** Feeds the backlog gauges; a growing backlog means Kafka is unreachable or the relay cannot keep up. */
    @Scheduled(fixedDelayString = "${ecommerce.outbox.metrics-interval-ms:15000}")
    public void refreshBacklogMetrics() {
        jdbcTemplate.query(BACKLOG_SQL, rs -> {
            Timestamp oldest = rs.getTimestamp("oldest");
            metrics.backlog(rs.getLong("pending"), oldest == null ? null : oldest.toInstant());
        });
    }

    private int relayBatch() {
        List<OutboxRow> rows = jdbcTemplate.query(SELECT_BATCH_SQL, (rs, i) -> new OutboxRow(
                rs.getObject("id", UUID.class),
                rs.getString("aggregate_id"),
                rs.getString("topic"),
                rs.getString("message_type"),
                rs.getString("payload"),
                rs.getString("correlation_id")), properties.batchSize());
        if (rows.isEmpty()) {
            return 0;
        }

        // Send asynchronously (the producer batches them), then wait for every broker acknowledgement.
        List<CompletableFuture<SendResult<String, String>>> futures = new ArrayList<>(rows.size());
        for (OutboxRow row : rows) {
            futures.add(kafkaTemplate.send(toRecord(row)));
        }

        Timestamp now = Timestamp.from(Instant.now());
        for (int i = 0; i < rows.size(); i++) {
            OutboxRow row = rows.get(i);
            try {
                futures.get(i).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                jdbcTemplate.update(MARK_PUBLISHED_SQL, now, row.id());
                metrics.published(row.topic());
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.warn("Outbox publish failed id={} topic={} : {}", row.id(), row.topic(), e.getMessage());
                jdbcTemplate.update(MARK_FAILED_SQL, truncate(e.getMessage()), row.id());
                metrics.failed(row.topic());
            }
        }
        return rows.size();
    }

    private static ProducerRecord<String, String> toRecord(OutboxRow row) {
        var record = new ProducerRecord<>(row.topic(), row.aggregateId(), row.payload());
        record.headers().add(MessageHeaders.MESSAGE_ID, row.id().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(MessageHeaders.MESSAGE_TYPE, row.messageType().getBytes(StandardCharsets.UTF_8));
        if (row.correlationId() != null) {
            record.headers().add(MessageHeaders.CORRELATION_ID, row.correlationId().getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private record OutboxRow(UUID id, String aggregateId, String topic, String messageType, String payload,
                             String correlationId) {
    }
}
