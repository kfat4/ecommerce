# ADR-0003: Transactional outbox (polling), CDC'ye geçiş yolu açık

- **Durum:** Kabul edildi
- **Tarih:** 2026-11-08
- **İlgili kod:** `libs/common-lib/.../outbox/OutboxRelay.java`, `services/product-service/.../outbox/MongoOutboxRelay.java`

## Bağlam

Bir servis hem veritabanını değiştirir hem Kafka'ya mesaj gönderir. Bu iki yazma atomik değildir
("dual write" problemi):

- Önce veritabanı, sonra Kafka: arada süreç ölürse sipariş kaydedilir, olay hiç gitmez. Saga sonsuza dek bekler.
- Önce Kafka, sonra veritabanı: transaction geri alınırsa var olmayan bir siparişin olayı yayınlanmış olur.

## Seçenekler

1. **Outbox + polling** – mesaj, iş verisiyle aynı transaction'da `outbox_events` tablosuna yazılır. Bir
   zamanlayıcı tabloyu okuyup Kafka'ya gönderir. Ek altyapı gerekmez. Bedeli: sorgu yükü ve yarım saniyeye
   varan gecikme.
2. **Outbox + CDC (Debezium)** – tablo sorgulanmaz, veritabanının değişiklik günlüğü (WAL/oplog) okunur.
   Gecikme düşük, yük az. Bedeli: Kafka Connect ve Debezium işletmek gerekir.
3. **Kafka transaction'ları** – yalnızca Kafka'dan Kafka'ya akışta tam bir kez sağlar; veritabanı yazmasını
   kapsamaz. Problemi çözmez.
4. **Listen-to-yourself** – servis önce olayı yayınlar, kendi olayını dinleyip veritabanına yazar. Kendi
   yazdığını hemen okuyamaz; kullanıcıya dönüş karmaşıklaşır.

## Karar

Outbox + polling (seçenek 1).

- PostgreSQL: `FOR UPDATE SKIP LOCKED` ile her replika farklı satırları alır; lider seçimi gerekmez.
- MongoDB: `SKIP LOCKED` yoktur. Eşdeğeri `findAndModify` ile atomik **kiralama**dır (`availableAt` ileri
  alınır, `lockedBy` yazılır). Replika ölürse kira süresi dolar ve başka replika devralır.
- Kafka anahtarı = aggregate kimliği; aynı varlığın olayları aynı partition'da sıralı kalır.

Teslimat garantisi **en az bir kez**dir: relay, Kafka onayından sonra ama `published_at` yazılmadan ölürse
mesaj tekrar gider. Bu nedenle her tüketici idempotent olmak zorundadır (ADR-0004).

## Sonuçlar

- Olumlu: mesaj kaybı olmaz; ek altyapı yok; her veritabanında aynı fikir uygulanabilir.
- Olumsuz: tabloyu sürekli sorgulamak yük getirir; yayınlanan satırların temizlenmesi gerekir (saatlik
  silme / Mongo TTL indeksi). MongoDB tarafında birden fazla replika aynı ürünün iki sürümünü ters sırada
  yayınlayabilir; tüketici sürüm numarasıyla korunur (ADR-0005).
- Operasyonel: `ecommerce_outbox_pending` ve `ecommerce_outbox_oldest_age_seconds` izlenir;
  `OutboxStalled` alarmı Kafka'ya ulaşılamadığını ilk gösteren sinyaldir.

## Ne zaman yeniden düşünülür

Outbox yazma hızı saniyede birkaç yüzü geçerse, olay gecikmesinin yüz milisaniyenin altına inmesi
gerekirse ya da şirkette zaten Kafka Connect işletiliyorsa CDC'ye geçilir. Tablo ve mesaj biçimi aynı
kalır; yalnızca relay değişir.
