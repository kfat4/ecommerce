# E-commerce Microservices

Spring Boot 4 ve Java 25 ile yazılmış, gerçekçi bir e-ticaret backend'i. İki amacı var: mikroservis
mimarisinin temel desenlerini çalışan kodda göstermek ve AI ajanlarla (Claude Code) geliştirme pratiği için
hazır bir ortam sunmak.

> **Durum:** Kod derleniyor ve birim testleri geçiyor. Yığın henüz Docker'da uçtan uca çalıştırılmadı;
> ilk çalıştırmada yapılandırma hataları çıkması beklenir. Ayrıntı: [docs/roadmap.md](docs/roadmap.md).

## Neler var

| Konu | Nerede |
|---|---|
| Saga (orkestrasyon), telafi, zaman aşımı | `services/order-service/.../saga` |
| Transactional outbox (PostgreSQL ve MongoDB) | `libs/common-lib/.../outbox`, `services/product-service/.../outbox` |
| Idempotent tüketici (inbox) ve HTTP `Idempotency-Key` | `libs/common-lib/.../inbox`, `.../idempotency` |
| CQRS okuma modeli (Elasticsearch) | `services/search-service` |
| API gateway: JWT, hız sınırı, circuit breaker | `services/api-gateway` |
| Dayanıklılık: timeout, retry, circuit breaker, bulkhead | `order-service`, `payment-service` |
| Gözlemlenebilirlik: OTLP log/trace, Prometheus, Grafana, alarmlar | `libs/observability-lib`, `infra/observability` |
| Yatay ölçekleme: durumsuz servisler, `SKIP LOCKED`, consumer group | tüm servisler |

Teknolojiler: Kafka, PostgreSQL (PL/pgSQL), MongoDB, Redis, Elasticsearch, Eureka, Spring Cloud Gateway,
resilience4j, OpenTelemetry, Docker.

## Hızlı başlangıç

Gerekenler: JDK 25, Docker.

```bash
./mvnw test
```

```bash
cp .env.example .env
```

`.env` içindeki `replace-me` değerlerini değiştir, sonra:

```bash
docker compose up -d --build
```

```bash
docker compose --profile observability up -d
```

| Arayüz | Adres |
|---|---|
| API gateway | http://localhost:8080 |
| Eureka | http://localhost:8761 |
| Kafka UI | http://localhost:8090 |
| Grafana | http://localhost:3000 |
| Kibana | http://localhost:5601 |
| Prometheus | http://localhost:9090 |

## Belgeler

- [Mimari genel bakış](docs/architecture/01-genel-bakis.md) ve [hedef mimari](docs/architecture/02-hedef-mimari.md)
- [Mimari karar kayıtları (ADR)](docs/adr/README.md)
- [Yol haritası](docs/roadmap.md)
- [AI ajanlarla geliştirme rehberi](docs/ai/README.md) ve [hafıza sistemi](docs/ai/hafiza-sistemi.md)

## AI ajanlarla çalışmak

Depo Claude Code için yapılandırılmıştır: [CLAUDE.md](CLAUDE.md), `.claude/agents` (8 uzman ajan),
`.claude/skills` (8 iş akışı), `.claude/rules`, bir koruma hook'u ve dosya tabanlı ortak hafıza.

```text
/feature-team <özellik>     tasarım → uygulama → test → inceleme
/map-architecture           tanımadığın bir kod tabanının haritası
/write-adr <başlık>         mimari karar kaydı
```

## Güvenlik notu

Yereldeki varsayılan sırlar ve demo kullanıcıları yalnızca geliştirme içindir. `.env` dosyaları depoya
girmez. Üretim öncesi yapılması gerekenler: [ADR-0006](docs/adr/0006-jwt-ve-servisler-arasi-guven.md) ve yol
haritası faz 6.
