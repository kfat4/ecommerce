# Gözlemlenebilirlik (Log · Trace · Metric)

Bu yığındaki her araç ücretsiz:

| Sinyal | Uygulamadan çıkış | Taşıyıcı | Depolama | Arayüz |
|---|---|---|---|---|
| **Log** | Logback → OpenTelemetry appender (OTLP/HTTP) | EDOT Collector | Elasticsearch `logs-*` | Kibana Discover |
| **Trace** | Micrometer Tracing → OTel SDK (OTLP/HTTP) | EDOT Collector | Elasticsearch `traces-*` | Kibana APM |
| **Metric** | Micrometer → `/actuator/prometheus` | Prometheus (pull, Eureka SD) | Prometheus TSDB (7 gün) | Grafana |
| **Alarm** | – | Prometheus kuralları | – | Alertmanager / Grafana |

```
                 ┌──────────── OTLP :4318 (log + trace) ───────────┐
 order-service ──┤                                                  ▼
 product-...   ──┤                                     otel-collector (EDOT)
 api-gateway   ──┘                                      memory_limiter → batch → elasticapm
      ▲                                                           │
      │ scrape /actuator/prometheus                               ▼
      │ (hedefler Eureka'dan bulunur)                 Elasticsearch ──▶ Kibana :5601
 Prometheus :9090 ──▶ Grafana :3000                    (logs-*, traces-*)   Discover · APM
      │                    │ exemplar (trace_id) ───────────────────────────▲
      └──▶ Alertmanager :9093
```

## Çalıştırma

```bash
docker compose up -d --build                      # çekirdek + EDOT collector (log/trace en baştan toplanır)
docker compose --profile observability up -d      # + Kibana, Prometheus, Grafana, Alertmanager
```

| Arayüz | Adres | Not |
|---|---|---|
| Grafana | http://localhost:3000 | `admin` / `GRAFANA_ADMIN_PASSWORD` (varsayılan `admin`), dashboardlar "E-commerce" klasöründe |
| Kibana | http://localhost:5601 | Observability → **APM** (trace), **Discover** (log) |
| Prometheus | http://localhost:9090 | Status → Targets: Eureka'dan bulunan replikalar |
| Alertmanager | http://localhost:9093 | Aktif alarmlar |

IDE'den çalıştırılan servisler de varsayılan olarak `http://localhost:4318` adresine gönderir. Collector'ın ayakta olması yeterlidir.

## Üç sinyali birbirine bağlamak

1. **Metrik → Trace.** Grafana'da p95 gecikme panelindeki noktalar *exemplar*'dır. Micrometer, histogram örneğine o isteğin `trace_id`'sini ekler. Bir noktaya tıklayınca "Kibana APM'de aç" ile tam o yavaş isteğin trace'i açılır.
2. **Trace → Log.** Kibana APM'de bir trace'in **Logs** sekmesi, aynı `trace_id`'yi taşıyan logları gösterir. OTel appender, aktif span'i her log kaydına otomatik ekler.
3. **İş akışı → Log.** Discover'da `attributes.correlationId : "<id>"` yazınca bir siparişin gateway → order → inventory → payment yolculuğu tek listede görünür. Bu çalışır, çünkü MDC alanları log attribute'u olur ve correlationId Kafka header'larıyla taşınır.

Kibana Discover'ı ilk açışta `logs-*` için bir data view oluştur. Faydalı alanlar şunlar:
- `resource.attributes.service.name`
- `severity_text`
- `body.text`
- `trace_id`
- `attributes.correlationId`

## Nerede ne yapılandırılıyor

| Dosya | İçerik |
|---|---|
| `libs/observability-lib` | Her servisin tek bağımlılığı: actuator, OTel, Prometheus registry, OTel Logback appender. Ayrıca ortak `logback-spring.xml` dosyası. |
| `.../ecommerce-observability-defaults.yml` | OTLP endpoint, sampling, histogramlar ve `application` etiketi. **En düşük öncelikle** yüklenir, servis veya ortam değişkeni her zaman ezer. |
| `otel-collector/otelcol.yml` | Elastic'in 9.2 gateway örneği temel alındı. APM UI için `elasticapm` processor ve connector kullanılıyor; APM Server gerekmez. |
| `prometheus/prometheus.yml` | Eureka service discovery: `--scale` ile eklenen replikalar otomatik izlenir. |
| `prometheus/rules/ecommerce-alerts.yml` | Alarm kuralları (aşağıda) |
| `alertmanager/alertmanager.yml` | Gruplama, inhibit kuralları ve bildirim kanalı örneği (Slack) |
| `grafana/dashboards/*.json` | İki hazır dashboard. UI'da düzenleyip JSON'u buraya geri koyabilirsin. |

Ortam değişkenleri: `OTLP_ENDPOINT`, `OTLP_LOGS_ENABLED`, `TRACING_SAMPLING`, `DEPLOYMENT_ENVIRONMENT`.

## İş metrikleri (kodda)

| Metrik | Nerede | Anlamı |
|---|---|---|
| `ecommerce_outbox_pending`, `ecommerce_outbox_oldest_age_seconds` | `OutboxMetrics` (PostgreSQL ve Mongo relay) | Kafka'ya henüz gitmemiş olaylar |
| `ecommerce_outbox_published_total` / `_failed_total{topic}` | aynı | Relay verimi ve hataları |
| `ecommerce_saga_started_total`, `ecommerce_saga_finished_total{outcome}` | `SagaMetrics` | Onaylanan veya iptal edilen siparişler |
| `ecommerce_saga_duration_seconds{outcome}` | aynı | Siparişten nihai duruma geçen süre (histogram) |
| `ecommerce_saga_compensations_total{trigger}`, `_timeouts_total{step}` | aynı | Telafi ve timeout'lar |
| `ecommerce_saga_stuck_total`, `ecommerce_saga_late_payments_total` | aynı | İnsan müdahalesi gerektiren durumlar |
| `ecommerce_inbox_duplicates_total{consumer}` | `ProcessedMessageStore` | At-least-once teslimatta atlanan tekrarlar |
| `ecommerce_idempotency_requests_total{outcome}` | `IdempotencyFilter` | `executed`, `replayed`, `in_progress`, `payload_mismatch`, `missing_key` |
| `ecommerce_search_projection_total{event,result}` | `ProductProjection` | `applied` / `stale` (external versioning ile reddedilen) |

Saga metrikleri **transaction commit edildikten sonra** yazılır (`TransactionalMetrics.afterCommit`). Rollback olup Kafka'dan tekrar gelen bir mesaj iki kez sayılmaz.

## Alarmlar

| Grup | Alarm | Önem |
|---|---|---|
| availability | `ServiceInstanceDown`, `ApiGatewayMissing` | critical |
| http-red | `HighHttpErrorRate` (5xx > %5), `HighHttpLatencyP95` (> 1 sn) | critical / warning |
| resilience | `CircuitBreakerOpen`, `DatabasePoolSaturated`, `JvmHeapHigh` | warning |
| messaging | `OutboxBacklogGrowing`, `OutboxStalled`, `KafkaConsumerLagHigh`, `KafkaListenerFailures`, `InboxDuplicatesSpike` | warning, critical, info |
| order-saga | `SagaStuck`, `LatePaymentNeedsRefund`, `HighOrderCancellationRate`, `SagaSlow` | critical / warning |
| telemetry-pipeline | `OtelCollectorExportFailing` | warning |

Alertmanager yerelde bildirim göndermez; alarmlar arayüzde görünür. Bildirim almak için `alertmanager.yml` içindeki Slack örneğini aç. Webhook URL'sini dosyadan oku ve repoya koyma.

## Tasarım kararları

- **Neden Elastic?** Arama için Elasticsearch zaten yığında. Log ve trace aynı motorda tutulur, Kibana tek arayüz olur. Elastic Basic lisansı ücretsizdir ve APM UI dahildir.
- **Neden EDOT Collector?** Uygulamalar Elastic'e özgü bir ajan kullanmaz, standart OTLP konuşur. Yarın Tempo, Loki veya Jaeger'a geçmek yalnızca collector yapılandırması değiştirmek demektir.
- **Metrikler neden OTLP ile değil Prometheus pull ile?**
  - `/actuator/prometheus` Micrometer'ın doğal formatıdır; hazır sorgu ve dashboard örnekleri bu isimlerle yazılır.
  - Exemplar (metrik → trace bağlantısı) Prometheus registry ile hazır gelir.
  - Pull modelde `up == 0` ölü replikayı kendiliğinden gösterir.
  - Eureka SD sayesinde ölçeklenen replikalar otomatik bulunur.
- **Actuator istekleri trace edilmez.** Prometheus her 15 saniyede tüm replikaları çağırır. `ActuatorObservationFilterAutoConfiguration` bu istekleri ayıklar, yoksa APM gerçek trafiği gömer. Spring Security filter span'leri de kapalıdır.
- **Graylog, OpenSearch ve Jaeger kaldırıldı.** Log ve trace artık Elasticsearch + Kibana'da. Uygulamada GELF bağımlılığı kalmadı.

## Production notları

- **Sampling.** `TRACING_SAMPLING=0.05..0.1` ya da collector'da tail sampling kullan. Tail sampling hatalı veya yavaş trace'lerin hepsini tutar.
- **Ayrı cluster.** Log ve trace verisi ayrı bir Elasticsearch cluster'ında olmalı. Bir log seli ürün aramasını yavaşlatmamalı.
- **Retention.** Data stream lifecycle ile saklama süresi koy (ör. log 7 gün, trace 3 gün):
  `PUT _data_stream/logs-*/_lifecycle {"data_retention": "7d"}`
- **Güvenlik.** Elasticsearch'te TLS ve API key kullan (`api_key: ${env:ELASTIC_API_KEY}`). Grafana ve Kibana için SSO veya güçlü parola kullan.
- **Hassas veri.** Loglara kişisel veri yazma. Gerekirse collector'da `transform` veya `attributes` processor ile maskele.
