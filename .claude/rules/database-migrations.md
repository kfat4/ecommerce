---
paths:
  - "**/db/migration/**"
  - "infra/postgres/**"
  - "infra/oracle/**"
---

# Database migrations

- Flyway owns the schema; Hibernate runs with `ddl-auto: validate`.
- An applied migration is immutable. To change something, add the next `V<n>__short_description.sql`.
  (A hook blocks edits to existing `V*.sql` files.)
- Migrations must be backward compatible with the previous application version, because replicas roll one
  at a time: expand (add nullable column / new table) → deploy code → contract (drop) in a later release.
- Never: drop or rename a column in the same release that stops using it, add `NOT NULL` without a
  default on a populated table, or build an index without `CONCURRENTLY` on a large table.
- Each service has its own database and its own migration folder. No cross-database foreign keys or joins.
- Outbox and inbox tables (`outbox_events`, `processed_messages`) are part of the service schema.
