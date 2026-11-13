---
paths:
  - "services/**/src/main/java/**/*.java"
  - "libs/**/src/main/java/**/*.java"
---

# Java service code

- Package by layer inside a service: `api` (controllers + DTO records), `application` (use cases,
  transactions), `domain` (entities, repositories), plus feature packages such as `saga`, `outbox`, `client`.
- Controllers stay thin: validate, call one application method, map to a DTO. Never return entities.
- `@Transactional` belongs on application services. Keep transactions short: no HTTP or Kafka calls
  inside one. Fetch remote data first, then open the transaction (see `OrderService.placeOrder`).
- Constructor injection only. No field injection, no Lombok.
- DTOs, events and config properties are `record`s. Use sealed interfaces + exhaustive `switch` for
  closed sets of message types (see `ProductEvent`).
- Errors: throw `BusinessException`, `ResourceNotFoundException` or `ServiceUnavailableException`;
  `ApiExceptionHandler` turns them into RFC 7807 problem responses. Do not build error JSON by hand.
- Jackson is version 3: `tools.jackson.databind.ObjectMapper`, not `com.fasterxml`.
- Config comes from `@ConfigurationProperties` records with defaults; values that differ per environment
  are read from environment variables in `application.yml`.
- Logs: parameterised SLF4J, include the business id (`order={}`), never log tokens, passwords or
  personal data. `traceId` and `correlationId` are added automatically.
- A comment explains *why* (the trade-off, the failure it prevents), not what the next line does.
