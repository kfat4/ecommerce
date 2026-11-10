package com.ecommerce.order.saga;

import com.ecommerce.common.observability.TransactionalMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Business metrics of the order saga. Everything is recorded only after the surrounding transaction commits
 * (see {@link TransactionalMetrics}), so a rolled back and redelivered reply is never counted twice.
 * <pre>
 *  ecommerce_saga_started_total
 *  ecommerce_saga_finished_total{outcome=confirmed|cancelled}
 *  ecommerce_saga_duration_seconds{outcome}            order placed -> final state (histogram)
 *  ecommerce_saga_compensations_total{trigger}         payment_failed | inventory_timeout
 *  ecommerce_saga_timeouts_total{step}
 *  ecommerce_saga_stuck_total                          needs a human -> alert
 *  ecommerce_saga_late_payments_total                  paid after we gave up -> refund needed -> alert
 * </pre>
 */
@Component
public class SagaMetrics {

    private final MeterRegistry registry;

    public SagaMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void started() {
        increment("ecommerce.saga.started", "Order sagas started");
    }

    void finished(String outcome, Instant orderCreatedAt) {
        TransactionalMetrics.afterCommit(() -> {
            Counter.builder("ecommerce.saga.finished").description("Order sagas that reached a final state")
                    .tag("outcome", outcome).register(registry).increment();
            Timer.builder("ecommerce.saga.duration").description("Time from order placement to the final state")
                    .tag("outcome", outcome)
                    .publishPercentileHistogram()
                    .maximumExpectedValue(Duration.ofMinutes(5))
                    .register(registry)
                    .record(Duration.between(orderCreatedAt, Instant.now()));
        });
    }

    void compensation(String trigger) {
        TransactionalMetrics.afterCommit(() -> Counter.builder("ecommerce.saga.compensations")
                .description("Sagas that had to undo completed steps")
                .tag("trigger", trigger).register(registry).increment());
    }

    void timeout(SagaStep step) {
        TransactionalMetrics.afterCommit(() -> Counter.builder("ecommerce.saga.timeouts")
                .description("Saga steps that did not answer in time")
                .tag("step", step.name().toLowerCase()).register(registry).increment());
    }

    void stuck() {
        increment("ecommerce.saga.stuck", "Sagas that need manual intervention");
    }

    void latePayment() {
        increment("ecommerce.saga.late.payments", "Payments completed after the saga had already given up");
    }

    private void increment(String name, String description) {
        TransactionalMetrics.afterCommit(() ->
                Counter.builder(name).description(description).register(registry).increment());
    }
}
