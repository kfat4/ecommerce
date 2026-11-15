# ADR-0009: Kubernetes'te servis keşfi ve dağıtım paketi

- **Durum:** Kabul edildi
- **Tarih:** 2026-11-15
- **İlgili kod:** `deploy/helm/ecommerce/`, özellikle `templates/app-config.yaml` ve `templates/services.yaml`

## Bağlam

Servisler birbirini mantıksal adla çağırır: gateway `lb://order-service`, order-service ise
`http://product-service`. docker-compose'da bu adlar Eureka kaydından çözülür. Her replika kendini kaydeder,
istemci tarafındaki Spring Cloud LoadBalancer da replikalar arasında dağıtır.

Kubernetes aynı işi kendisi yapar. Her Deployment'ın önünde bir Service vardır; Service adı DNS'te çözülür ve
kube-proxy trafiği hazır pod'lara dağıtır. Kubernetes'te Eureka çalıştırmak aynı sorunu iki kez çözmek olur.
Pod IP'leri kısa ömürlü olduğu için Eureka'da bayat kayıt riski de doğar.

## Seçenekler

1. **Profil ile ikisi birden.** Compose Eureka ile kalır. Kubernetes'te `kubernetes` profili Eureka'yı
   kapatır. Mantıksal adlar Spring Cloud'un "simple discovery" listesiyle Service DNS adlarına bağlanır.
2. **Eureka'yı her yerden kaldırmak.** Compose'da Docker DNS kullanılır. Daha sade olur, ama compose'daki
   `--scale` ile istemci tarafı dağıtım ve Eureka'nın öğrettiği konu kaybolur.
3. **Eureka Kubernetes'te de çalışsın.** Kod ve ayar değişmez, ama gereksiz bir katman eklenir. Gerçek
   şirketlerde nadiren görülür.
4. **Spring Cloud Kubernetes.** Servisler Kubernetes API'sinden pod listesini okur. Bunun için ek bağımlılık
   ve RBAC izni gerekir; Service DNS'in zaten verdiği şey için fazla.

## Karar

**Seçenek 1.** Java kodu değişmez. Kubernetes'te her pod'a bir ConfigMap'ten `application-kubernetes.yml`
bağlanır (`SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/config/`, `SPRING_PROFILES_ACTIVE=kubernetes`):

```yaml
eureka.client.enabled: false
spring.cloud.discovery.client.simple.instances:
  product-service: [{ uri: http://product-service:8081 }]
  # ... her servis için bir satır, values.yaml'deki servis listesinden üretilir
```

`lb://product-service` artık tek bir adres görür: Service'in adresi. Dağıtımı Spring değil kube-proxy yapar.

Dağıtım paketi **Helm**'dir. Spring servislerinin hepsi tek bir şablondan (`services.yaml`) üretilir; servisler
arasındaki fark yalnızca `values.yaml`'dedir. Altyapı (PostgreSQL, MongoDB, Kafka, Elasticsearch) aynı chart'ta
StatefulSet olarak çalışır; Redis ve WireMock birer Deployment'tır.

## Sonuçlar

- Olumlu: aynı imaj iki ortamda da değişmeden çalışır. Ortam farkı kodda değil yapılandırmadadır
  (on iki faktör, III. madde).
- Olumlu: `helm upgrade --install` tek komutla bütün sistemi kurar. Yeni bir ortam yeni bir values
  dosyasıdır.
- Olumsuz: Kubernetes'te yük dağıtımı L4'tür (bağlantı başına). Uzun ömürlü HTTP bağlantıları (keep-alive)
  aynı pod'a yapışabilir. Eureka ile istemci tarafında istek başına dağıtım vardı. Gerekirse service mesh ya da
  headless Service ve istemci tarafı dağıtım ile çözülür.
- Olumsuz: Prometheus compose'da hedefleri Eureka'dan buluyordu (`eureka_sd_configs`). Kubernetes'te
  `kubernetes_sd_configs` gerekir. Pod'lar `prometheus.io/*` anotasyonlarını taşıyor, ama gözlemlenebilirlik
  yığını henüz chart'ta yok.
- Olumsuz: veritabanlarının kümede StatefulSet olarak çalışması yalnızca yerel ortam içindir. Üretimde
  yönetilen servis (RDS, MSK, Atlas, Elastic Cloud) ya da operatör kullanılır. Yedekleme, yükseltme ve
  yüksek erişilebilirlik bu chart'ın işi değildir.
- Gizli bilgiler: chart bir Secret üretir, ama değerler `values.yaml`'deki geliştirme değerleridir. Gerçek
  ortamda External Secrets ya da Vault gelir (yol haritası Faz 5).

## Ne zaman yeniden düşünülür

- İstek başına yük dağıtımı, mTLS ya da trafik bölme (canary) gerektiğinde: service mesh ya da Gateway API.
- Compose ortamı terk edildiğinde: Eureka ve `discovery-server` tamamen kaldırılır (Seçenek 2).
