package com.ecommerce.common.messaging;

import com.ecommerce.common.web.CorrelationId;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * Restores the correlation id of the originating HTTP request into the logging MDC before a listener runs.
 * Result: in Kibana (logs-* data streams) you can search one correlationId and see the full saga across every service.
 */
public class CorrelationIdRecordInterceptor implements RecordInterceptor<Object, Object> {

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        String correlationId = MessageReader.header(record, MessageHeaders.CORRELATION_ID);
        if (correlationId != null) {
            MDC.put(CorrelationId.MDC_KEY, correlationId);
        }
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        MDC.remove(CorrelationId.MDC_KEY);
    }
}
