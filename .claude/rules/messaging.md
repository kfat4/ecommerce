---
paths:
  - "libs/common-lib/src/main/java/com/ecommerce/common/contracts/**"
  - "libs/common-lib/src/main/java/com/ecommerce/common/outbox/**"
  - "libs/common-lib/src/main/java/com/ecommerce/common/inbox/**"
  - "libs/common-lib/src/main/java/com/ecommerce/common/messaging/**"
  - "services/**/*Listener.java"
  - "services/**/*Handler.java"
  - "services/**/outbox/**"
  - "services/**/saga/**"
---

# Messaging (Kafka, outbox, inbox, saga)

- Publishing: only through the outbox, inside the business transaction. The relay delivers at-least-once.
- Consuming: read with `MessageReader`, then either `inbox.markProcessed(messageId, CONSUMER)` in the same
  transaction as the state change, or a write that is idempotent by design. Skip quietly on duplicates.
- Message key = aggregate id, so all events of one aggregate stay ordered in one partition.
- Throw `NonRetryableMessageException` for poison messages (bad payload, unknown type): they go straight
  to `<topic>.DLT`. Any other exception is retried with back-off, then dead-lettered.
- A new message type needs: a `record` in `contracts`, registration in `MessageTypes`, a topic in `Topics`
  (add to `COMPACTED` only for state-carrying events keyed by entity id), a handler, and a test.
- Contract evolution: add optional fields only. Consumers must tolerate unknown fields. Renames and
  removals are breaking: introduce a new type and run both until every consumer has moved.
- Saga handlers guard with the current step/status (`OrderSaga.isAt`) so duplicates and late replies are
  no-ops. Every step that changes another service needs a compensation or a documented reason why not.
- Never assume a timeout means failure when money is involved: retry the idempotent command instead.
