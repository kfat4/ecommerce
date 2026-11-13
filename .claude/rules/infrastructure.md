---
paths:
  - "docker-compose.yml"
  - "Dockerfile"
  - "infra/**"
  - ".env.example"
---

# Infrastructure files

- After editing `docker-compose.yml` run `docker compose config -q` (and with `--profile observability`).
- Pin image tags to an exact version. Check that the tag exists before using it; do not guess.
- Services get no host ports (only the gateway and dev UIs do), so they can be scaled with `--scale`.
- Every new variable gets a placeholder in `.env.example` and a safe local default in compose.
- YAML: quote `host:port` strings inside flow sequences (`["alertmanager:9093"]`).
- Prometheus label names: `application` (Micrometer tag) and `service`/`instance` (from Eureka SD).
  New alert rules go in `infra/observability/prometheus/rules/` with `severity` and a `summary`.
- Grafana dashboards are provisioned from JSON in `infra/observability/grafana/dashboards/`.
