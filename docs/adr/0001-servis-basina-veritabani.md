# ADR-0001: Mikroservis mimarisi ve servis başına veritabanı

- **Durum:** Kabul edildi
- **Tarih:** 2026-11-08
- **İlgili kod:** `infra/postgres/init-databases.sql`, her servisin `application.yml` dosyası

## Bağlam

Proje iki amaca hizmet ediyor: gerçekçi bir e-ticaret backend'i olmak ve dağıtık sistem desenlerini
öğretmek. E-ticarette alanların yük profili çok farklıdır: katalog ve arama okuma ağırlıklıdır, sipariş ve
ödeme tutarlılık ister, stok kilit ister. Tek bir veritabanı ve tek bir uygulama, bu alanları aynı ölçekleme
ve yayın döngüsüne bağlar.

## Seçenekler

1. **Modüler monolit** – tek uygulama, modüller arası net sınırlar, tek veritabanı. En ucuz işletim, en
   kolay tutarlılık. Küçük ekipler için çoğu zaman doğru başlangıç.
2. **Mikroservisler, ortak veritabanı** – bağımsız yayın gibi görünür ama şema değişikliği herkesi kırar.
   En kötü seçenek: dağıtık sistemin bedelini öder, faydasını almaz ("dağıtık monolit").
3. **Mikroservisler, servis başına veritabanı** – gerçek bağımsızlık; karşılığında dağıtık transaction
   yok, veri tutarlılığı elle (saga, outbox) sağlanır.

## Karar

Her servis kendi veritabanına sahiptir ve başka servisin verisine yalnızca API ya da olay ile ulaşır
(seçenek 3). Veritabanı türü alana göre seçilir: PostgreSQL (sipariş, stok, ödeme, kimlik), MongoDB
(katalog, bildirim), Elasticsearch (arama), Redis (önbellek, hız sınırı, idempotency).

Gerekçe: öğrenmek istediğimiz desenlerin tamamı (saga, outbox, idempotency, CQRS) yalnızca bu seçenekte
gereklidir. Monolitte bunlar yapay kalırdı.

## Sonuçlar

- Olumlu: servisler bağımsız ölçeklenir ve yayınlanır; bir alanın veri modeli diğerini etkilemez.
- Olumsuz: servisler arası JOIN yok; raporlama için ayrı bir okuma modeli gerekir. Tutarlılık anlık değil,
  nihaidir. İşletim yükü (izleme, yayın, hata ayıklama) belirgin biçimde artar.
- Operasyonel: dağıtık izleme zorunlu hale gelir (ADR-0007).

## Ne zaman yeniden düşünülür

Gerçek bir şirkette ekip 10 kişinin altındaysa ve tek bir yayın döngüsü yetiyorsa modüler monolit daha
doğrudur. İki servis sürekli birlikte değişiyor ve birbirini senkron çağırıyorsa sınır yanlış çizilmiştir;
birleştirilmelidir.
