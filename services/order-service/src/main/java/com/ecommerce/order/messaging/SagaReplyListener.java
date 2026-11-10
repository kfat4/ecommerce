package com.ecommerce.order.messaging;

import com.ecommerce.common.contracts.Topics;
import com.ecommerce.common.inbox.ProcessedMessageStore;
import com.ecommerce.common.messaging.IncomingMessage;
import com.ecommerce.common.messaging.MessageReader;
import com.ecommerce.order.saga.OrderSagaOrchestrator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receives replies from saga participants. Kafka key = orderId, so all replies of one order arrive in order on
 * one partition, and therefore at one consumer (= one replica) at a time.
 */
@Component
public class SagaReplyListener {

    private static final Logger log = LoggerFactory.getLogger(SagaReplyListener.class);
    private static final String CONSUMER = "order-service";

    private final MessageReader reader;
    private final ProcessedMessageStore inbox;
    private final OrderSagaOrchestrator orchestrator;

    public SagaReplyListener(MessageReader reader, ProcessedMessageStore inbox, OrderSagaOrchestrator orchestrator) {
        this.reader = reader;
        this.inbox = inbox;
        this.orchestrator = orchestrator;
    }

    @KafkaListener(topics = {Topics.INVENTORY_EVENTS, Topics.PAYMENT_EVENTS})
    @Transactional
    public void onReply(ConsumerRecord<String, String> record) {
        IncomingMessage message = reader.read(record);
        if (!inbox.markProcessed(message.messageId(), CONSUMER)) {
            log.info("Duplicate reply ignored id={} type={}", message.messageId(), message.type());
            return;
        }
        log.info("Saga reply {} for order={}", message.type(), message.sagaPayload().orderId());
        orchestrator.handle(message.sagaPayload());
    }
}
