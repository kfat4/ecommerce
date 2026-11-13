---
paths:
  - "**/src/test/**"
---

# Tests

- Unit tests are plain JUnit 5 + AssertJ + Mockito, no Spring context, and must run without Docker.
- Test behaviour through the public surface. Name tests as sentences (`duplicateReplyIsIgnored`).
- For every messaging handler test the unhappy paths: duplicate, out-of-order, late reply, timeout.
- Prefer a small in-memory fake over deep mocking when the collaborator has real semantics
  (see the versioned in-memory index in `ProductProjectionTest`).
- Integration tests end in `IT`, use Testcontainers with `disabledWithoutDocker = true`, and exercise the
  real thing (real PostgreSQL with Flyway, real Elasticsearch). They are not part of plain `./mvnw test`.
- Metrics are behaviour too: assert counters with `SimpleMeterRegistry` when a metric backs an alert.
- A bug fix starts with a test that fails for the reported reason.
