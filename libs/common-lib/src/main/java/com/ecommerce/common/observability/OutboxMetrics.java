package com.ecommerce.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics shared by every outbox relay (PostgreSQL and MongoDB):
 * <ul>
 *   <li>{@code ecommerce_outbox_published_total{topic}} / {@code ecommerce_outbox_failed_total{topic}}</li>
 *   <li>{@code ecommerce_outbox_pending} – messages written but not yet on Kafka</li>
 *   <li>{@code ecommerce_outbox_oldest_age_seconds} – how long the oldest pending message has been waiting</li>
 * </ul>
 * The backlog gauges are refreshed by the relay on a schedule instead of querying the database on every scrape.
 * Every replica reports the same backlog (it is a property of the shared table), so dashboards use {@code max()}.
 */
public final class OutboxMetrics {

    private final MeterRegistry registry;
    private final String store;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();

    public OutboxMetrics(MeterRegistry registry, String store) {
        this.registry = registry;
        this.store = store;
        Gauge.builder("ecommerce.outbox.pending", pending, AtomicLong::get)
                .description("Outbox messages not yet published to Kafka")
                .tag("store", store)
                .register(registry);
        Gauge.builder("ecommerce.outbox.oldest.age", oldestAgeSeconds, AtomicLong::get)
                .description("Age of the oldest unpublished outbox message")
                .baseUnit("seconds")
                .tag("store", store)
                .register(registry);
    }

    public void published(String topic) {
        counter("ecommerce.outbox.published", "Outbox messages acknowledged by Kafka", topic).increment();
    }

    public void failed(String topic) {
        counter("ecommerce.outbox.failed", "Outbox publish attempts that failed (will be retried)", topic).increment();
    }

    /** @param oldestCreatedAt creation time of the oldest pending message, {@code null} when nothing is pending */
    public void backlog(long pendingCount, Instant oldestCreatedAt) {
        pending.set(pendingCount);
        oldestAgeSeconds.set(oldestCreatedAt == null ? 0
                : Math.max(0, Duration.between(oldestCreatedAt, Instant.now()).toSeconds()));
    }

    private Counter counter(String name, String description, String topic) {
        // Micrometer caches meters by name+tags, so builder.register() is cheap after the first call.
        return Counter.builder(name).description(description).tag("store", store).tag("topic", topic)
                .register(registry);
    }
}
