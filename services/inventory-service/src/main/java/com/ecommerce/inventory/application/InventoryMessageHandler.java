package com.ecommerce.inventory.application;

import com.ecommerce.common.contracts.InventoryReleasedEvent;
import com.ecommerce.common.contracts.InventoryReservationFailedEvent;
import com.ecommerce.common.contracts.InventoryReservedEvent;
import com.ecommerce.common.contracts.OrderCancelledEvent;
import com.ecommerce.common.contracts.OrderConfirmedEvent;
import com.ecommerce.common.contracts.ReleaseInventoryCommand;
import com.ecommerce.common.contracts.ReserveInventoryCommand;
import com.ecommerce.common.contracts.Topics;
import com.ecommerce.common.inbox.ProcessedMessageStore;
import com.ecommerce.common.messaging.IncomingMessage;
import com.ecommerce.common.messaging.MessageReader;
import com.ecommerce.common.messaging.NonRetryableMessageException;
import com.ecommerce.common.outbox.OutboxPublisher;
import com.ecommerce.inventory.infrastructure.StockRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga participant. Each message is handled in ONE local transaction containing:
 * inbox insert (dedupe) + stock change (PL/pgSQL) + outbox insert (reply).
 * Either all three are committed or none – there is no window where stock changed but no reply will be sent.
 */
@Component
public class InventoryMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(InventoryMessageHandler.class);
    private static final String CONSUMER = "inventory-service";
    private static final String AGGREGATE = "Order";

    private final MessageReader reader;
    private final ProcessedMessageStore inbox;
    private final OutboxPublisher outbox;
    private final StockRepository stock;

    public InventoryMessageHandler(MessageReader reader, ProcessedMessageStore inbox, OutboxPublisher outbox,
                                   StockRepository stock) {
        this.reader = reader;
        this.inbox = inbox;
        this.outbox = outbox;
        this.stock = stock;
    }

    @KafkaListener(topics = Topics.INVENTORY_COMMANDS)
    @Transactional
    public void onCommand(ConsumerRecord<String, String> record) {
        IncomingMessage message = reader.read(record);
        if (!inbox.markProcessed(message.messageId(), CONSUMER)) {
            log.info("Duplicate message ignored id={} type={}", message.messageId(), message.type());
            return;
        }
        switch (message.payload()) {
            case ReserveInventoryCommand cmd -> {
                var result = stock.reserve(cmd.orderId(), cmd.lines());
                log.info("Reserve stock order={} success={} reason={}", cmd.orderId(), result.success(), result.reason());
                Object reply = result.success()
                        ? new InventoryReservedEvent(cmd.orderId())
                        : new InventoryReservationFailedEvent(cmd.orderId(), result.reason());
                outbox.publish(Topics.INVENTORY_EVENTS, AGGREGATE, cmd.orderId().toString(), reply);
            }
            case ReleaseInventoryCommand cmd -> {
                int released = stock.release(cmd.orderId());
                log.info("Compensation: released {} reservation lines for order={} reason={}",
                        released, cmd.orderId(), cmd.reason());
                outbox.publish(Topics.INVENTORY_EVENTS, AGGREGATE, cmd.orderId().toString(),
                        new InventoryReleasedEvent(cmd.orderId()));
            }
            default -> throw new NonRetryableMessageException("Unexpected command " + message.type());
        }
    }

    /** Choreography: inventory reacts to order lifecycle events it does not own. */
    @KafkaListener(topics = Topics.ORDER_EVENTS)
    @Transactional
    public void onOrderEvent(ConsumerRecord<String, String> record) {
        IncomingMessage message = reader.read(record);
        if (!inbox.markProcessed(message.messageId(), CONSUMER)) {
            return;
        }
        switch (message.payload()) {
            case OrderConfirmedEvent event -> log.info("Order {} confirmed, committed {} reservation lines",
                    event.orderId(), stock.commit(event.orderId()));
            // Safety net: releasing is idempotent, so doing it again for a cancelled order is harmless.
            case OrderCancelledEvent event -> stock.release(event.orderId());
            default -> log.debug("Ignoring {}", message.type());
        }
    }
}
