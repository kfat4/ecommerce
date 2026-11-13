# AI Ajanlarla Geliştirme Rehberi

Bu depo aynı zamanda agentic geliştirme pratiği için kurulmuştur. Burada Claude Code'un beş yapı taşını
gerçek bir projede kullanırsın: talimatlar, kurallar, ajanlar, skill'ler ve hook'lar. Altıncısı hafızadır;
[hafiza-sistemi.md](hafiza-sistemi.md) dosyasında ayrıca anlatılır.

## 1. Yapı taşları ve bu depodaki karşılıkları

| Yapı taşı | Ne işe yarar | Bu depoda | Ne zaman yüklenir |
|---|---|---|---|
| `CLAUDE.md` | Projenin anayasası: komutlar, mimari kurallar, çalışma biçimi | [CLAUDE.md](../../CLAUDE.md) | Her oturumda |
| Yol kuralları | Yalnızca ilgili dosyalarla çalışırken geçerli kurallar | `.claude/rules/*.md` | Eşleşen dosya okununca |
| Ajanlar (subagent) | Kendi bağlamı ve araçları olan uzmanlar | `.claude/agents/*.md` | Görev devredilince |
| Skill'ler | Paketlenmiş iş akışları, `/ad` ile çağrılır | `.claude/skills/*/SKILL.md` | Çağrılınca |
| Hook'lar | Modelin kararından bağımsız çalışan zorunlu denetimler | `.claude/hooks/`, `settings.json` | Her araç çağrısında |
| Hafıza | Oturumlar arası bilgi | `.claude/memory/`, `.claude/agent-memory/` | Her oturumda (dizin) |

En önemli ayrım: **`CLAUDE.md` tavsiyedir, hook zorunluluktur.** "`.env` dosyasına dokunma" cümlesi çoğu
zaman işe yarar; `guard-protected-files.py` her zaman işe yarar. Geri alınamaz bir hatayı önlemek
istiyorsan hook yaz.

## 2. Ajan ekibi

| Ajan | Rolü | Yazabilir mi | Model |
|---|---|---|---|
| `solution-architect` | Tasarım seçenekleri, karar, ADR | Yalnızca `docs/` | opus |
| `java-backend-developer` | Uygulama | Evet | oturumun modeli |
| `test-engineer` | Testler, hata yeniden üretme | Yalnızca test kodu | sonnet |
| `code-reviewer` | Diff incelemesi | Hayır | opus |
| `security-reviewer` | Güvenlik incelemesi | Hayır | opus |
| `platform-engineer` | Docker, CI/CD, Kubernetes | Evet (altyapı dosyaları) | sonnet |
| `sre-observability` | Metrik, alarm, dashboard, olay analizi | Evet (gözlem dosyaları) | sonnet |
| `tech-mentor` | Sana konu anlatır | Yalnızca `docs/learning/` | oturumun modeli |

Tasarım ilkeleri (kendi ajanlarını yazarken de geçerli):

- **Tek sorumluluk.** Ajanın `description` alanı, ana oturumun onu ne zaman çağıracağını belirler. Belirsiz
  açıklama = hiç çağrılmayan ajan.
- **En az yetki.** İnceleyen ajanlar yazamaz. Yazamayan bir inceleyici, bulduğu hatayı sessizce "düzeltip"
  gizleyemez.
- **Ajan konuşmayı görmez.** Her ajan boş bir bağlamla başlar. Ona verdiğin görev tanımı kendi başına yeterli
  olmalıdır: amaç, dosyalar, kısıtlar, "bitti" ne demek.
- **Ajanın raporu kanıt değildir.** `java-backend-developer` "testler geçti" derse `./mvnw test` komutunu
  yine de kendin (ya da ana oturum) çalıştırır.

## 3. Skill'ler

| Komut | Ne yapar | Kim çağırır |
|---|---|---|
| `/feature-team <özellik>` | Tasarım → uygulama → test → inceleme hattı | Yalnızca sen |
| `/new-microservice <ad> <amaç>` | Yeni servis iskeleti ve kontrol listesi | Yalnızca sen |
| `/add-integration-event <olay>` | Kafka mesajını uçtan uca ekleme adımları | Sen ya da Claude |
| `/write-adr <başlık>` | Şablondan ADR yazar, dizine ekler | Sen ya da Claude |
| `/remember <bilgi>` | Ortak hafızaya kalıcı not | Sen ya da Claude |
| `/handoff` | Oturum devir notu | Yalnızca sen |
| `/teach <konu>` | Konuyu koddan anlatır, alıştırma verir | Sen ya da Claude |
| `/map-architecture [yol]` | Tanımadığın bir kod tabanının haritasını çıkarır | Sen ya da Claude |

"Yalnızca sen" olanlar yan etkili ya da pahalıdır (`disable-model-invocation: true`); Claude kendi kararıyla
başlatamaz.

## 4. Eklentiler (plugin)

`.claude/settings.json` şu eklentileri etkin olarak bildirir:

| Eklenti | Kaynak | Ne katar |
|---|---|---|
| `claude-code-setup` | Resmi pazar yeri | Projeye uygun otomasyon önerileri (kurulu) |
| `superpowers` | Resmi pazar yeri (obra/superpowers) | Beyin fırtınası, plan, TDD, sistematik hata ayıklama skill'leri |
| `security-guidance` | Resmi pazar yeri | Düzenlemelerde güvenlik uyarıları, diff güvenlik incelemesi |
| `andrej-karpathy-skills` | `multica-ai/andrej-karpathy-skills` | Dört davranış ilkesi (aşağıda) |

**Kurulum senin elinde.** Ayar dosyası eklentiyi yalnızca bildirir; dış kaynaklı bir eklentinin her kişi
tarafından bir kez kurulması gerekir. Terminaldeki Claude Code'da:

```text
/plugin install superpowers@claude-plugins-official
```

```text
/plugin install security-guidance@claude-plugins-official
```

```text
/plugin marketplace add multica-ai/andrej-karpathy-skills
```

```text
/plugin install andrej-karpathy-skills@karpathy-skills
```

Notlar:

- "Karpathy skills" Andrej Karpathy'nin kendi yazdığı bir paket **değildir**; onun LLM ile kodlama üzerine
  gözlemlerinden türetilmiş, topluluk tarafından hazırlanmış tek bir skill'dir (MIT lisanslı, yalnızca metin).
  Dört ilkesi (kodlamadan önce düşün, sadelik, cerrahi değişiklik, hedef odaklı çalışma) bu projenin
  `CLAUDE.md` dosyasına kendi cümlelerimizle zaten yazılmıştır; eklenti kurulmasa da ilkeler geçerlidir.
- `superpowers` kendi iş akışını getirir (önce beyin fırtınası, sonra plan, sonra uygulama). Bu deponun
  `/feature-team` hattıyla aynı fikirdedir; ikisini aynı işte üst üste çalıştırma, birini seç.
- Üçüncü taraf eklenti, senin adına komut çalıştırabilen koddur. Kurmadan önce ne içerdiğine bak.

## 5. Günlük çalışma biçimleri

### Küçük değişiklik

Doğrudan iste. Ajan ekibine gerek yok. Bitince "ne doğruladın?" diye sor.

### Yeni özellik

```
/feature-team Sepete ürün ekleme: Redis'te TTL'li sepet, misafir sepeti girişte birleşsin
```

Hat, tasarım aşamasında durur ve sana sorar. Tasarımı okumadan onaylama; öğrenmenin en yoğun olduğu yer orası.

### Hata ayıklama

Belirtiyi ver (log satırı, başarısız test, alarm adı). "Önce hatayı yeniden üreten bir test yaz" de. Kök neden
bulunmadan düzeltme kabul etme.

### Öğrenme

```
/teach outbox
```

### Uzun işi bölmek

Bağlam dolmaya başladığında `/handoff` çalıştır, yeni oturum aç. Yeni oturum devir notunu ve ortak hafızayı
okuyarak kaldığı yerden devam eder.

## 6. Agentic geliştiricinin dört alışkanlığı

1. **Bağlamı sen yönetirsin.** Modelin kalitesi, ona verdiğin bağlamın kalitesidir. Aynı şeyi ikinci kez
   açıklıyorsan o bilgi `CLAUDE.md`, bir kural ya da hafızaya aittir.
2. **Doğrulanabilir hedef ver.** "Sepeti ekle" yerine "şu test geçsin, `./mvnw test` yeşil kalsın". Ajan,
   kontrol edebileceği bir hedefe doğru döngü kurabilir.
3. **Küçük adım, sık doğrulama.** Büyük bir değişikliği tek seferde isteme. Dal aç, dilimle, her dilimi gözden geçir.
4. **Kararı sen verirsin.** Ajan seçenek üretir ve uygular; servis sınırı, veri sahipliği ve geri alınamaz
   işlemler (push, silme, yayın) senin onayındadır. Kodu okumadan birleştirme.

## 7. Kendi otomasyonunu yazmak (alıştırmalar)

1. **Kural:** `.claude/rules/` altına API tasarım kuralı yaz (`paths: services/**/api/**`): sayfalama, hata
   biçimi, sürümleme.
2. **Skill:** `/runbook <alarm adı>` skill'i yaz; alarm kuralını okuyup runbook taslağı çıkarsın.
3. **Hook:** `git commit` komutundan önce `./mvnw -q test` çalıştıran bir `PreToolUse` hook'u ekle.
4. **Ajan:** `api-designer` ajanı yaz; OpenAPI sözleşmesini gözden geçirsin. Yazma yetkisi verme.

Her birinde aynı soruyu sor: bu bilgi her oturumda mı gerekli (CLAUDE.md), yalnızca bazı dosyalarda mı
(kural), bir iş akışı mı (skill), bir uzmanlık mı (ajan), yoksa asla atlanmaması gereken bir şey mi (hook)?

## 8. Doğrulanmamış olanlar

Bu kurulumdaki ajanlar, skill'ler ve ayar dosyası Claude Code belgelerindeki biçime göre yazıldı ve hook
elle test edildi. Ajan ekibi henüz gerçek bir özellikte uçtan uca çalıştırılmadı; ilk kullanımda açıklama
metinlerinin ince ayar gerektirmesi beklenir.
