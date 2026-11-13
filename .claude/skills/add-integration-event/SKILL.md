---
name: add-integration-event
description: Add or evolve a Kafka message (event or command) end to end - contract record, registration, topic, outbox publishing, idempotent consumer, tests. Use when a service must tell other services that something happened.
argument-hint: "<EventName> from <producer> to <consumer(s)>"
---

# Add an integration event

Request: $ARGUMENTS

1. **Name it as a fact or an order.** Events are past tense facts owned by the producer
   (`OrderConfirmedEvent`); commands are imperatives owned by the receiver (`ReserveInventoryCommand`).
2. **Contract** – a `record` in `libs/common-lib/.../contracts` implementing the right marker
   (`SagaMessage`, `ProductEvent`, or `IntegrationMessage`). Carry what consumers need so they do not have
   to call back, but no personal or payment data they do not need. Include the aggregate id and, for
   state events, a monotonically increasing version.
3. **Register** the type in `MessageTypes` and pick the topic in `Topics` (reuse the aggregate's topic;
   add to `COMPACTED` only for latest-state-wins events keyed by entity id).
4. **Publish** inside the business transaction through `OutboxPublisher.publish(topic, aggregateType,
   aggregateId, message)` (or `MongoOutboxPublisher`). Key = aggregate id.
5. **Consume** – a `@KafkaListener` that reads with `MessageReader`, then in one transaction:
   `inbox.markProcessed(messageId, CONSUMER)` → business change → outbox for follow-up messages.
   Poison input → `NonRetryableMessageException`.
6. **Evolve safely** – adding an optional field is fine. Rename/remove/semantic change = new type,
   publish both, migrate consumers, then retire the old one.
7. **Test** – producer writes the outbox row in the same transaction; consumer handles duplicate,
   out-of-order and unknown-field payloads.
8. **Observe** – is there something to alert on (failures, lag, stuck process)? Add the metric and rule.
9. **Document** – the event catalogue in `docs/architecture/01-genel-bakis.md`.
