package com.ecommerce.common.outbox;

import com.ecommerce.common.contracts.MessageTypes;
import com.ecommerce.common.util.UuidV7;
import com.ecommerce.common.web.CorrelationId;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Transactional Outbox – write side.
 * <p>
 * Instead of calling Kafka directly (dual-write problem: DB commit succeeds but publish fails, or vice versa),
 * the message is INSERTed into {@code outbox_events} in the <b>same local transaction</b> as the business change.
 * {@link OutboxRelay} later ships it to Kafka. {@code Propagation.MANDATORY} makes it impossible to call this
 * method outside a transaction – the atomicity guarantee is enforced by the framework, not by discipline.
 */
public class OutboxPublisher {

    private static final String INSERT_SQL = """
            INSERT INTO outbox_events (id, aggregate_type, aggregate_id, topic, message_type, payload, correlation_id, created_at)
            VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID publish(String topic, String aggregateType, String aggregateId, Object message) {
        UUID id = UuidV7.next();
        jdbcTemplate.update(INSERT_SQL,
                id,
                aggregateType,
                aggregateId,
                topic,
                MessageTypes.nameOf(message),
                objectMapper.writeValueAsString(message),
                MDC.get(CorrelationId.MDC_KEY),
                Timestamp.from(Instant.now()));
        return id;
    }
}
