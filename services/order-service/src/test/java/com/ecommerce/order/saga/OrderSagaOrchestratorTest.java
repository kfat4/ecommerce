package com.ecommerce.order.saga;

import com.ecommerce.common.contracts.InventoryReleasedEvent;
import com.ecommerce.common.contracts.InventoryReservationFailedEvent;
import com.ecommerce.common.contracts.InventoryReservedEvent;
import com.ecommerce.common.contracts.OrderCancelledEvent;
import com.ecommerce.common.contracts.OrderConfirmedEvent;
import com.ecommerce.common.contracts.PaymentCompletedEvent;
import com.ecommerce.common.contracts.PaymentFailedEvent;
import com.ecommerce.common.contracts.ProcessPaymentCommand;
import com.ecommerce.common.contracts.ReleaseInventoryCommand;
import com.ecommerce.common.contracts.ReserveInventoryCommand;
import com.ecommerce.common.contracts.Topics;
import com.ecommerce.common.outbox.OutboxPublisher;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.domain.OrderStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderSagaOrchestratorTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderSagaRepository sagas = mock(OrderSagaRepository.class);
    private final OutboxPublisher outbox = mock(OutboxPublisher.class);
    private final SagaProperties properties = new SagaProperties(Duration.ofSeconds(30), 2);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final OrderSagaOrchestrator orchestrator =
            new OrderSagaOrchestrator(orders, sagas, outbox, properties, new SagaMetrics(meterRegistry));

    private Order order;
    private OrderSaga saga;

    @BeforeEach
    void setUp() {
        order = new Order(UUID.randomUUID(), UUID.randomUUID(), "EUR", "key-1",
                List.of(new OrderItem("PRD-1", "Book", 2, new BigDecimal("10.00"))));
        saga = new OrderSaga(order.getId(), properties.stepTimeout());
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(sagas.findById(order.getId())).thenReturn(Optional.of(saga));
    }

    @Test
    void startSendsReserveInventoryCommand() {
        orchestrator.start(order);

        verify(sagas).save(any(OrderSaga.class));
        var captor = ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(eq(Topics.INVENTORY_COMMANDS), eq("Order"), eq(order.getId().toString()), captor.capture());
        assertThat(captor.getValue()).isInstanceOfSatisfying(ReserveInventoryCommand.class,
                cmd -> assertThat(cmd.lines()).hasSize(1));
        assertThat(order.getTotalAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void happyPathReservedThenPaidConfirmsOrder() {
        orchestrator.handle(new InventoryReservedEvent(order.getId()));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(saga.getStep()).isEqualTo(SagaStep.PROCESS_PAYMENT);
        verify(outbox).publish(eq(Topics.PAYMENT_COMMANDS), anyString(), anyString(), any(ProcessPaymentCommand.class));

        orchestrator.handle(new PaymentCompletedEvent(order.getId(), UUID.randomUUID(), "ch_1"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        verify(outbox).publish(eq(Topics.ORDER_EVENTS), anyString(), anyString(), any(OrderConfirmedEvent.class));
        assertThat(meterRegistry.get("ecommerce.saga.finished").tag("outcome", "confirmed").counter().count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.get("ecommerce.saga.duration").tag("outcome", "confirmed").timer().count())
                .isEqualTo(1);
    }

    @Test
    void inventoryFailureCancelsWithoutCompensation() {
        orchestrator.handle(new InventoryReservationFailedEvent(order.getId(), "INSUFFICIENT_STOCK:PRD-1"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.ABORTED);
        verify(outbox, never()).publish(eq(Topics.INVENTORY_COMMANDS), anyString(), anyString(), any());
        verify(outbox).publish(eq(Topics.ORDER_EVENTS), anyString(), anyString(), any(OrderCancelledEvent.class));
    }

    @Test
    void paymentFailureTriggersCompensation() {
        orchestrator.handle(new InventoryReservedEvent(order.getId()));
        orchestrator.handle(new PaymentFailedEvent(order.getId(), "INSUFFICIENT_FUNDS"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
        verify(outbox).publish(eq(Topics.INVENTORY_COMMANDS), anyString(), anyString(), any(ReleaseInventoryCommand.class));

        orchestrator.handle(new InventoryReleasedEvent(order.getId()));

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.ABORTED);
        verify(outbox).publish(eq(Topics.ORDER_EVENTS), anyString(), anyString(), any(OrderCancelledEvent.class));
    }

    @Test
    void duplicateReplyIsIgnored() {
        orchestrator.handle(new InventoryReservedEvent(order.getId()));
        orchestrator.handle(new InventoryReservedEvent(order.getId()));

        verify(outbox, times(1)).publish(eq(Topics.PAYMENT_COMMANDS), anyString(), anyString(), any());
    }

    @Test
    void confirmedOrderIsNeverCancelledByLateReply() {
        orchestrator.handle(new InventoryReservedEvent(order.getId()));
        orchestrator.handle(new PaymentCompletedEvent(order.getId(), UUID.randomUUID(), "ch_1"));
        orchestrator.handle(new PaymentFailedEvent(order.getId(), "late"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void paymentTimeoutRetriesThenMarksSagaStuck() {
        orchestrator.handle(new InventoryReservedEvent(order.getId()));

        orchestrator.onTimeout(saga);
        orchestrator.onTimeout(saga);
        orchestrator.onTimeout(saga);

        // 1 original + 2 retries (maxPaymentRetries = 2), then STUCK
        verify(outbox, times(3)).publish(eq(Topics.PAYMENT_COMMANDS), anyString(), anyString(), any(ProcessPaymentCommand.class));
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.STUCK);
        assertThat(meterRegistry.get("ecommerce.saga.timeouts").tag("step", "process_payment").counter().count())
                .isEqualTo(3.0);
        assertThat(meterRegistry.get("ecommerce.saga.stuck").counter().count()).isEqualTo(1.0);
    }

    @Test
    void inventoryTimeoutCompensates() {
        orchestrator.onTimeout(saga);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
        verify(outbox).publish(eq(Topics.INVENTORY_COMMANDS), anyString(), anyString(), any(ReleaseInventoryCommand.class));
    }
}
