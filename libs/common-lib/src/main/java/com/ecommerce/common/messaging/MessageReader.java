package com.ecommerce.common.messaging;

import com.ecommerce.common.contracts.MessageTypes;
import com.ecommerce.common.contracts.IntegrationMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/** Converts a raw Kafka record (JSON string payload + type header) into a typed contract object. */
public class MessageReader {

    private final ObjectMapper objectMapper;

    public MessageReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public IncomingMessage read(ConsumerRecord<String, String> record) {
        String messageId = header(record, MessageHeaders.MESSAGE_ID);
        String type = header(record, MessageHeaders.MESSAGE_TYPE);
        if (messageId == null || type == null) {
            throw new NonRetryableMessageException("Missing message id/type headers on " + record.topic());
        }
        Class<? extends IntegrationMessage> target = MessageTypes.resolve(type)
                .orElseThrow(() -> new NonRetryableMessageException("Unknown message type: " + type));
        try {
            return new IncomingMessage(messageId, type, objectMapper.readValue(record.value(), target));
        } catch (JacksonException e) {
            throw new NonRetryableMessageException("Cannot deserialize " + type, e);
        }
    }

    public static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
