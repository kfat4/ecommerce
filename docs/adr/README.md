# Mimari Karar Kayıtları (ADR)

Bir ADR, bir mimari kararın **neden** alındığını tek sayfada kaydeder. Kod "ne"yi gösterir, ADR "neden"i.
Yeni bir şirkete girdiğinde ilk arayacağın belgeler bunlardır; yoksa ilk yazacağın belgeler de bunlardır.

Yeni ADR: `/write-adr <başlık>`. Bir ADR değiştirilmez; yerine yenisi yazılır ve eskisi "Yerini NNNN aldı"
olarak işaretlenir.

| No | Karar | Durum |
|---|---|---|
| [0001](0001-servis-basina-veritabani.md) | Mikroservis mimarisi ve servis başına veritabanı | Kabul edildi |
| [0002](0002-saga-orkestrasyon.md) | Sipariş süreci için orkestrasyon tabanlı saga | Kabul edildi |
| [0003](0003-transactional-outbox.md) | Transactional outbox (polling), CDC'ye geçiş yolu açık | Kabul edildi |
| [0004](0004-idempotency.md) | İki katmanlı idempotency: HTTP anahtarı ve inbox | Kabul edildi |
| [0005](0005-arama-icin-cqrs.md) | Arama için CQRS ve Elasticsearch okuma modeli | Kabul edildi |
| [0006](0006-jwt-ve-servisler-arasi-guven.md) | JWT ile kimlik; yerelde HS256, üretimde asimetrik | Kabul edildi (geçici) |
| [0007](0007-gozlemlenebilirlik-yigini.md) | Gözlemlenebilirlik: OTLP + Elastic, Prometheus + Grafana | Kabul edildi |
| [0008](0008-monorepo-ve-ortak-kutuphane.md) | Monorepo ve ortak kütüphane | Kabul edildi |
