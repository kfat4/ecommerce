# Project state

Updated: 2026-11-14 (phase 1 in progress)

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

- The core stack ran locally on 2026-11-15 (one replica per service): smoke test 11/11 after three fixes
  (see lessons.md). The observability profile (Kibana, Grafana, Prometheus) has not been started yet.
- Testcontainers ITs (`StockProceduresIT`, `ElasticsearchProductIndexIT`) have never been executed.
- The CI `smoke` job has not passed yet in GitHub Actions.
- Kibana APM UI with OTel-native data, Grafana → Kibana exemplar links, alert rule expressions.

## Next (see docs/roadmap.md)

Phase 1 "make it real" (branch `feat/phase-1-ci-and-its`):
- done: failsafe wired, smoke test script, CI workflow (build / config / smoke), Makefile, guard-hook test.
- done 2026-11-15: stack started locally, smoke test green, API console added (`tools/api-console`, `make console`).
- next: get CI green, verify observability with real data.
- done 2026-11-15 (phase 5 slice 1): Helm chart `deploy/helm/ecommerce`, ADR-0009, `make k8s-up`. Runs on
  Docker Desktop Kubernetes, smoke test 11/11. Not in the chart yet: observability stack, HPA, PDB,
  NetworkPolicy, ingress/TLS, external secrets.
