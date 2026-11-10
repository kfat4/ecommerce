package com.ecommerce.product.outbox;

import com.ecommerce.common.contracts.IntegrationMessage;
import com.ecommerce.common.contracts.MessageTypes;
import com.ecommerce.common.util.UuidV7;
import com.ecommerce.common.web.CorrelationId;
import org.slf4j.MDC;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

/**
 * Transactional Outbox for MongoDB – write side. Same idea as the JDBC {@code OutboxPublisher} in common-lib:
 * the event is stored in the same (multi-document) transaction as the product change, never sent directly.
 */
@Component
public class MongoOutboxPublisher {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    public MongoOutboxPublisher(MongoTemplate mongoTemplate, ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String topic, String aggregateId, IntegrationMessage message) {
        mongoTemplate.insert(new OutboxMessage(
                UuidV7.next().toString(),
                aggregateId,
                topic,
                MessageTypes.nameOf(message),
                objectMapper.writeValueAsString(message),
                MDC.get(CorrelationId.MDC_KEY),
                Instant.now()));
    }
}
