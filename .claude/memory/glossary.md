# Glossary

| Term | Meaning in this project |
|---|---|
| Aggregate | The consistency unit a single transaction may change (an `Order` with its items). Its id is the Kafka key. |
| Bounded context | A part of the domain with its own model and language; here roughly one service each. |
| Command (message) | An instruction to one service (`ReserveInventoryCommand`). Owned by the receiver. |
| Event (message) | A fact that already happened (`OrderConfirmedEvent`). Owned by the producer. |
| Saga | A business process spanning services as a chain of local transactions with compensations. |
| Orchestration | One coordinator (`OrderSagaOrchestrator`) tells participants what to do. The opposite is choreography. |
| Compensation | The business-level undo of a completed step (`ReleaseInventoryCommand`). Not a rollback. |
| Outbox | Table written in the business transaction; a relay publishes its rows to Kafka afterwards. |
| Inbox | `processed_messages`: consumed message ids, written with the business change, to drop duplicates. |
| At-least-once | A message is delivered one or more times. The reason every consumer must be idempotent. |
| Idempotency key | Client-generated id sent with a create request so a retry returns the first result. |
| Read model | A store shaped for queries and fed by events (the Elasticsearch `products` index). |
| Tombstone | A document marked deleted (kept for its version) instead of physically removed. |
| DLT | Dead-letter topic `<topic>.DLT` for messages that failed all retries or are poison. |
| Exemplar | A trace id attached to a metric sample, linking a latency spike to one concrete request. |
| EDOT | Elastic Distribution of OpenTelemetry: the collector that writes OTLP data to Elasticsearch. |
