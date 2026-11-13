# ADR-0007: Gözlemlenebilirlik: OTLP + Elastic (log, trace), Prometheus + Grafana (metrik)

- **Durum:** Kabul edildi
- **Tarih:** 2026-11-11
- **İlgili kod:** `libs/observability-lib/`, `infra/observability/`

## Bağlam

Dokuz servis ve asenkron akışlarla "sipariş neden takıldı?" sorusu tek bir log dosyasından cevaplanamaz.
Üç sinyal gerekir ve birbirine bağlanabilmelidir: metrik (bir sorun var mı), trace (nerede), log (neden).
Kısıt: yalnızca ücretsiz araçlar. Önceki kurulum Graylog + OpenSearch (log) ve Jaeger (trace) idi; metrik
toplayan bir sunucu yoktu ve üç ayrı arayüz arasında bağlantı zayıftı.

## Seçenekler

1. **Grafana yığını** (Loki, Tempo, Prometheus, Grafana) – tek arayüz, güçlü korelasyon, yaygın.
2. **Elastic** (Elasticsearch + Kibana) log ve trace için, Prometheus + Grafana metrik için.
3. **Mevcut araçları koruyup Prometheus eklemek** – en az değişiklik, üç arayüz.
4. **Hepsi bir arada ürün** (SigNoz).

## Karar

Seçenek 2 (proje sahibinin tercihi). Elasticsearch arama için zaten yığındaydı.

- Uygulamalar satıcıya özgü ajan kullanmaz; **OTLP** konuşur. Log ve trace, EDOT collector üzerinden
  Elasticsearch'e yazılır. Arka uç değişirse yalnızca collector yapılandırması değişir.
- **Metrikler OTLP ile değil, Prometheus'un çekmesiyle** toplanır. Gerekçe: Micrometer'ın doğal metrik adları,
  hazır exemplar desteği (metrikten trace'e geçiş), `up == 0` ile ölü replikanın kendiliğinden görünmesi
  ve Eureka üzerinden otomatik hedef keşfi.
- Tüm ayarlar `observability-lib` içinde ve **en düşük öncelikle** yüklenir; servis ya da ortam ezebilir.
- Actuator istekleri ve Spring Security filtre adımları trace'e yazılmaz (gürültü).
- İş metrikleri transaction commit edildikten sonra kaydedilir.

## Sonuçlar

- Olumlu: üç sinyal `trace_id` ve `correlationId` ile bağlanır; uygulama kodu arka uçtan bağımsızdır.
- Olumsuz: iki arayüz (Kibana ve Grafana). Elasticsearch yerelde hem arama hem gözlem verisi taşır; üretimde
  ayrı kümeler şarttır, yoksa bir log seli ürün aramasını yavaşlatır. Bellek ihtiyacı yüksektir.
- **Doğrulanmadı:** yığın henüz çalıştırılmadı. Collector yapılandırması, alarm ifadeleri ve dashboard'lar
  yalnızca statik olarak denetlendi.

## Ne zaman yeniden düşünülür

Log hacmi Elasticsearch maliyetini taşınmaz kılarsa (Loki çok daha ucuzdur) ya da ekip tek arayüz isterse
Grafana yığınına geçilir. OTLP sayesinde bu, uygulama değişikliği gerektirmez.
