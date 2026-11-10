package com.ecommerce.order.saga;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Persistent saga state (saga log). Survives restarts, can be inspected, timed-out and retried. */
@Entity
@Table(name = "order_sagas")
public class OrderSaga {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStep step;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStatus status;

    @Column(nullable = false)
    private int attempts;

    /** If no reply arrives before the deadline the timeout scheduler acts (retry or compensate). */
    @Column(nullable = false)
    private Instant deadline;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected OrderSaga() {
    }

    public OrderSaga(UUID orderId, Duration stepTimeout) {
        this.orderId = orderId;
        this.step = SagaStep.RESERVE_INVENTORY;
        this.status = SagaStatus.STARTED;
        touch(stepTimeout);
    }

    public boolean isAt(SagaStep expectedStep, SagaStatus expectedStatus) {
        return step == expectedStep && status == expectedStatus;
    }

    public void advanceTo(SagaStep next, Duration stepTimeout) {
        this.step = next;
        this.attempts = 0;
        touch(stepTimeout);
    }

    public void compensateWith(SagaStep compensatingStep, Duration stepTimeout) {
        this.status = SagaStatus.COMPENSATING;
        this.step = compensatingStep;
        this.attempts = 0;
        touch(stepTimeout);
    }

    public void retry(Duration stepTimeout) {
        this.attempts++;
        touch(stepTimeout);
    }

    public void complete() {
        finish(SagaStatus.COMPLETED);
    }

    public void abort() {
        finish(SagaStatus.ABORTED);
    }

    public void markStuck() {
        this.status = SagaStatus.STUCK;
        this.updatedAt = Instant.now();
    }

    private void finish(SagaStatus terminal) {
        this.status = terminal;
        this.step = SagaStep.DONE;
        this.updatedAt = Instant.now();
    }

    private void touch(Duration stepTimeout) {
        this.updatedAt = Instant.now();
        this.deadline = updatedAt.plus(stepTimeout);
    }

    public UUID getOrderId() { return orderId; }
    public SagaStep getStep() { return step; }
    public SagaStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getDeadline() { return deadline; }
}
