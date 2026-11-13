# ADR-0005: Arama için CQRS ve Elasticsearch okuma modeli

- **Durum:** Kabul edildi
- **Tarih:** 2026-11-10
- **İlgili kod:** `services/search-service/`, `services/product-service/.../outbox/`, `Topics.PRODUCT_EVENTS`

## Bağlam

Katalog MongoDB'dedir. Kullanıcı ise yazım hatasına dayanıklı tam metin arama, kategori ve fiyat süzgeci,
yazarken öneri ister. MongoDB bunları iyi yapmaz; Elasticsearch yapar ama birincil kayıt deposu olarak
uygun değildir (transaction yok, yazma gecikmeli görünür).

## Seçenekler

1. **Aramayı MongoDB'de yapmak** – tek depo, tutarlılık sorunu yok. Arama kalitesi ve performansı zayıf.
2. **Çift yazma** – product-service hem MongoDB'ye hem Elasticsearch'e yazar. Dual write problemi (ADR-0003):
   biri başarılı, diğeri başarısız olursa iki depo sessizce ayrışır.
3. **Olayla beslenen ayrı okuma modeli (CQRS)** – product-service yalnızca kendi deposuna yazar ve olay
   yayınlar; search-service olayı dinleyip indeksi günceller.

## Karar

Seçenek 3. Yazma modeli MongoDB (product-service), okuma modeli Elasticsearch (search-service), aralarında
`product.events` topic'i.

- Olaylar **durumun tamamını** taşır (event-carried state transfer); search-service geri arama yapmaz.
- Topic **compacted**'dır ve anahtar ürün kimliğidir: her ürünün son durumu saklanır, yeni bir tüketici
  baştan okuyarak indeksi kurabilir.
- Sıra ve tekrar koruması Elasticsearch **external versioning** ile sağlanır: olay ürünün sürüm numarasını
  taşır, daha eski sürüm 409 ile reddedilir. Ayrı bir inbox tablosu gerekmez.
- Silme fiziksel değildir: **tombstone** belge yazılır. Fiziksel silmede Elasticsearch sürüm bilgisini 60 sn
  sonra unutur (`index.gc_deletes`) ve geç gelen eski bir olay ürünü diriltir.
- İndeks `products` takma adının (alias) arkasındadır (`products-v1`); eşleme değişikliği yeni indeks +
  alias değişimi ile kesintisiz yapılır. Eşleme `dynamic: strict`'tir.
- Yeniden kurma: `POST /api/v1/search/admin/reindex`, product-service'in iç dışa aktarım uç noktasını
  sayfa sayfa okur. Sürüm kontrolü sayesinde canlı olaylarla eşzamanlı çalışması güvenlidir.

## Sonuçlar

- Olumlu: arama yükü kataloğu etkilemez; iki taraf bağımsız ölçeklenir; indeks her zaman yeniden üretilebilir.
- Olumsuz: **nihai tutarlılık**. Ürün güncellendikten sonra aramada görünmesi saniyeler alabilir. Arayüz
  ve iş kuralları (ör. fiyat) buna göre tasarlanmalı; sipariş anında fiyat aramadan değil kaynaktan okunur.
- Operasyonel: tüketici gecikmesi (`KafkaConsumerLagHigh`) arama tazeliğinin ölçüsüdür;
  `ecommerce_search_projection_total{result="stale"}` tekrar ve sıra bozulmasını gösterir.

## Ne zaman yeniden düşünülür

Aramanın stok ya da kişiye özel fiyat gibi sık değişen veriye ihtiyacı olursa indeksi şişirmek yerine
sorgu anında zenginleştirme (BFF) değerlendirilir.
