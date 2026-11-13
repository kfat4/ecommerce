# Lessons (gotchas and verified facts)

Format: `YYYY-MM-DD` – problem → cause → fix. `verified:` says how we know.

## Spring Boot 4 / Jackson 3

- 2026-11-09 – Jackson is v3 in Boot 4: import `tools.jackson.databind.ObjectMapper`, not
  `com.fasterxml.jackson.databind`. verified: compiles.
- 2026-11-09 – Boot 4 starters are modular: web is `spring-boot-starter-webmvc`, Kafka is
  `spring-boot-starter-kafka`, Elasticsearch is `spring-boot-starter-elasticsearch`. verified: build.
- 2026-11-11 – `spring.config.import` makes the imported file win over the importing file. For defaults a
  service must be able to override, add a low-precedence property source with an
  `EnvironmentPostProcessor` instead (see `ObservabilityDefaultsEnvironmentPostProcessor`). verified: unit test.
- 2026-11-11 – Property names for OTLP in Boot 4.0.9: `management.opentelemetry.tracing.export.otlp.endpoint`,
  `management.opentelemetry.logging.export.otlp.endpoint`, `management.logging.export.otlp.enabled`.
  verified: read from the Boot jars' configuration metadata.

## OpenTelemetry

- 2026-11-11 – Boot builds the OTel log exporter but does NOT attach Logback to it. You need
  `opentelemetry-logback-appender-1.0` plus `OpenTelemetryAppender.install(openTelemetry)`
  (done in `OpenTelemetryLogbackAutoConfiguration`). verified: unit test of the auto-configuration.
- 2026-11-11 – Boot 4.0.9 manages OTel SDK 1.55.0; the matching instrumentation release is
  2.21.0-alpha. Mixing versions breaks at runtime. verified: instrumentation BOM imports opentelemetry-bom 1.55.0.
- 2026-11-11 – Record business metrics after commit (`TransactionalMetrics.afterCommit`); otherwise a
  rollback followed by Kafka redelivery counts the event twice.

## Elasticsearch

- 2026-11-10 – Java client 9.x: highlight fields are `NamedValue.of("name", HighlightField.of(f -> f))`;
  the old `fields(f -> f.name(..))` form no longer exists. verified: compiles + query test.
- 2026-11-10 – Do not delete documents when using external versioning: `index.gc_deletes` (60 s) forgets
  the version, and a late older event would resurrect the document. Write a tombstone instead.
- 2026-11-11 – Graylog 6.3 does not support Elasticsearch 8+. That is one reason Graylog/OpenSearch were
  removed in favour of ES + Kibana (ADR-0007).
- 2026-11-11 – With `action.auto_create_index` restricted, OTel data streams need
  `+logs-*,+traces-*,+metrics-*,+.*` allowed or the collector cannot write. unverified (never run).

## MongoDB

- 2026-11-10 – Multi-document transactions need a replica set, even with one node (`--replSet rs0`), and
  the client URI needs `directConnection=true` for a single-node set. unverified at runtime.

## Build and tooling

- 2026-11-11 – `./mvnw -rf :module` fails if `common-lib` is not installed in `~/.m2`; run the full
  reactor or `-pl <module> -am`.
- 2026-11-14 – `*IT` tests run in `verify` through failsafe. Declaring the plugin in the root pom is enough:
  spring-boot-starter-parent's pluginManagement supplies the executions and `classesDirectory`.
  verified: effective-pom.
- 2026-11-14 – `@Testcontainers(disabledWithoutDocker = true)` only skips when Docker is absent. If the
  socket exists but the daemon hangs, the test JVM blocks for minutes. verified: observed locally.
- 2026-11-11 – YAML: `targets: [host:9093]` is a parse error in strict parsers; quote it.
- 2026-11-11 – Verify image tags exist before pinning (Docker Hub API or registry). Tags used:
  `elasticsearch:9.2.8`, `kibana:9.2.8`, `elastic/elastic-agent:9.2.8`, `prom/prometheus:v3.15.1`,
  `prom/alertmanager:v0.34.2`, `grafana/grafana:13.0.13`. verified: tags existed on 2026-11-11.
  Also verified on 2026-11-14: `maven:3.9-eclipse-temurin-25`, `eclipse-temurin:25-jre`, `postgres:17-alpine`,
  `mongo:7.0`, `redis:7.4-alpine`, `apache/kafka:4.3.2`, `wiremock/wiremock:3.13.2`, `kafbat/kafka-ui:v1.5.0`.
- 2026-11-14 – GitHub Actions are pinned by commit SHA in `ci.yml` (checkout v7.0.1, setup-java v6.2.0,
  upload-artifact v7.0.2). Resolve a tag's SHA with `gh api repos/<owner>/<repo>/commits/<tag> --jq .sha`.
- 2026-11-11 – zsh treats `--include=*.java` and `?` in unquoted arguments as globs; quote them. zsh also
  does not word-split unquoted variables (`set -- $pair` keeps one argument); use `while read -r a b`.
