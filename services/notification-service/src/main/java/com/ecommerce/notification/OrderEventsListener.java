package com.ecommerce.notification;

import com.ecommerce.common.contracts.OrderCancelledEvent;
import com.ecommerce.common.contracts.OrderConfirmedEvent;
import com.ecommerce.common.contracts.Topics;
import com.ecommerce.common.messaging.IncomingMessage;
import com.ecommerce.common.messaging.MessageReader;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Choreography example: the order-service does not know this service exists. It publishes domain events;
 * anyone interested subscribes. Adding SMS/push/analytics later requires zero changes in order-service.
 */
@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final MessageReader reader;
    private final NotificationRepository repository;

    public OrderEventsListener(MessageReader reader, NotificationRepository repository) {
        this.reader = reader;
        this.repository = repository;
    }

    @KafkaListener(topics = Topics.ORDER_EVENTS)
    public void onOrderEvent(ConsumerRecord<String, String> record) {
        IncomingMessage message = reader.read(record);
        Notification notification = switch (message.payload()) {
            case OrderConfirmedEvent e -> new Notification(message.messageId(), e.customerId(), e.orderId(), "EMAIL",
                    "Your order is confirmed",
                    "Order %s confirmed. Total: %s %s".formatted(e.orderId(), e.totalAmount(), e.currency()),
                    Instant.now());
            case OrderCancelledEvent e -> new Notification(message.messageId(), e.customerId(), e.orderId(), "EMAIL",
                    "Your order was cancelled",
                    "Order %s was cancelled. Reason: %s".formatted(e.orderId(), e.reason()), Instant.now());
            default -> null;
        };
        if (notification == null) {
            return;
        }
        try {
            repository.insert(notification);
            log.info("[EMAIL] to customer={} : {}", notification.customerId(), notification.subject());
        } catch (DuplicateKeyException duplicate) {
            log.info("Duplicate event {} ignored", message.messageId());
        }
    }
}
