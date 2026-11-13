# Open questions

| Since | Question | Who decides | Notes |
|---|---|---|---|
| 2026-11-14 | Which roadmap phase first? | User | Recommendation: phase 1 (run it, CI, ITs) before adding features. |
| 2026-11-14 | Stay on Eureka or move discovery to Kubernetes DNS when Helm charts are added? | ADR | Affects Prometheus SD and the gateway `lb://` routes. |
| 2026-11-14 | Schema registry (Avro/Protobuf) or keep JSON records in `common-lib`? | ADR | `common-lib` couples release cycles; a registry removes that but adds an operational component. |
| 2026-11-11 | Metrics: keep Prometheus pull or switch to OTLP push through the collector? | User | User originally picked OTLP for all three signals; pull was chosen for exemplars and Micrometer naming. |
