package com.ecommerce.order.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Order aggregate. State changes only through intention-revealing methods that guard the state machine,
 * so an out-of-order or duplicated saga reply can never move an order backwards (e.g. CONFIRMED → CANCELLED).
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic locking: two replicas handling replies for the same order cannot overwrite each other. */
    @Version
    private long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "line_no")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public Order(UUID id, UUID customerId, String currency, String idempotencyKey, List<OrderItem> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Order needs at least one item");
        }
        this.id = id;
        this.customerId = customerId;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.items = new ArrayList<>(items);
        this.totalAmount = items.stream().map(OrderItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public boolean markAwaitingPayment() {
        return transition(OrderStatus.PENDING, OrderStatus.AWAITING_PAYMENT);
    }

    public boolean confirm() {
        return transition(OrderStatus.AWAITING_PAYMENT, OrderStatus.CONFIRMED);
    }

    public boolean cancel(String reason) {
        if (status == OrderStatus.CONFIRMED || status == OrderStatus.CANCELLED) {
            return false;
        }
        this.status = OrderStatus.CANCELLED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
        return true;
    }

    private boolean transition(OrderStatus expected, OrderStatus next) {
        if (status != expected) {
            return false;
        }
        status = next;
        updatedAt = Instant.now();
        return true;
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OrderItem> getItems() { return List.copyOf(items); }
}
