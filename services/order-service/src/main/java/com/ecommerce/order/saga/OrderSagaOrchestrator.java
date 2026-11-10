package com.ecommerce.order.saga;

import com.ecommerce.common.contracts.InventoryReleasedEvent;
import com.ecommerce.common.contracts.InventoryReservationFailedEvent;
import com.ecommerce.common.contracts.InventoryReservedEvent;
import com.ecommerce.common.contracts.OrderCancelledEvent;
import com.ecommerce.common.contracts.OrderConfirmedEvent;
import com.ecommerce.common.contracts.OrderLine;
import com.ecommerce.common.contracts.PaymentCompletedEvent;
import com.ecommerce.common.contracts.PaymentFailedEvent;
import com.ecommerce.common.contracts.ProcessPaymentCommand;
import com.ecommerce.common.contracts.ReleaseInventoryCommand;
import com.ecommerce.common.contracts.ReserveInventoryCommand;
import com.ecommerce.common.contracts.SagaMessage;
import com.ecommerce.common.contracts.Topics;
import com.ecommerce.common.outbox.OutboxPublisher;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestration-based Saga for "place order".
 *
 * <pre>
 *  PENDING ──ReserveInventory──▶ inventory ──Reserved──▶ AWAITING_PAYMENT ──ProcessPayment──▶ payment
 *     │                              │                                                       │
 *     │                         ReservationFailed                                   Completed │ Failed
 *     ▼                              ▼                                                       ▼        ▼
 *  CANCELLED ◀───────────────── CANCELLED                                          CONFIRMED   CANCELLED
 *                                                                                               │
 *                                               ReleaseInventory (compensation) ◀───────────────┘
 * </pre>
 *
 * Every handler runs inside the caller's transaction (MANDATORY) together with the inbox insert, the state
 * change and the outbox insert of the next command – the local-transaction building block of the saga.
 * Guards ({@link OrderSaga#isAt}) make every handler idempotent and safe against out-of-order replies.
 */
@Component
@EnableConfigurationProperties(SagaProperties.class)
@Transactional(propagation = Propagation.MANDATORY)
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);
    private static final String AGGREGATE = "Order";

    private final OrderRepository orders;
    private final OrderSagaRepository sagas;
    private final OutboxPublisher outbox;
    private final SagaProperties properties;
    private final SagaMetrics metrics;

    public OrderSagaOrchestrator(OrderRepository orders, OrderSagaRepository sagas, OutboxPublisher outbox,
                                 SagaProperties properties, SagaMetrics metrics) {
        this.orders = orders;
        this.sagas = sagas;
        this.outbox = outbox;
        this.properties = properties;
        this.metrics = metrics;
    }

    /** Step 0: called in the same transaction that inserts the order. */
    public void start(Order order) {
        sagas.save(new OrderSaga(order.getId(), properties.stepTimeout()));
        send(Topics.INVENTORY_COMMANDS, order.getId(), reserveCommand(order));
        metrics.started();
        log.info("Saga started for order={}", order.getId());
    }

    public void handle(SagaMessage message) {
        switch (message) {
            case InventoryReservedEvent e -> onInventoryReserved(e);
            case InventoryReservationFailedEvent e -> onInventoryReservationFailed(e);
            case PaymentCompletedEvent e -> onPaymentCompleted(e);
            case PaymentFailedEvent e -> onPaymentFailed(e);
            case InventoryReleasedEvent e -> onInventoryReleased(e);
            default -> log.warn("Orchestrator ignores {}", message.getClass().getSimpleName());
        }
    }

    private void onInventoryReserved(InventoryReservedEvent event) {
        OrderSaga saga = saga(event.orderId());
        if (!saga.isAt(SagaStep.RESERVE_INVENTORY, SagaStatus.STARTED)) {
            ignore(event, saga);
            return;
        }
        Order order = order(event.orderId());
        order.markAwaitingPayment();
        saga.advanceTo(SagaStep.PROCESS_PAYMENT, properties.stepTimeout());
        send(Topics.PAYMENT_COMMANDS, order.getId(), paymentCommand(order));
    }

    private void onInventoryReservationFailed(InventoryReservationFailedEvent event) {
        OrderSaga saga = saga(event.orderId());
        if (!saga.isAt(SagaStep.RESERVE_INVENTORY, SagaStatus.STARTED)) {
            ignore(event, saga);
            return;
        }
        Order order = order(event.orderId());
        order.cancel(event.reason());
        saga.abort(); // nothing to compensate: the failing step was the first one
        send(Topics.ORDER_EVENTS, order.getId(),
                new OrderCancelledEvent(order.getId(), order.getCustomerId(), event.reason()));
        metrics.finished("cancelled", order.getCreatedAt());
    }

    private void onPaymentCompleted(PaymentCompletedEvent event) {
        OrderSaga saga = saga(event.orderId());
        if (!saga.isAt(SagaStep.PROCESS_PAYMENT, SagaStatus.STARTED)) {
            // e.g. payment succeeded after we already gave up – would require a refund flow (see ADR-0002).
            log.error("Late PaymentCompleted for order={} in saga state {}/{} – manual refund required",
                    event.orderId(), saga.getStep(), saga.getStatus());
            metrics.latePayment();
            return;
        }
        Order order = order(event.orderId());
        order.confirm();
        saga.complete();
        send(Topics.ORDER_EVENTS, order.getId(), new OrderConfirmedEvent(order.getId(), order.getCustomerId(),
                order.getTotalAmount(), order.getCurrency()));
        metrics.finished("confirmed", order.getCreatedAt());
        log.info("Saga completed, order={} CONFIRMED", order.getId());
    }

    private void onPaymentFailed(PaymentFailedEvent event) {
        OrderSaga saga = saga(event.orderId());
        if (!saga.isAt(SagaStep.PROCESS_PAYMENT, SagaStatus.STARTED)) {
            ignore(event, saga);
            return;
        }
        compensate(order(event.orderId()), saga, "PAYMENT_FAILED:" + event.reason(), "payment_failed");
    }

    private void onInventoryReleased(InventoryReleasedEvent event) {
        OrderSaga saga = saga(event.orderId());
        if (!saga.isAt(SagaStep.RELEASE_INVENTORY, SagaStatus.COMPENSATING)) {
            ignore(event, saga);
            return;
        }
        Order order = order(event.orderId());
        saga.abort();
        send(Topics.ORDER_EVENTS, order.getId(),
                new OrderCancelledEvent(order.getId(), order.getCustomerId(), order.getFailureReason()));
        metrics.finished("cancelled", order.getCreatedAt());
        log.info("Compensation finished, order={} CANCELLED", order.getId());
    }

    /** Called by {@link SagaTimeoutScheduler} for sagas whose current step did not answer in time. */
    public void onTimeout(OrderSaga saga) {
        Order order = order(saga.getOrderId());
        metrics.timeout(saga.getStep());
        switch (saga.getStep()) {
            // No answer from inventory: compensating is always safe because release is idempotent
            // and inventory remembers released orders (a late reserve command is then rejected).
            case RESERVE_INVENTORY -> compensate(order, saga, "INVENTORY_TIMEOUT", "inventory_timeout");
            // No answer from payment: we must NOT assume failure (the customer may have been charged).
            // ProcessPayment is idempotent (orderId is the key), so asking again is safe.
            case PROCESS_PAYMENT -> {
                if (saga.getAttempts() >= properties.maxPaymentRetries()) {
                    saga.markStuck();
                    metrics.stuck();
                    log.error("Saga STUCK: no payment answer for order={} after {} retries", order.getId(),
                            saga.getAttempts());
                } else {
                    saga.retry(properties.stepTimeout());
                    log.warn("Payment timeout, re-sending ProcessPayment order={} attempt={}", order.getId(),
                            saga.getAttempts());
                    send(Topics.PAYMENT_COMMANDS, order.getId(), paymentCommand(order));
                }
            }
            case RELEASE_INVENTORY -> {
                saga.retry(properties.stepTimeout());
                send(Topics.INVENTORY_COMMANDS, order.getId(),
                        new ReleaseInventoryCommand(order.getId(), order.getFailureReason()));
            }
            case DONE -> { }
        }
    }

    /** @param trigger low-cardinality metric tag; {@code reason} is free text and stays in logs and events */
    private void compensate(Order order, OrderSaga saga, String reason, String trigger) {
        metrics.compensation(trigger);
        order.cancel(reason);
        saga.compensateWith(SagaStep.RELEASE_INVENTORY, properties.stepTimeout());
        send(Topics.INVENTORY_COMMANDS, order.getId(), new ReleaseInventoryCommand(order.getId(), reason));
        log.info("Compensating order={} reason={}", order.getId(), reason);
    }

    private static ReserveInventoryCommand reserveCommand(Order order) {
        return new ReserveInventoryCommand(order.getId(), order.getItems().stream()
                .map(i -> new OrderLine(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                .toList());
    }

    private static ProcessPaymentCommand paymentCommand(Order order) {
        return new ProcessPaymentCommand(order.getId(), order.getCustomerId(), order.getTotalAmount(),
                order.getCurrency());
    }

    private void send(String topic, UUID orderId, Object message) {
        outbox.publish(topic, AGGREGATE, orderId.toString(), message);
    }

    private OrderSaga saga(UUID orderId) {
        return sagas.findById(orderId).orElseThrow(() -> new IllegalStateException("No saga for order " + orderId));
    }

    private Order order(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() -> new IllegalStateException("No order " + orderId));
    }

    private static void ignore(SagaMessage message, OrderSaga saga) {
        log.warn("Ignoring {} for order={} – saga is at {}/{} (duplicate or out-of-order reply)",
                message.getClass().getSimpleName(), message.orderId(), saga.getStep(), saga.getStatus());
    }
}
