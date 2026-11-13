# ADR-0006: JWT ile kimlik; yerelde HS256, üretimde asimetrik anahtar

- **Durum:** Kabul edildi (geçici; üretim öncesi değişmeli)
- **Tarih:** 2026-11-08
- **İlgili kod:** `services/auth-service/.../TokenService.java`, `libs/common-lib/.../security/`, gateway `SecurityConfig`

## Bağlam

Servisler durumsuz olmalı ki yatay ölçeklensin; oturum sunucuda tutulamaz. Her istekte "bu kim, ne
yapabilir" sorusu, merkezi bir servise sormadan cevaplanabilmelidir.

## Seçenekler

1. **Opak token + introspection** – her istekte kimlik servisine sorulur. İptal anında etkili; ama kimlik
   servisi her isteğin yolunda tek hata noktası olur.
2. **JWT, simetrik imza (HS256)** – doğrulama yerelde yapılır. Ama doğrulayabilen herkes token **üretebilir**:
   sır tüm servislerdedir, biri ele geçirilirse hepsi geçilmiş olur.
3. **JWT, asimetrik imza (RS256/ES256) + JWKS** – özel anahtar yalnızca kimlik servisindedir; diğerleri açık
   anahtarla doğrular. Anahtar döndürme JWKS ile yapılır.
4. **Hazır kimlik sağlayıcı** (Keycloak, bulut IdP) – OAuth2/OIDC akışları hazır gelir.

## Karar

Şu an seçenek 2: HS256, 30 dakikalık erişim token'ı, ortak sır ortam değişkeninden (`JWT_SECRET`).
Gateway imzayı doğrular; **her servis de** kendi başına doğrular. Servisler ağa güvenmez: gateway'i atlayan
bir istek de geçerli token taşımak zorundadır. `/internal/**` yolları gateway'den yönlendirilmez.

Bu, yerel geliştirme için bilinçli bir sadeleştirmedir. Üretim için hedef seçenek 3 ya da 4'tür.

## Sonuçlar

- Olumlu: basit, hızlı, kimlik servisi çökse bile mevcut token'lar çalışır.
- Olumsuz / bilinen açıklar:
  - Ortak sır: bir servisin ele geçirilmesi tüm sistemde token üretmeye yeter.
  - Refresh token ve iptal yok; çalınan token süresi dolana kadar geçerlidir.
  - Servisler arası çağrılarda (`/internal/**`) ayrı bir servis kimliği yok; yalnızca ağ yalıtımına dayanıyor.
- Operasyonel: sır döndürme tüm servislerin birlikte yeniden başlatılmasını gerektirir.

## Ne zaman yeniden düşünülür

Üretime çıkmadan önce kesinlikle. Yol haritası faz 6: RS256 + JWKS, refresh token, servisler arası
client-credentials ya da mTLS.
