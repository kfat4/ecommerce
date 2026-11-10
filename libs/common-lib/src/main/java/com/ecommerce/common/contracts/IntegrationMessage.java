package com.ecommerce.common.contracts;

/**
 * Root marker for everything that may travel over Kafka between services.
 * Only registered implementations ({@link MessageTypes}) can be deserialized by consumers.
 */
public interface IntegrationMessage {
}
