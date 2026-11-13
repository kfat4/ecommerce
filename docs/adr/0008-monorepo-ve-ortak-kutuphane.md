# ADR-0008: Monorepo ve ortak kütüphane

- **Durum:** Kabul edildi
- **Tarih:** 2026-11-08
- **İlgili kod:** kök `pom.xml`, `libs/common-lib/`, `libs/observability-lib/`

## Bağlam

Dokuz servis aynı altyapı desenlerini (outbox, inbox, idempotency, güvenlik, hata biçimi) ve aynı mesaj
sözleşmelerini kullanır. Bunların her serviste kopyalanması tutarsızlık üretir; ortak bir kütüphane ise
servisleri birbirine bağlar.

## Seçenekler

1. **Monorepo + ortak kütüphane** – tek derleme, atomik değişiklik, kolay yeniden düzenleme. Bedeli: ortak
   kütüphane değişince herkes yeniden derlenir ve yayınlanır.
2. **Servis başına repo + sürümlü kütüphaneler** – gerçek bağımsızlık. Bedeli: sürüm matrisi, çapraz repo
   değişikliklerinde koordinasyon.
3. **Ortak kod yok, kopyala** – tam bağımsızlık, kaçınılmaz sapma.

## Karar

Seçenek 1: tek Maven çoklu modül deposu. `common-lib` her entegrasyonu **isteğe bağlı** otomatik
yapılandırma olarak sunar (`ecommerce.outbox.enabled` gibi); servis yalnızca kullandığını açar.
`observability-lib` web yığınından bağımsızdır, böylece reaktif gateway de kullanabilir.

Önemli ayrım: monorepo bir **kaynak kod düzeni**dir. Çalışma zamanında her servis kendi jar'ı, kendi imajı
ve kendi replikalarıdır.

## Sonuçlar

- Olumlu: tek kişilik ya da küçük ekip için en hızlı yol; tutarlılık kendiliğinden gelir.
- Olumsuz: `common-lib` içindeki mesaj sözleşmeleri, tüm servislerin aynı anda aynı sürümü kullandığı
  varsayımını teşvik eder. Gerçekte servisler ayrı ayrı yayınlanır; bu yüzden sözleşme değişikliği kuralı
  (yalnızca isteğe bağlı alan ekle) kütüphane ne derse desin geçerlidir. Tek Dockerfile her imaj için tüm
  projeyi derler.

## Ne zaman yeniden düşünülür

Birden fazla ekip farklı takvimlerle yayın yapmaya başladığında: sözleşmeler şema kaydına taşınır (Avro ya
da Protobuf), altyapı kodu sürümlü bir "platform starter" olur, CI yalnızca değişen servisi derler.
Yol haritası faz 4.
