package com.ecommerce.common.inbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Idempotent Consumer (a.k.a. Inbox pattern).
 * <p>
 * Kafka + outbox give at-least-once delivery, so duplicates WILL arrive (redelivery after rebalance, relay retry…).
 * The consumer records the message id in {@code processed_messages} inside the same transaction as its business
 * change. A duplicate hits the primary key, {@code ON CONFLICT DO NOTHING} returns 0 rows and the handler skips it.
 * If the business logic fails, the whole transaction – including this insert – rolls back, so a retry is possible.
 */
public class ProcessedMessageStore {

    private static final String INSERT_SQL = """
            INSERT INTO processed_messages (message_id, consumer_group, processed_at)
            VALUES (?, ?, ?)
            ON CONFLICT DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    public ProcessedMessageStore(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;
    }

    /** @return {@code true} if this is the first time the message is seen and processing should continue. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(String messageId, String consumerGroup) {
        boolean firstTime = jdbcTemplate.update(INSERT_SQL, messageId, consumerGroup, Timestamp.from(Instant.now())) == 1;
        if (!firstTime) {
            // A steady trickle is normal (at-least-once); a spike points at rebalances or a relay re-sending.
            Counter.builder("ecommerce.inbox.duplicates")
                    .description("Messages skipped because they were already processed")
                    .tag("consumer", consumerGroup)
                    .register(meterRegistry)
                    .increment();
        }
        return firstTime;
    }
}
