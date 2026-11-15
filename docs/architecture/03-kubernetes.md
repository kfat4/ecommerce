# Kubernetes'te Çalıştırma (yerel: Docker Desktop)

Aynı imajlar iki ortamda çalışır: docker-compose ve Kubernetes. Ortam farkı kodda değil, yapılandırmadadır.
Karar ve gerekçe: [ADR-0009](../adr/0009-kubernetes-servis-kesfi.md).

## 1. Ön koşullar

- Docker Desktop → Settings → Kubernetes → **Enable Kubernetes** (kubeadm, tek düğüm). Bağlam adı:
  `docker-desktop`.
- `brew install helm`
- Docker'a en az 8 GB bellek. Compose yığını ile Kubernetes **aynı anda sığmaz**. İkisi de `localhost:8080`
  portunu kullandığı için önce `make down` çalıştır.

## 2. Komutlar

```bash
make down          # compose yığınını durdur (veriler kalır)
make k8s-up        # imajları derle + helm upgrade --install (her şey hazır olana kadar bekler)
make k8s-status    # pod ve service listesi
make smoke         # aynı uçtan uca test, bu kez Kubernetes'e karşı
make console       # API konsolu: http://localhost:5500
make k8s-ui        # Kafka UI, pgAdmin, mongo-express, RedisInsight'ı 127.0.0.1'e port-forward eder (Ctrl+C kapatır)
make k8s-down      # chart'ı kaldır (PVC'ler kalır)
kubectl --context docker-desktop delete namespace ecommerce   # verilerle birlikte her şeyi sil
```

## 3. Kümede ne var

```mermaid
flowchart LR
    host([localhost:8080]) -->|"Service type: LoadBalancer"| gw[api-gateway<br/>Deployment]
    gw -->|"Service DNS<br/>http://order-service:8082"| svc[7 iş servisi<br/>Deployment + ClusterIP Service]
    svc --> pg[(postgres-0<br/>StatefulSet + PVC)]
    svc --> mongo[(mongo-0<br/>StatefulSet + PVC)]
    svc --> kafka{{kafka-0<br/>StatefulSet + PVC}}
    svc --> es[(elasticsearch-0<br/>StatefulSet + PVC)]
    svc --> redis[(redis<br/>Deployment)]
    svc --> wm[payment-provider<br/>WireMock]
    cm[/ConfigMap<br/>application-kubernetes.yml/] -.->|/config| svc
    sec[/Secret<br/>JWT, DB parolası/] -.->|env| svc
```

| Kubernetes nesnesi | Neden bu | Compose karşılığı |
|---|---|---|
| **Deployment** (servisler, Redis, WireMock) | Durumsuz; pod'lar birbirinin yerine geçebilir | `services:` + `deploy.replicas` |
| **StatefulSet** (PostgreSQL, Mongo, Kafka, ES) | Sabit ad (`mongo-0`) ve pod başına kalıcı disk | `volumes:` |
| **Service ClusterIP** | Sabit DNS adı + pod'lar arası yük dağıtımı | Docker ağı + Eureka |
| **Headless Service** (`clusterIP: None`) | Ad doğrudan pod IP'sine çözülür; veritabanları kendi adresini tanır | – |
| **Service LoadBalancer** (gateway) | Docker Desktop bunu `localhost:8080` adresinde açar | `ports: ["8080:8080"]` |
| **ConfigMap** | Ortama özgü ayar dosyası, imaja gömülmez | `environment:` |
| **Secret** | Parola ve anahtarlar ayrı nesnede; RBAC ile ayrıca korunabilir | `.env` |
| **initContainer** `wait-for-dependencies` | Kubernetes'te `depends_on` yok; bağımlılık açılana kadar bekler | `depends_on: service_healthy` |

## 4. Sağlık kontrolleri (probe)

Her Spring servisinde üç probe vardır ve her biri farklı bir soruyu cevaplar:

| Probe | Soru | Başarısız olursa | Uç nokta |
|---|---|---|---|
| startup | JVM açılışını bitirdi mi? | Diğer ikisi beklemeye devam eder; süre (180 sn) dolarsa yeniden başlatır | `/actuator/health/liveness` |
| liveness | Süreç takıldı mı? | Konteyneri **yeniden başlatır** | `/actuator/health/liveness` |
| readiness | Şu an trafik alabilir mi? | Pod'u Service'ten **çıkarır**, yeniden başlatmaz | `/actuator/health/readiness` |

Sık yapılan hata: liveness içine veritabanı kontrolü koymak. Veritabanı yavaşlayınca bütün pod'lar aynı anda
yeniden başlar ve sorun büyür. Dış bağımlılık kontrolü readiness'e aittir, liveness'e değil.

## 5. İlk kurulumda çıkan sorunlar (öğretici)

1. **Mongo replica set "tavuk-yumurta" sorunu.** Headless Service DNS'te yalnızca *hazır* pod'ları
   gösterir. Mongo replica set'i `mongo:27017` adıyla başlatmaya çalıştı. Bu ad, Mongo hazır olmadan
   çözülmüyordu; Mongo da replica set olmadan hazır olamıyordu. Çözüm: pod'un kendi kalıcı adını
   (`mongo-0.mongo.ecommerce.svc.cluster.local`) kullanmak. Kubelet bu adı pod'un `/etc/hosts` dosyasına
   yazar.
2. **StatefulSet takılı pod'u değiştirmez.** Şablon düzeltildi, ama hiç hazır olamamış pod eski haliyle
   kaldı. StatefulSet bir pod hazır olmadan sıradakine geçmez; bozuk pod'u elle silmek gerekir
   (`kubectl delete pod mongo-0`). PVC silinmez, veri kalır.
3. **`enableServiceLinks: false`.** Kubernetes varsayılan olarak her Service için `KAFKA_PORT=tcp://...` gibi
   ortam değişkenleri ekler. Kafka imajı `KAFKA_*` ile başlayan her değişkeni broker ayarı sayar, bu da
   sessiz bir hataya yol açar. Bütün pod'larda bu özellik kapalıdır.

## 6. Veri arayüzleri (Kafka UI, pgAdmin, mongo-express, RedisInsight)

`devTools.enabled: true` (varsayılan) ile [templates/dev-tools.yaml](../../deploy/helm/ecommerce/templates/dev-tools.yaml)
dört arayüzü kümede çalıştırır. Bunlar kümenin **canlı verisine** bakar: servis adlarıyla bağlanırlar
(`kafka:9092`, `postgres:5432`, `mongo:27017`, `redis:6379`). İmajlar ve localhost portları compose ile aynıdır.

```bash
make k8s-ui        # açık kaldığı sürece çalışır; Ctrl+C dört port-forward'u birden kapatır
```

| Arayüz | Adres | Not |
|---|---|---|
| Kafka UI | http://localhost:8090 | Küme adı `ecommerce-k8s`; topic'ler, consumer group lag, mesajlar |
| pgAdmin | http://localhost:5050 | Sunucu kayıtlı ve parola sorulmaz (aşağıya bak) |
| mongo-express | http://localhost:8091 | `product_db`, `notification_db` |
| RedisInsight | http://localhost:5540 | İlk açılışta EULA/gizlilik ekranını **sen** onayla; `ecommerce (k8s)` bağlantısı ondan sonra listede görünür |

**Neden LoadBalancer değil de port-forward?** Bu arayüzlerin önünde hiçbir kimlik doğrulama yok: mongo-express
ve pgAdmin (desktop modu) açan herkese veritabanını tam yetkiyle verir. Docker Desktop'ta LoadBalancer
Service'i yalnızca localhost'a değil **tüm ağ arayüzlerine** bağlanır (`lsof -iTCP:8080` → `*:8080`). Yani
aynı Wi-Fi'daki herkes erişebilirdi. Compose'da bu yüzden `127.0.0.1:5050:80` yazıyoruz. Kubernetes'te
karşılığı: Service'ler `ClusterIP`, erişim `kubectl port-forward --address 127.0.0.1` ile. Trade-off: komut
açık kalmalı, pod yeniden başlarsa (ör. `helm upgrade`) port-forward kopar ve `make k8s-ui` tekrar çalışır.
Gerçek bir ortamda bu araçlar ya hiç kurulmaz (`devTools.enabled: false`) ya da SSO'lu bir proxy
(oauth2-proxy) ve NetworkPolicy arkasında durur.

**pgAdmin parolası Secret'tan.** `infra/pgadmin/servers.json` compose ile ortaktır (`files/` altında symlink).
Helm bu dosyaya `PassFile` ekler; bir initContainer `ecommerce-secrets` içindeki kullanıcı ve paroladan
`/pgpass/pgpass` dosyasını yazar (bellek tabanlı `emptyDir`). libpq bu dosyayı yalnızca `0600` izinliyse ve
okuyan kullanıcıya aitse kullanır. Bu yüzden initContainer pgAdmin ile aynı uid'de (5050) çalışır. Not:
pgAdmin `servers.json`'u yalnızca ilk açılışta içe aktarır. Dosya değişirse pod anotasyonundaki checksum
pod'u yeniden oluşturur. pgAdmin verisi kalıcı diskte değil, pod içinde durduğu için yeni pod dosyayı yeniden içe aktarır.

Bellek: dört arayüz birlikte yaklaşık 650 MiB kullanır (Kafka UI ~280, pgAdmin ~230, RedisInsight ~110,
mongo-express ~40). 8 GB'lık Docker VM'de sıkışırsan `--set devTools.enabled=false` ile kapat.

## 7. Henüz yok (sonraki dilimler)

- Gözlemlenebilirlik yığını (collector, Prometheus `kubernetes_sd_configs`, Grafana). Pod'larda
  `prometheus.io/*` anotasyonları hazır.
- HorizontalPodAutoscaler, PodDisruptionBudget, NetworkPolicy.
- Ingress ya da Gateway API, TLS.
- Gizli bilgi yönetimi (External Secrets / Vault), imaj kayıt deposu (GHCR), GitOps (Argo CD).
