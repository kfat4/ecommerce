package com.ecommerce.common.contracts;

import java.util.List;
import java.util.Set;

/**
 * Kafka topic catalogue. Commands and events are kept on separate topics:
 * commands have exactly one owner (the receiver), events can have many subscribers.
 */
public final class Topics {

    public static final String INVENTORY_COMMANDS = "inventory.commands";
    public static final String INVENTORY_EVENTS = "inventory.events";
    public static final String PAYMENT_COMMANDS = "payment.commands";
    public static final String PAYMENT_EVENTS = "payment.events";
    public static final String ORDER_EVENTS = "order.events";
    /**
     * Log-compacted: Kafka keeps at least the latest event per product key forever, so the topic is a full
     * snapshot of the catalogue. A new read model (e.g. a fresh search index) can be rebuilt by replaying it.
     */
    public static final String PRODUCT_EVENTS = "product.events";

    public static final String DLT_SUFFIX = ".DLT";

    public static final List<String> ALL = List.of(
            INVENTORY_COMMANDS, INVENTORY_EVENTS, PAYMENT_COMMANDS, PAYMENT_EVENTS, ORDER_EVENTS, PRODUCT_EVENTS);

    public static final Set<String> COMPACTED = Set.of(PRODUCT_EVENTS);

    private Topics() {
    }
}
