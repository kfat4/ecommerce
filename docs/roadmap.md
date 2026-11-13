# Yol Haritası

Her faz hem projeye bir yetenek ekler hem de sana bir mimarlık konusu öğretir. Sıra bilinçlidir: önce
elimizdekinin gerçekten çalıştığını kanıtlarız, sonra alan ekleriz, sonra platformu büyütürüz.

Her faz aynı döngüyle yürür: `solution-architect` tasarlar ve ADR yazar → sen onaylarsın →
`java-backend-developer` uygular → `test-engineer` ve `code-reviewer` denetler → `tech-mentor` sana anlatır.

| Faz | Başlık | Durum |
|---|---|---|
| 0 | Temel: 9 servis, saga, outbox, idempotency, arama, gözlemlenebilirlik | tamam (birim testleri geçiyor, Docker'da denenmedi) |
| 1 | Gerçek yap: çalıştır, test et, CI | sürüyor (CI, IT bağlantısı ve deneme betiği yazıldı; henüz hiçbiri çalışmadı) |
| 2 | Satın alma yolculuğu: sepet, fiyat, checkout | bekliyor |
| 3 | Sipariş sonrası: kargo, iade, gerçek bildirim | bekliyor |
| 4 | Sözleşmeler ve API olgunluğu | bekliyor |
| 5 | Platform: Kubernetes, CD, gizli bilgi yönetimi | bekliyor |
| 6 | Güvenlik sertleştirme | bekliyor |
| 7 | Ölçek ve dayanıklılık kanıtı | bekliyor |

## Faz 1 – Gerçek yap

Şu an kodun derlendiğini ve birim testlerinin geçtiğini biliyoruz. Çalıştığını bilmiyoruz.

- Yığını Docker'da ayağa kaldır, uçtan uca bir sipariş ver, çıkan hataları düzelt.
- Kibana'da trace ve log'u, Grafana'da metrikleri gör; alarm kurallarını gerçek veriyle doğrula.
- Failsafe eklentisini bağla; `*IT` testleri `./mvnw verify` ile çalışsın.
- Uçtan uca akış testi: sipariş → stok → ödeme → onay (Testcontainers ile Kafka ve PostgreSQL).
- GitHub Actions: derle, birim testi, IT, imaj oluştur.
- `Makefile` ya da görev betikleri: `make up`, `make test`, `make demo`.

**Öğreneceğin:** test piramidi mikroserviste nasıl değişir, CI tasarımı, "bende çalışıyor" ile "çalışıyor"
arasındaki fark, bir sistemi ilk kez ayağa kaldırırken hata ayıklama.

## Faz 2 – Satın alma yolculuğu

- **cart-service** (Redis, TTL): sepete ekle/çıkar, misafir sepetini girişte birleştir.
- **pricing-service**: liste fiyatı, kampanya, kupon, vergi. Fiyat katalogdan ayrılır.
- **Checkout**: sepet → fiyatın sunucuda yeniden hesaplanması → sipariş. İstemciden gelen fiyata asla güvenilmez.
- Kupon kullanımı sagaya adım olarak eklenir (kullan / geri al).
- Katalogda varyant (renk, beden) ve kategori ağacı.
- **customer-service**: adresler, tercihler.

**Öğreneceğin:** servis sınırı çizmek (neden sepet ≠ sipariş), fiyatın anlık görüntüsünü almak, Redis veri
modelleme, sagaya adım eklemek ve telafisini tasarlamak, yarış durumları (son ürün, tek kullanımlık kupon).

## Faz 3 – Sipariş sonrası

- **shipping-service**: gönderi oluşturma, kargo firması webhook'ları, durum olayları.
- **İade ve geri ödeme sagası**: iade talebi → onay → stok iadesi → geri ödeme. `LatePaymentNeedsRefund`
  alarmının arkasındaki akış burada kapanır.
- Ödeme sağlayıcı webhook'u: asenkron ödeme sonucu, imza doğrulama, tekrar oynatma koruması.
- Bildirim: gerçek e-posta (MailHog ile yerelde), şablonlar, kullanıcı tercihleri, yeniden deneme.
- Sipariş durum geçmişi (kim, ne zaman, neden).

**Öğreneceğin:** uzun süren süreçler, dış sistem entegrasyonu ve anti-corruption layer, webhook güvenliği,
süreç yöneticisi ile koreografi arasında seçim, para iadesi gibi geri alınamaz işlemler.

## Faz 4 – Sözleşmeler ve API olgunluğu

- OpenAPI belgeleri (springdoc) ve gateway'de birleşik görünüm.
- API sürümleme politikası ve kullanımdan kaldırma (deprecation) süreci.
- Tüketici güdümlü sözleşme testleri (Spring Cloud Contract ya da Pact).
- Şema kaydı: Avro ya da Protobuf, uyumluluk kuralları. `common-lib` içindeki sözleşmelerden çıkış planı.
- Outbox için CDC (Debezium) seçeneği; polling ile ölçülerek karşılaştırma.
- BFF: ürün sayfası için toplama uç noktası.

**Öğreneceğin:** ekipler arası bağımsız yayın, geriye uyumlu değişim, ortak kütüphanenin bedeli, API'yi ürün
gibi yönetmek.

## Faz 5 – Platform

- Her servis için Helm chart; probe'lar, kaynak sınırları, HPA, PodDisruptionBudget.
- Eureka yerine Kubernetes DNS (ADR ile karar).
- ConfigMap + gizli bilgi yönetimi (Vault ya da External Secrets).
- İmaj yayını (GHCR), sürüm etiketleme, SBOM.
- Argo CD ile GitOps; canary yayın.
- Yerelde kind/minikube ile deneme.

**Öğreneceğin:** on iki faktör, sıfır kesintili yayın, geriye uyumlu veritabanı göçü (expand/contract),
yapılandırma ile kodun ayrılması, yayın stratejileri.

## Faz 6 – Güvenlik

- RS256 + JWKS; anahtar döndürme. Refresh token ve token iptali.
- Servisler arası kimlik: client-credentials ya da mTLS.
- Yetkilendirme denetimi: her uç noktada sahiplik kontrolü (IDOR taraması).
- Bağımlılık, imaj ve gizli bilgi taraması CI'da.
- Denetim kaydı (audit log) ve KVKK: veri silme/anonimleştirme akışı olaylarla.
- Hız sınırı katmanları: kullanıcı, IP, uç nokta.

**Öğreneceğin:** tehdit modelleme, sıfır güven, kişisel verinin olaylarda yayılması sorunu.

## Faz 7 – Ölçek ve dayanıklılık kanıtı

- Yük testi (k6 ya da Gatling): kampanya günü senaryosu, darboğaz bulma.
- Kaos deneyleri: Kafka'yı durdur, veritabanını yavaşlat, bir replikayı öldür; alarmlar çalıyor mu?
- SLO ve hata bütçesi; burn-rate alarmları.
- Okuma replikası, önbellek stratejileri (cache-aside, stampede koruması), sıcak anahtar sorunu.
- Kafka: partition sayısı, tüketici gecikmesi, yeniden dengeleme, sıra garantisi sınırları.
- Runbook'lar: her kritik alarm için "ne yapmalı" belgesi.

**Öğreneceğin:** kapasite planlama, darboğaz analizi, olay (incident) yönetimi, "ölçeklenir" demek yerine
ölçmek.

## Nasıl ilerleriz

Bir faza başlamak için: `/feature-team Faz 1: yığını ayağa kaldır ve CI ekle` yazman yeterli. Fazlar
küçük dilimlere bölünür; her dilim ayrı bir dalda, ayrı bir PR ile biter.
