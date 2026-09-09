# ddp-prod-cluster — İstanbul → İstanbul + Ankara Migration Runbook

Tek lokasyonlu (İstanbul) 5 node'luk Elasticsearch cluster'ını, iki lokasyona
yayılmış (stretched) 7 node'luk zone-aware bir cluster'a taşır.

| | Mevcut | Hedef |
|---|---|---|
| Master (İstanbul) | 3 — `.26` `.27` `.28` | **2** — `.26` `.27` |
| Master (Ankara) | 0 | **1** — `ank-master-1` |
| Data (İstanbul) | 2 — `.29` `.30` | 2 — değişmiyor |
| Data (Ankara) | 0 | **2** — `ank-data-1`, `ank-data-2` |
| **Toplam** | **5** | **7** |

`ddp-prod-ist-master-3` (`10.95.27.28`) decommission edilir.
Master sayısı 3 → quorum 2.

---

## Sıfırdan çalıştırma

IP'ler ve kimlik bilgileri `hosts.env` içinde tanımlı — ek hazırlık gerekmiyor.

```bash
cd migration

./00-preflight.sh                 # salt okunur, FAIL varsa devam etmeyin
./02-backup.sh                    # SOĞUK YEDEK — bu geçmeden ilerlemeyin
./03-join-ankara.sh               # Ankara node'ları katılır
./04-enable-awareness.sh          # veri Ankara'ya taşınır  (uzun sürer)
./06-decommission-ist-master-3.sh # .28 çıkarılır
./07-post-checks.sh               # doğrulama

# İzleme (ayrı terminal, salt okunur)
./05-watch-relocation.sh
```

`01-rolling-restart.sh` **opsiyonel** ve sıraya dahil değil — bkz. aşağıdaki
"Rolling restart neden opsiyonel".

Her script kendi ön koşullarını kontrol eder ve sağlanmıyorsa çalışmaz.
Script'ler SSH yapmaz — node durdurma/başlatma adımlarını operatör yapar,
script bekler ve doğrular.

**Sorun anında:** [ROLLBACK.md](ROLLBACK.md)

---

## Yedekleme: neden soğuk kopya

ES snapshot'ı `path.repo` ile tanımlı **paylaşımlı bir disk** ister; ortamda NFS
kurulamıyor. Onun yerine node veri klasörlerinin soğuk kopyasını alıyoruz.

**Neden burada çalışıyor:** 5 index × 1 shard × `rep=1` = 10 shard kopyası,
2 data node. ES bir shard'ın primary'si ile replica'sını asla aynı node'a koymaz
→ `.29` ve `.30`'un **her biri tüm index'lerin birer kopyasını taşır.**
Tek node'un klasörü = tam veri seti.

`02-backup.sh` iki tar alıyor:
- **`.30`** → shard verisi
- **`.26`** → cluster state (mapping, template, ILM, alias — bunlar master'da durur)

İkisi de olmadan tam geri dönüş yok. Script ayrıca tam-kopya varsayımını
**her çalıştığında shard shard doğruluyor**, varsayıma güvenmiyor.

> Migration sonrası bu yöntem geçersiz olur — 4 data node'a dağılmış kopyalarda
> hiçbir tek node tüm veriyi taşımaz. NFS/S3 bulunup gerçek snapshot'a geçilmeli.

### Rolling restart neden opsiyonel

`path.repo` gerekmeyince `01-rolling-restart.sh` migration için zorunlu olmaktan
çıktı. Ankara node'ları İstanbul master'larını seed olarak görüp katılıyor,
awareness çalışıyor, `.28` çıkarılabiliyor — mevcut IST config'lerine hiç
dokunmadan.

Yine de yaml'lar güncellendi ve hazır duruyor; **bir sonraki bakım penceresinde**
uygulanmalı:
- `cluster.initial_master_nodes` kapatılması (bir node `path.data`'sını
  kaybederse kendi ayrı cluster'ını bootstrap eder — sessiz bir tuzak)
- `seed_hosts`'tan `.28`'in çıkarılması
- `path.repo` (NFS/S3 bulunduğunda gerçek snapshot için)

### 2. Awareness'ı önce açamazsınız
`force.rack.values: ddp-istanbul,ddp-ankara` ayarını Ankara node'ları yokken
açarsanız ES tüm replica'ları "ankara zone'u yok" diye unassigned bırakır ve
cluster anında **yellow** olur. **Önce join, sonra awareness.**

Ek olarak, join ile awareness arasında ES boş node görüp kendi kafasına göre
rebalance eder; awareness açılınca aynı shard'lar bir daha taşınır — veri WAN
üzerinden iki kez gider. Bunu önlemek için join öncesi
`cluster.routing.rebalance.enable: none` veriliyor, awareness bittikten sonra
geri açılıyor. Forced awareness kaynaklı zorunlu taşımalar bu ayardan
etkilenmez (ayrı bir decider yolundan gider), yani Faz 2 planlandığı gibi çalışır.

### 3. `.28`'i en sona bırakın
Master sayısı şu sırayla ilerlemeli:

```
3 IST (quorum 2)  →  ank-master-1 join  →  4 (quorum 3)  →  .28 çıkar  →  3 (quorum 2)
    tolerans 1              tolerans 1 (IST'te 3 var)            tolerans 1
```

`.28` önce çıkarılsaydı migration'ın en riskli bölümü sıfır tolerans ile
geçilirdi. Ayrıca master-eligible bir node'u haber vermeden kapatmak yetmez:
voting configuration onu hâlâ üye sayar ve quorum hesabı bozulur. Önce
`voting_config_exclusions` ile oylama listesinden düşürülür.

---

## Faz 0 — Hazırlık kontrol listesi

`00-preflight.sh` bunların çoğunu otomatik denetler, ama şunlar elle yapılır:

- [ ] **Ankara'ya İstanbul ile TAM AYNI ES sürümü** kurulu
      (`00-preflight.sh` sürümü ekrana basar). Migration ile upgrade'i aynı anda yapmayın.
- [ ] **`elastic-certificates.p12` İstanbul'dan kopyalandı.** Aynı CA olmazsa node
      cluster'a katılamaz ve hata mesajı belirsizdir (`handshake failed`).
      `verification_mode: certificate` olduğu için hostname/SAN doğrulanmaz —
      sertifikayı yeniden üretmeye gerek yok, kopyalamak yeterli.
      ```bash
      scp <ist-node>:/app/elasticsearch-*/config/certs/elastic-certificates.p12 \
          <ank-node>:/app/elasticsearch-*/config/certs/
      chown elasticsearch:elasticsearch .../elastic-certificates.p12 && chmod 640 ...
      ```
- [ ] **Keystore parolaları Ankara node'larında da tanımlı**
      ```bash
      bin/elasticsearch-keystore add xpack.security.transport.ssl.keystore.secure_password
      bin/elasticsearch-keystore add xpack.security.transport.ssl.truststore.secure_password
      ```
- [ ] **9300 çift yönlü açık** — sadece master'lar değil, **7 node'un hepsi**
      birbirini görmeli. İstanbul `10.95.27.x`, Ankara `10.6.245.x` — farklı
      subnet'ler, routing ve firewall ayrı bir iş kalemi
- [ ] **Yedek için disk alanı** — `.30` ve `.26` üzerinde tar dosyalarının
      yazılacağı yer (`hosts.env` → `BACKUP_DEST_DIR`). Veri boyutu kadar yer
      gerekir; yoksa `tar czf - ... | ssh` ile doğrudan başka sunucuya
- [ ] **Yedek sırasında yazma trafiği durdurulacak** — `index-api`, `cms-catalog`
      ve varsa cron/batch işleri. Soğuk kopyanın tutarlı olması için şart
- [ ] **IST↔ANK RTT ölçüldü.** >100ms ise stretched cluster yanlış araçtır —
      her yazma bu gecikmeyi yer, CCR değerlendirilmelidir.
- [ ] **Her node'da `elasticsearch.yml` + `elasticsearch.keystore` yedeği alındı**

---

## Config dosyaları

| Node | Dosya | Durum |
|---|---|---|
| `ddp-prod-ist-master-1` | `../Toluca04.txt` | Güncellendi, **dağıtımı opsiyonel** — sonraki bakımda |
| `ddp-prod-ist-master-2` | `../Toluca05.txt` | aynı |
| `ddp-prod-ist-master-3` | `../Toluca06.txt` | aynı — **decommission edilecek** |
| `ddp-prod-ist-data-4` | `../Toluca07.txt` | aynı |
| `ddp-prod-ist-data-5` | `../Toluca08.txt` | aynı |
| `ddp-prod-ank-master-1` | `../config-ankara/ank-master-1.yml` | **yeni — migration için ŞART** |
| `ddp-prod-ank-data-1` | `../config-ankara/ank-data-1.yml` | **yeni — ŞART** |
| `ddp-prod-ank-data-2` | `../config-ankara/ank-data-2.yml` | **yeni — ŞART** |

İstanbul dosyalarındaki değişiklik: `path.repo` eklendi, `seed_hosts` hedef
master listesine güncellendi, `cluster.initial_master_nodes` yorum satırına
alındı. Migration bunlar olmadan da çalışır (bkz. "Rolling restart neden
opsiyonel"), ama dosyalar hazır bekliyor.

IP'ler dolduruldu: `ank-master-1` = `10.6.245.97`, `ank-data-1` = `10.6.245.99`,
`ank-data-2` = `10.6.245.100`. Yalnızca `network.publish_host` satırlarındaki
placeholder'lar duruyor — onlar sadece NAT varsa açılır.

Awareness ayarları **yaml'da değil**, persistent cluster setting olarak
veriliyor — dynamic ayarlar oldukları için rollback tek API çağrısı.

---

## Bilinen ve kabul edilen riskler

1. **İstanbul kaybı = cluster durur.** 3 master'ın 2'si İstanbul'da; Ankara'daki
   tek master quorum (2) sağlayamaz. Otomatik failover **yoktur**, manuel
   müdahale gerekir → [ROLLBACK.md § R7](ROLLBACK.md#r7--i̇stanbul-komple-kayip-migration-sonrasi-operasyonel-senaryo).
   Ankara kaybında İstanbul sorunsuz devam eder.
   *(Otomatik iki yönlü failover için 3. bir lokasyonda `voting_only` bir master
   gerekir — mevcut kapsam dışı, ileride değerlendirilebilir.)*

2. **`number_of_replicas: 0` olan index'ler Ankara'ya hiç gitmez.** Awareness
   replica üretmez, sadece var olan kopyaları dağıtır. `00-preflight.sh` listeler.

3. **`number_of_replicas >= 2` olan index'ler kalıcı yellow kalır.** 2 zone =
   shard başına 2 kopya; fazlası unassigned kalır. 2 zone için doğru değer `1`.

4. **Primary'yi bir zone'a sabitlemek ES'te mümkün değil.** Awareness sadece
   kopyaların dağılımını garanti eder. Ankara'ya düşen primary'lere yapılan
   yazmalar WAN gecikmesini yer.

5. **Yazma latency'si artar.** `wait_for_active_shards` varsayılanıyla her yazma
   replica'nın onayını bekler; replica artık Ankara'da.

---

## Migration sonrası

- [ ] `07-post-checks.sh` tamamen geçti
- [ ] `10.95.27.28:9200`'e bağlanan istemciler güncellendi
      ```bash
      grep -rn "10.95.27.28" ../../../index-api ../../../search-api ../../../ui ../../../cms-catalog
      ```
- [ ] Kibana / monitoring / load balancer hedefleri güncellendi
- [ ] Yazma latency'si ve IST↔ANK hat kullanımı izleniyor
- [ ] **Birkaç gün stabil çalışmadan** `.28`'in `path.data`'sını, `/yedek/es-*.tar.gz`
      dosyalarını ve `.pre-migration-*` yaml/keystore yedeklerini **silmeyin**

### Açık madde: yedekleme yöntemi değişmeli

Migration'dan sonra data node 2'den 4'e çıkıyor, her shard'ın 2 kopyası iki
lokasyona dağılıyor — **hiçbir tek node tüm veriyi taşımıyor.** Soğuk kopya
yöntemi bu topolojide geçersiz.

Yapılacak: NFS veya S3/MinIO + `repository-s3` kurup gerçek ES snapshot'ına
geçmek. `path.repo` satırları config'lerde hazır, tek eksik depolama. Bu
yapılana kadar elinizdeki tek yedek migration öncesine ait.
