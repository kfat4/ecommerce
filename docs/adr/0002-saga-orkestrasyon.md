# ADR-0002: Sipariş süreci için orkestrasyon tabanlı saga

- **Durum:** Kabul edildi
- **Tarih:** 2026-11-08
- **İlgili kod:** `services/order-service/.../saga/OrderSagaOrchestrator.java`, `SagaTimeoutScheduler.java`

## Bağlam

Sipariş vermek üç servisi değiştirir: sipariş kaydı, stok rezervasyonu, ödeme. Servis başına veritabanı
(ADR-0001) nedeniyle tek bir ACID transaction kurulamaz. İki aşamalı commit (2PC/XA) Kafka ve çoğu modern
veri deposuyla pratik değildir ve bir katılımcı yavaşladığında herkesi kilitler.

## Seçenekler

1. **Koreografi** – merkezi yönetici yok; her servis olaya tepki verip yeni olay yayınlar. Az bileşen, gevşek
   bağ. Ama süreç hiçbir yerde yazılı değildir: "sipariş neden takıldı?" sorusunun cevabı beş servisin
   koduna dağılır. Adım sayısı arttıkça döngüsel bağımlılık riski büyür.
2. **Orkestrasyon** – bir yönetici (order-service) süreci durum makinesi olarak tutar, katılımcılara komut
   gönderir, cevapları dinler. Süreç tek yerde okunur ve izlenir. Bedeli: yönetici tüm adımları bilir.
3. **Hazır iş akışı motoru** (Temporal, Camunda) – zaman aşımı, yeniden deneme, sürümleme hazır gelir.
   Bedeli: yeni bir altyapı bileşeni ve öğrenme eğrisi.

## Karar

Orkestrasyon; yönetici order-service içinde elle yazılmış bir durum makinesidir (`order_sagas` tablosu).

Gerekçe: üç-dört adımlı, para içeren bir süreçte "şu an hangi adımdayız" sorusunun tek yerde cevabı olması
operasyon için belirleyicidir. Motoru elle yazmak deseni öğretir; gerçek hayatta adım sayısı artınca
seçenek 3 ciddi bir adaydır.

Tasarım kuralları:

- Her işleyici, sipariş değişikliği + saga durumu + bir sonraki komutun outbox satırını **tek transaction**
  içinde yazar.
- Her işleyici mevcut adım/durum ile korunur (`OrderSaga.isAt`); tekrar gelen ya da geç gelen cevap etkisizdir.
- Telafi iş düzeyinde geri almadır (`ReleaseInventoryCommand`), veritabanı rollback'i değildir.
- **Para içeren adımda zaman aşımı başarısızlık sayılmaz.** Ödeme cevap vermezse idempotent komut tekrar
  gönderilir; deneme hakkı bitince saga `STUCK` olur ve insan müdahalesi için alarm çalar.

## Sonuçlar

- Olumlu: süreç okunabilir, test edilebilir (8 birim testi), izlenebilir (`ecommerce_saga_*` metrikleri).
- Olumsuz: order-service katılımcıların komut ve olaylarını bilir. Ara durumlar kullanıcıya görünür
  (`PENDING`, `AWAITING_PAYMENT`); arayüz buna göre tasarlanmalıdır.
- **Açık risk:** ödeme, sipariş iptal edildikten sonra tamamlanırsa müşteriden para çekilmiş, sipariş
  iptal olmuştur. Şu an yalnızca loglanır ve `LatePaymentNeedsRefund` alarmı çalar. Otomatik iade akışı
  yol haritasının 3. fazındadır.

## Ne zaman yeniden düşünülür

Adım sayısı 6-7'yi geçerse, insan onayı gibi günler süren beklemeler gelirse ya da birden fazla saga türü
ortaya çıkarsa bir iş akışı motoruna geçiş değerlendirilir.
