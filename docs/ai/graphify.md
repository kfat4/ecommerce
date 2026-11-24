# graphify: yerel kod grafiği (isteğe bağlı)

[graphify](https://github.com/Graphify-Labs/graphify) kaynak kodu tree-sitter ile ayrıştırıp sınıf, metot,
import ve çağrılardan bir grafik çıkarır. Sonra "bunu kim kullanıyor", "A'dan B'ye nasıl gidilir" diye
sorulabilir. Bu depoda **yalnızca kod çıkarımı** kullanılır: LLM yok, API anahtarı yok, bulut hesabı yok.
Derleme, testler ve CI bu araca bağlı değildir; kurmayan hiçbir şey kaybetmez.

## Kurulum

PyPI paketinin adı `graphifyy` (çift y). Diğer `graphify*` paketleri bu projeyle ilişkili değildir.
Sabit sürüm: **0.9.112** (17.11.2026, Apache-2.0). Seçim kuralı: en az 7 günlük en yeni sürüm.

```bash
uv tool install --python 3.13 --exclude-newer 2026-11-18T00:00:00Z graphifyy==0.9.112
```

`--exclude-newer` bağımlılıkları da o tarihten eski sürümlerle sınırlar. Sürümü yükseltirken bu dosyadaki ve
`Makefile` içindeki sürüm ile tarihi birlikte değiştir.

**`graphify install` (ve `install --project`) çalıştırma.** Nedeni aşağıda.

## Kullanım

```bash
make graph
```

Çıktı `graphify-out/graph.json`. Grafik kendiliğinden güncellenmez: kod değişince `make graph` komutunu
yeniden çalıştır (yalnızca değişen dosyalar işlenir). SQL ve Helm şablonları işlenemediği için her çalıştırmada
"AST extraction incomplete" uyarısı çıkar; hata değildir.

```bash
graphify affected ReserveInventoryCommand
```

```bash
graphify explain RoutesConfig
```

```bash
graphify path "<kaynak id>" "<hedef id>"
```

```bash
graphify query "how does the order saga reserve stock" --budget 1200
```

`Ambiguous` yanıtı gelirse listelenen tam `id` ile tekrar dene. Java'da sınıf ile kurucu metodu aynı adı taşır
ve graphify ikisini ayrı düğüm yapar; bu yüzden açık kurucusu olan sınıflarda kısa ad çoğu zaman yetmez.

## Bu depoda ne işe yarıyor, ne yaramıyor (25.11.2026 denemesi)

2093 düğüm, 4400 kenar; kenarların 274'ü `INFERRED` (tahmin). Sonuçlar kodla karşılaştırıldı:

| Deneme | Sonuç | Kodla karşılaştırma |
|---|---|---|
| `affected ReserveInventoryCommand` | Orkestratör, inventory tüketicisi ve test bulundu | Doğru ve yararlı: sözleşme değişince kim etkilenir. Tüketici yalnızca dosya (import) düzeyinde; `switch` içindeki tip eşlemesi görülmüyor |
| `path` OrderSagaOrchestrator → OutboxPublisher | 1 adım | Doğru |
| `explain` OutboxPublisher | 17 bağlantı; kullanan üç servis | Doğru |
| `path` OrderSagaOrchestrator → InventoryMessageHandler | Yönlü yol yok | **Eksik.** Gerçekte `inventory.commands` topic'i ile bağlılar |
| Aynısı, `--undirected` | İkisi de OutboxPublisher kullanıyor | **Yanıltıcı.** Ortak sınıf, mesaj akışı değil |
| `path` OutboxPublisher → OutboxRelay | Yönlü yol yok; `--undirected` JdbcTemplate üzerinden | **Yanıltıcı.** Gerçek bağ `outbox_events` tablosu |
| `explain` RoutesConfig | Yalnızca kendi metotları ve `@Configuration` | **Eksik.** `lb://order-service` gibi hedefler yok |
| `explain` reserve_stock | Düğüm yok | **Eksik.** SQL ayrıştırılmıyor |
| `query` (stok ayırma sorusu) | Doğru başlangıç düğümleri, sonra getter'lar; 381 düğümün 27'si | Gürültülü; akışı anlatmıyor |

Sonuç: **servisler arası doğrudan kenar sayısı 0.** Servisler yalnızca ortak kütüphaneler üzerinden bağlı görünür.

- Kafka: `@KafkaListener` ile yayıncı eşleşmez. `Topics` yalnızca import olarak görünür; hangi topic'in
  kullanıldığı görünmez.
- HTTP: gateway rotaları ve servisler arası HTTP istemcileri hedef servise bağlanmaz.
- Veritabanı: 9 `.sql` dosyası (PL/pgSQL dahil) grafikte yok. `graphifyy[sql]` eki bilerek kurulmadı.
- Spring: bağımlılık alan tipinden, birçok çağrı tahminle (`INFERRED`) görülür; proxy davranışı görülmez.
- Markdown belgeleri (ADR'ler dahil) `--code-only` nedeniyle grafikte yok.

Saga, outbox ve olay akışları için `docs/architecture/` ve kod esastır. Grafikte yol bulunmaması "bağlantı yok"
demek değildir. Bulunan yolu da adım adım kodda doğrula.

## Neler yerelde kalıyor, neler grafiğe girmiyor

- `graphify-out/` depoya commit'lenir (üretilen çıktı, GitHub diff'lerinde kapalı gelir). Dışarıda kalanlar: ev
  dizinini içeren `.graphify_root` ve elle yönlendirilmiş `*.log` dosyaları.
- Commit'lenen grafik **kendiliğinden güncellenmez, bayatlar**: kodun commit'teki halini gösterir. `make graph` ile
  tazelenir; yeni çıktı commit'lenmeden önce ev dizini, e-posta ve anahtar için taranmalıdır (depo herkese açık).
- `.graphifyignore`: `.env*`, anahtarlar, öğrenme dosyaları, `.claude/worktrees/`, `target/` ve IDE dosyaları.
- Grafik, ağ erişimi kapalıyken de (macOS `sandbox-exec`) üretildi. İncelenen graphify kodunda telemetri ya da
  güncelleme kontrolü bulunmadı (bağımlılıkların kodu incelenmedi); sorgu günlüğü varsayılan olarak kapalı.
- Ağ kullanan komutlar var, burada kullanılmaz: `add <url>`, `clone`, `--backend` ile LLM, `prs`, neo4j/falkordb
  gönderimi. HTML dışa aktarma çıktısı tarayıcıda açılınca CDN'den betik indirir.
- Depo dışına yazılanlar: `~/.local/share/uv/tools/graphifyy/`, `~/.local/bin/graphify` ve `graphify-mcp`,
  uv önbelleği. `graphify-mcp` kullanılmıyor (gereken `mcp` eki kurulu değil).

Kaldırmak için:

```bash
uv tool uninstall graphifyy
```

## Neden `install --project` kullanılmadı

Komut, sahte bir HOME ile geçici bir kopyada denendi. Yazdıkları ve alınmama nedenleri:

| Yazdığı | Neden alınmadı |
|---|---|
| `.claude/skills/graphify/` | Araç yoksa sürümü sabitlemeden `uv tool install --upgrade` ya da `pip install --break-system-packages` çalıştırıyor; belgeleri LLM ajanlarına okutuyor; bulut hizmetini öneriyor |
| `CLAUDE.md` bölümü ve yeni `.claude/CLAUDE.md` | "Her aramadan önce grafiğe sor" diyor (eksik kenarlar varken yanlış yönlendirir) ve `graph.json` dosyasının commit'lenebileceğini varsayıyor |
| `settings.json` içinde üç hook | Çıplak `graphify` komutu; kurulu olmayan klonda 127 koduyla biter. Ayrıca ajanı eksik grafiğe yönlendirir |
| `settings.json.graphify-bak`, `~/.cache/graphify/skill-locks/` | Gereksiz dosyalar |

Deneme kopyasında mevcut izinler, guard hook'u ve eklentiler korundu; yine de gerçek `settings.json`
değiştirilmedi. Git hook'u ve strict modu da kurulmadı.
