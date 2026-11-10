package com.ecommerce.common.contracts;

/**
 * Catalogue change events, published by product-service (owner of the data) to {@link Topics#PRODUCT_EVENTS}.
 * <p>
 * Event-carried state transfer: an upsert event contains the <b>whole</b> product, so consumers (search-service)
 * can build their own read model without ever calling product-service back.
 * <p>
 * {@link #version()} is the aggregate version (MongoDB {@code @Version}) and grows monotonically. Consumers use it
 * to make processing idempotent and order-independent: an event older than what they already applied is ignored.
 */
public sealed interface ProductEvent extends IntegrationMessage permits ProductUpsertedEvent, ProductDeletedEvent {

    String productId();

    long version();
}
