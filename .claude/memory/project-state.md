# Project state

Updated: 2026-11-14

## Built and unit-tested

- 9 services + 2 libs compile; `./mvnw test` green (28 tests) on 2026-11-11.
- Order saga (orchestration) with compensation, timeouts, stuck detection.
- Transactional outbox: PostgreSQL (polling, `SKIP LOCKED`) and MongoDB (`findAndModify` lease).
- Idempotent consumer (inbox) and HTTP `Idempotency-Key` filter (Redis).
- CQRS read model: product-service → `product.events` (compacted) → search-service → Elasticsearch.
- Gateway: JWT validation, Redis rate limiter, circuit breaker with fallback, retry on GET.
- Observability: OTLP logs + traces → EDOT collector → Elasticsearch/Kibana; Prometheus (Eureka SD),
  Grafana (2 dashboards), Alertmanager (17 rules); business metrics for saga, outbox, inbox, idempotency.
- Claude Code setup: CLAUDE.md, 5 path-scoped rules, 8 agents, 8 skills, guard hook, shared memory.

## Not verified

- Nothing has run in Docker. First `docker compose up` will likely surface configuration mistakes.
- Testcontainers ITs (`StockProceduresIT`, `ElasticsearchProductIndexIT`) have never been executed.
- Kibana APM UI with OTel-native data, Grafana → Kibana exemplar links, alert rule expressions.

## Next (see docs/roadmap.md)

Phase 1 "make it real": run the stack, wire failsafe for ITs, CI pipeline, fix what breaks.
The user has not yet chosen which phase to start.
