---
name: new-microservice
description: Scaffold a new Spring Boot microservice in this monorepo, wired like the existing ones (pom from template, config, gateway route, compose entry, Prometheus, docs).
argument-hint: "<service-name> <one-line purpose>"
disable-model-invocation: true
---

# New microservice

Request: $ARGUMENTS

A new service is an architectural decision. Before creating files, confirm there is an ADR or an approved
design that answers: which data does it own, why can't an existing service own it, how does it talk to
the others (events first), and who operates it. If not, hand over to `solution-architect` first.

## Checklist

1. **pom** – from the repo root:
   `python3 infra/templates/gen-pom.py <name> "<description>" <groupId:artifactId[:scope]> ...`
   then add `<module>services/<name></module>` to the root `pom.xml`.
2. **Application** – `services/<name>/src/main/java/com/ecommerce/<pkg>/<Name>ServiceApplication.java`
   with packages `api`, `application`, `domain`.
3. **application.yml** – port (next free 808x), `spring.application.name`, `spring.config.import:
   classpath:ecommerce-common.yml`, datastore URL from environment variables, and the opt-ins it needs
   (`ecommerce.outbox.enabled`, `ecommerce.inbox.enabled`, `ecommerce.idempotency.*`).
4. **Data** – its own database: add it to `infra/postgres/init-databases.sql` (PostgreSQL) and a Flyway
   `V1__*.sql` including `outbox_events` / `processed_messages` if it publishes or consumes.
5. **Gateway** – a route in `RoutesConfig` and security rules in the gateway `SecurityConfig`, only if the
   service has a public API.
6. **Compose** – a service entry using the `*app` anchor in `docker-compose.yml`; run
   `docker compose config -q`.
7. **Observability** – nothing to add for logs, traces and scraping (observability-lib + Eureka SD);
   add business metrics and an alert if the service owns a flow someone must watch.
8. **Tests** – at least one unit test of the core behaviour.
9. **Docs** – service table in `CLAUDE.md`, `docs/architecture/01-genel-bakis.md`, and the ADR.
10. **Verify** – `./mvnw -pl services/<name> -am test`.
