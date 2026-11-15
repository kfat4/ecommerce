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

Yığın ayağa kalktıktan sonra uçtan uca deneme (kayıt, giriş, üç sipariş senaryosu, arama):

```bash
scripts/smoke-test.sh
```

Kısayollar için `make help`.

| Arayüz | Adres | Nasıl açılır |
|---|---|---|
| API gateway | http://localhost:8080 | `make up` (ya da Kubernetes: `make k8s-up`) |
| API konsolu | http://localhost:5500 | `make console` |
| Eureka | http://localhost:8761 | `make up` |
| Kafka UI | http://localhost:8090 | `make up` (Kubernetes: `make k8s-ui`) |
| pgAdmin (PostgreSQL) | http://localhost:5050 | `make up-db-ui` (Kubernetes: `make k8s-ui`) – sunucu kayıtlı; compose'da parola `.env` içindeki `POSTGRES_PASSWORD`, Kubernetes'te sorulmaz |
| mongo-express (MongoDB) | http://localhost:8091 | `make up-db-ui` (Kubernetes: `make k8s-ui`) |
| RedisInsight (Redis) | http://localhost:5540 | `make up-db-ui` (Kubernetes: `make k8s-ui`) – ilk açılışta EULA ekranını onayla, bağlantı sonra görünür |
| Grafana | http://localhost:3000 | `make up-obs` |
| Kibana | http://localhost:5601 | `make up-obs` |
| Prometheus | http://localhost:9090 | `make up-obs` |

Kubernetes'te veri arayüzleri yalnızca 127.0.0.1'e port-forward edilir; nedeni
[docs/architecture/03-kubernetes.md](docs/architecture/03-kubernetes.md) bölüm 6'da.

Postman koleksiyonu: [tools/postman/ecommerce.postman_collection.json](tools/postman/ecommerce.postman_collection.json)
(Postman → Import; önce `1. Auth` klasöründeki iki giriş isteğini çalıştır).

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
