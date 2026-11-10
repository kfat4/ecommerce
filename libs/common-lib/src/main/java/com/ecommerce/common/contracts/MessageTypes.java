package com.ecommerce.common.contracts;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Explicit allow-list of message types that may travel over Kafka.
 * The logical type name (not the Java FQCN) is sent in the {@code x-message-type} header, so producers and
 * consumers can evolve their package structure independently and no arbitrary class can be instantiated.
 */
public final class MessageTypes {

    private static final Map<String, Class<? extends IntegrationMessage>> BY_NAME = Stream.of(
                    ReserveInventoryCommand.class,
                    ReleaseInventoryCommand.class,
                    InventoryReservedEvent.class,
                    InventoryReservationFailedEvent.class,
                    InventoryReleasedEvent.class,
                    ProcessPaymentCommand.class,
                    PaymentCompletedEvent.class,
                    PaymentFailedEvent.class,
                    OrderConfirmedEvent.class,
                    OrderCancelledEvent.class,
                    ProductUpsertedEvent.class,
                    ProductDeletedEvent.class)
            .collect(Collectors.toUnmodifiableMap(Class::getSimpleName, Function.identity()));

    private MessageTypes() {
    }

    public static String nameOf(Object message) {
        String name = message.getClass().getSimpleName();
        if (!BY_NAME.containsKey(name)) {
            throw new IllegalArgumentException("Unregistered message type: " + message.getClass());
        }
        return name;
    }

    public static Optional<Class<? extends IntegrationMessage>> resolve(String name) {
        return Optional.ofNullable(BY_NAME.get(name));
    }
}
