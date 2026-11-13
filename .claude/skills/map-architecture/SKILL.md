---
name: map-architecture
description: Map an unfamiliar microservice codebase quickly - services, data ownership, sync and async dependencies, critical flows, risks. Use when joining a new project or company, onboarding to a repository, or auditing an architecture before changing it. Works in any repository, not only this one.
argument-hint: "[path or focus area]"
---

# Map an architecture

Scope: $ARGUMENTS (default: the current repository)

Goal: in one pass, produce the map a new architect needs in week one. Read, do not change anything.
Mark every statement as **seen in code** or **inferred**.

1. **Inventory** – build files, modules, deployables. For each service: language, framework, entry
   point, how it is packaged and deployed (Dockerfile, Helm, pipeline files).
2. **Data ownership** – which datastore each service uses (connection config, migrations, entities).
   Flag any datastore touched by more than one service.
3. **Synchronous edges** – HTTP/gRPC clients and their targets (Feign, RestClient, WebClient, gateway
   routes). Note timeouts, retries, circuit breakers, or their absence.
4. **Asynchronous edges** – topics/queues, who publishes, who consumes, message key, delivery guarantee,
   dead-letter handling, schema management.
5. **Consistency mechanisms** – saga or process manager, outbox or CDC, idempotency, distributed locks,
   two-phase commit (flag it).
6. **Cross-cutting** – authentication and service-to-service trust, configuration and secrets, service
   discovery, observability (logs, metrics, traces, alerts), rate limiting.
7. **Critical flows** – trace the two or three flows that make money (for a shop: browse → order → pay →
   ship) across services, step by step.
8. **Risk list** – single points of failure, shared databases, chatty sync chains, missing idempotency,
   unbounded retries, no back-pressure, missing tests around money, secrets in the repo.

## Output (Turkish)

- A service table (service, owner data, sync deps, async in/out).
- A dependency diagram in Mermaid.
- The critical flows as numbered sequences.
- Top risks ranked, each with the evidence (`file:line`) and a first step to reduce it.
- Questions to ask the team that the code cannot answer (SLOs, on-call, deploy frequency, incidents).

Offer to save the result under `docs/architecture/`.
