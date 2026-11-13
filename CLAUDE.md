# E-commerce Microservices

Reference e-commerce backend (Spring Boot 4.0.9, Java 25, Spring Cloud 2025.1.4, Maven multi-module).
It has two jobs: be a realistic architecture to learn from, and be the playground where the owner practises
agentic development. The owner is a senior Java developer growing into a microservices architect.

## Language

- Talk to the user in **Turkish**. Docs under `docs/` are Turkish.
- Code, comments, commit messages, identifiers, logs and files under `.claude/` are **English**.

## Commands

```bash
./mvnw -q -DskipTests package                 # compile everything
./mvnw test                                   # all unit tests (no Docker needed)
./mvnw -pl services/order-service -am test    # one module + what it depends on
./mvnw verify                                 # + Testcontainers ITs (*IT, failsafe; need a working Docker)
docker compose config -q                      # validate compose after editing it
docker compose up -d --build                  # core stack                      (make up)
docker compose --profile observability up -d  # + Kibana, Prometheus, Grafana   (make up-obs)
scripts/smoke-test.sh                         # end-to-end order through the gateway (make smoke)
```

`make help` lists the shortcuts. CI (`.github/workflows/ci.yml`) runs `verify`, config validation and the
smoke test on every pull request.

Run the narrowest test that proves the change, then the full `./mvnw test` before saying "done".
Docker is often not running on this machine: if you could not start the stack, say so plainly. A hung Docker
Desktop makes `./mvnw verify` stall in the first `*IT` (Testcontainers waits on the socket); use `./mvnw test`.

## Layout

```
libs/common-lib          contracts (events/commands), outbox, inbox, idempotency, security, web, messaging
libs/observability-lib   OTel logs+traces, Prometheus registry, shared logback, low-precedence defaults
services/<name>          one Spring Boot app each: api/ application/ domain/ (+ saga/, outbox/, client/ ...)
infra/                   observability configs, postgres init, wiremock, pom template
scripts/                 smoke test, guard-hook test
docs/                    architecture, ADRs, roadmap, learning curriculum (Turkish)
.claude/                 agents, skills, rules, hooks, shared memory
```

| Service | Port | Store | Role |
|---|---|---|---|
| api-gateway | 8080 | Redis (rate limit) | only public entry; JWT check, routing, retry on GET, circuit breaker |
| discovery-server | 8761 | – | Eureka registry |
| auth-service | 8086 | PostgreSQL `auth_db` | users, login, JWT issuing |
| product-service | 8081 | MongoDB `product_db` + Redis cache | catalogue, Mongo outbox → `product.events` |
| inventory-service | 8083 | PostgreSQL `inventory_db` | stock reservation (PL/pgSQL procedures) |
| order-service | 8082 | PostgreSQL `order_db` | orders + saga orchestrator |
| payment-service | 8084 | PostgreSQL `payment_db` | payments, calls external provider (WireMock) |
| notification-service | 8085 | MongoDB `notification_db` | reacts to `order.events` |
| search-service | 8087 | Elasticsearch `products` alias | CQRS read model fed by `product.events` |

## Architecture rules (do not break without an ADR)

1. **Database per service.** No service reads another service's tables or collections. Data crosses a
   boundary only through a public API or an event.
2. **State change + message = one local transaction.** Never call `KafkaTemplate.send` from business code.
   Publish through `OutboxPublisher` (PostgreSQL) or `MongoOutboxPublisher` inside the transaction.
3. **Every consumer is idempotent.** Delivery is at-least-once. Use `ProcessedMessageStore.markProcessed`
   in the same transaction as the business change, or a naturally idempotent write (search-service uses
   Elasticsearch external versioning).
4. **Contracts live in `common-lib/contracts`** and are registered in `MessageTypes`. Changes must be
   backward compatible: add optional fields, never rename or remove. A breaking change is a new type.
5. **Commands that create something over HTTP require `Idempotency-Key`** (`ecommerce.idempotency.paths`).
6. **Sync calls between services need a timeout, retry (idempotent calls only) and a circuit breaker.**
   Prefer events over sync calls; a sync call is a runtime coupling.
7. **Only the gateway is public.** `/internal/**` is never routed. Services trust the JWT, not the network.
8. **Schema belongs to Flyway.** Applied migrations are immutable: add `V<n+1>__*.sql`, never edit an old one.
9. **Observable by default.** New flows get logs with context, are covered by tracing, and expose a metric
   if someone would want to alert on them. Metric tags must be low-cardinality (never ids or free text).
10. **Secrets never enter the repo.** Config comes from the environment; `.env.example` holds placeholders only.

Why each rule exists is in `docs/adr/`. Read the relevant ADR before changing a rule's area.

## How to work here

- **Think before coding.** State assumptions. If a request has two reasonable readings, say which you chose.
  Ask only when a wrong guess would be expensive.
- **Simplicity first.** Write the least code that solves the stated problem. No speculative abstraction,
  no config knobs nobody asked for. This is a teaching codebase: readable beats clever.
- **Surgical changes.** Touch what the task needs. Match the surrounding style. Mention unrelated problems
  you notice instead of fixing them silently.
- **Goal-driven.** Turn the task into something checkable (a failing test, a command that must pass) and
  loop until it passes. Report what you verified and what you could not.
- **Teach while you build.** After a non-trivial change, explain in Turkish what pattern was applied, why,
  what the trade-off is and which production concern it addresses. Link the file and line.
- **Decisions leave a trace.** An architectural choice gets an ADR (`/write-adr`). A gotcha that cost real
  time goes into shared memory (`/remember`).

## Git

- Never commit to `main` directly; branch (`feat/…`, `fix/…`, `docs/…`).
- Commit or push only when the user asks. Ask before any push.
- The commit identity is the GitHub noreply address already set in this repo. Do not change it.
- Never stage `.env*` (except `.env.example`), keys, certificates or anything with personal data.

## Agent team

Specialists live in `.claude/agents/`. Delegate when the work matches; keep small edits in the main session.

| Agent | Use for |
|---|---|
| `solution-architect` | design options, service boundaries, ADR drafts; read-only on code |
| `java-backend-developer` | implementing features and fixes in services and libs |
| `test-engineer` | unit, slice, Testcontainers and contract tests; reproducing bugs |
| `code-reviewer` | reviewing a diff against the rules above |
| `security-reviewer` | authn/authz, secrets, injection, PII in logs |
| `platform-engineer` | Docker, compose, CI/CD, Kubernetes, config |
| `sre-observability` | metrics, alerts, dashboards, tracing, SLOs, incident analysis |
| `tech-mentor` | teaching a topic from this codebase to the user, with exercises |

`/feature-team <feature>` runs the full pipeline: design → implement → test → review.

## Memory

Shared, committed project memory is imported below and is part of every session. Protocol and layers:
`docs/ai/hafiza-sistemi.md`. Treat memory as a claim to verify, not as truth: check the code before acting on it.

@.claude/memory/MEMORY.md
