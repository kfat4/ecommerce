# ADR-0004: İki katmanlı idempotency: HTTP anahtarı ve inbox

- **Durum:** Kabul edildi
- **Tarih:** 2026-11-08
- **İlgili kod:** `libs/common-lib/.../idempotency/IdempotencyFilter.java`, `.../inbox/ProcessedMessageStore.java`

## Bağlam

Dağıtık sistemde "bir kez gönder" yoktur. İstemci zaman aşımında yeniden dener, gateway GET'leri tekrarlar,
Kafka yeniden dengelemede mesajı tekrar teslim eder, outbox relay tekrar gönderir. "Tam bir kez teslimat"
ağ düzeyinde mümkün değildir; ulaşılabilir hedef, tekrarın **etkisiz** olmasıdır.

Sonuçları ağır iki örnek: aynı siparişin iki kez oluşması ve müşteriden iki kez para çekilmesi.

## Seçenekler

1. **Doğal idempotency** – işlemi tekrarı zararsız olacak biçimde tasarlamak ("stoktan 2 düş" yerine "sipariş
   X için 2 ayır"). En sağlamı; her zaman mümkün değil.
2. **İstemci anahtarı (Idempotency-Key)** – istemci her işlem için benzersiz bir anahtar gönderir; sunucu
   ilk sonucu saklar ve tekrarında aynısını döner.
3. **İşlenmiş mesaj tablosu (inbox)** – tüketici mesaj kimliğini iş değişikliğiyle aynı transaction'da
   kaydeder; birincil anahtar çakışırsa atlar.
4. **Yalnızca tekilleştirme önbelleği** (Redis'te "gördüm" işareti) – iş değişikliğiyle atomik değildir;
   işaret yazılıp iş başarısız olursa mesaj kaybolur.

## Karar

Katmanlı savunma:

- **HTTP sınırında** seçenek 2: `POST /api/v1/orders` için `Idempotency-Key` zorunludur. Kayıt Redis'te
  tutulur (24 saat), kullanıcıya göre ayrılır. Aynı anahtar farklı gövdeyle gelirse 422; işlem sürüyorsa
  409 ve `Retry-After`; tamamlandıysa saklanan cevap `Idempotent-Replayed: true` ile döner. 5xx cevaplar
  saklanmaz ki istemci yeniden deneyebilsin.
- **Veritabanında ikinci çizgi**: `(customer_id, idempotency_key)` üzerinde tekil kısıt. Redis kaydı
  kaybolsa ya da iki replika yarışsa bile çift sipariş oluşmaz.
- **Mesaj tüketiminde** seçenek 3: `processed_messages` tablosu, iş değişikliğiyle aynı transaction.
- **Mümkün olan yerde** seçenek 1: stok fonksiyonları sipariş kimliğiyle çalışır; ödeme sağlayıcısına
  `Idempotency-Key: order-<id>` gönderilir; arama indeksi sürüm numarasıyla yazar.

## Sonuçlar

- Olumlu: yeniden deneme her katmanda güvenlidir; bu da zaman aşımı ve retry politikalarını cesurca
  kullanmayı mümkün kılar.
- Olumsuz: istemciye yük biner (anahtar üretmek). Inbox tablosu büyür ve temizlenmesi gerekir. Redis
  kesintisinde filtre devre dışı kalırsa yalnızca veritabanı kısıtı korur.
- Operasyonel: `ecommerce_idempotency_requests_total{outcome}` ve `ecommerce_inbox_duplicates_total`
  tekrarların gerçekten yaşandığını gösterir; ani artış bir yeniden dengeleme ya da relay sorununa işarettir.

## Ne zaman yeniden düşünülür

Anahtar saklama süresi (24 saat) iş ihtiyacına göre değişir; ödeme tarafında günler gerekebilir. Çok
yüksek hacimde inbox tablosu yerine partition bazlı offset + sürüm kontrolü değerlendirilir.
