package com.ecommerce.search.projection;

import com.ecommerce.common.contracts.ProductEvent;
import com.ecommerce.common.contracts.Topics;
import com.ecommerce.common.messaging.IncomingMessage;
import com.ecommerce.common.messaging.MessageReader;
import com.ecommerce.common.messaging.NonRetryableMessageException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code product.events}. No inbox table here, unlike the saga participants: idempotency comes for free
 * from the aggregate version + Elasticsearch external versioning.
 * <p>
 * {@code auto-offset-reset: earliest} + a compacted topic: a brand-new consumer group (or a reset offset)
 * replays the latest state of every product and so rebuilds the whole index.
 * Elasticsearch outages: the error handler retries with back-off, then parks the record on
 * {@code product.events.DLT}; replaying the DLT later is safe because writes are idempotent.
 */
@Component
public class ProductEventsListener {

    private final MessageReader reader;
    private final ProductProjection projection;

    public ProductEventsListener(MessageReader reader, ProductProjection projection) {
        this.reader = reader;
        this.projection = projection;
    }

    @KafkaListener(topics = Topics.PRODUCT_EVENTS)
    public void onProductEvent(ConsumerRecord<String, String> record) {
        IncomingMessage message = reader.read(record);
        if (!(message.payload() instanceof ProductEvent event)) {
            throw new NonRetryableMessageException("Unexpected message on product.events: " + message.type());
        }
        projection.apply(event);
    }
}
