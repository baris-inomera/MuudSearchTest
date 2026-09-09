# ROLLBACK — Yedekleme ve Geri Dönüş Prosedürleri

> `ddp-prod-cluster` · İstanbul tek lokasyon → İstanbul + Ankara stretched cluster
>
> Bu doküman migration sırasında **açık tutulur**. Bir sorun anında baştan
> okunacak bir metin değil, doğrudan senaryoya atlanacak bir başvuru kaynağıdır.

---

## 0. Yedekleme stratejisi — SOĞUK KOPYA

> Ortamda paylaşımlı disk (NFS) kurulamadığı için **ES snapshot'ı kullanılmıyor.**
> Snapshot, `path.repo` ile tanımlı paylaşımlı bir depolama şart koşar.
> Onun yerine node'ların veri klasörlerinin soğuk kopyasını alıyoruz.

### Neden bu yöntem burada çalışıyor

5 index × 1 shard × `rep=1` = **10 shard kopyası, 2 data node.**
Elasticsearch bir shard'ın primary'si ile replica'sını asla aynı node'a koymaz —
dolayısıyla `.29` ve `.30`'un **her biri 5 index'in de birer kopyasını taşır.**

**Tek bir data node'un veri klasörü = tüm verinin tam kopyası.**

> ⚠️ Bu varsayım index/shard sayısı değişirse **bozulur**. `00-preflight.sh` ve
> `02-backup.sh` her çalıştığında bunu shard shard yeniden doğrular.

### Ne yedekleniyor

| # | İçerik | Nasıl | Nerede |
|---|---|---|---|
| 1 | Shard verisi | `.30`'un `path.data` klasörü, tar | `/yedek/es-data-*.tar.gz` |
| 2 | **Cluster state** (mapping, template, ILM, alias) | `.26`'nın `path.data` klasörü, tar | `/yedek/es-master-*.tar.gz` |
| 3 | Cluster ayarları, mapping, alias, template, ILM | JSON dump | `migration/artifacts/backup-<ts>/` |
| 4 | Shard & node yerleşim fotoğrafı, doküman sayısı | `_cat` çıktıları | aynı dizin |
| 5 | `elasticsearch.yml` + `elasticsearch.keystore` | **elle** `cp` | node'un kendi disk'i |

**(2) neden şart:** data node'un klasöründe shard verisi var, ama index
mapping'leri, ayarları ve template'ler **cluster state** içinde ve o master
node'larda duruyor. İkisi olmadan tam geri dönüş yok.

**(5) neden elle:** veri kopyası node konfigürasyonunu kurtarmaz.
```bash
cd /app/elasticsearch-*/config
cp elasticsearch.yml       elasticsearch.yml.pre-migration-$(date +%Y%m%d)
cp elasticsearch.keystore  elasticsearch.keystore.pre-migration-$(date +%Y%m%d)
```

### Tutarlılık şartı

Soğuk kopya alınırken **ES'e yazma olmamalı.** `02-backup.sh` bunu soruyor ve
kopyalamadan önce `POST /_flush` çalıştırıyor. Yazma trafiği kapalıyken alınan
kopya tam tutarlıdır.

### Yedeğin doğrulanması

```bash
# 1. tar dosyaları yerinde ve makul boyutta mı?
ls -lh /yedek/es-data-*.tar.gz /yedek/es-master-*.tar.gz

# 2. tar bozuk değil mi?
tar tzf /yedek/es-data-*.tar.gz | head

# 3. Yedek kaydı
cat migration/artifacts/LAST_BACKUP
```

### Üç uyarı

1. **tar dosyalarını bulundukları makinenin DIŞINA kopyalayın.** `.30`'un yedeği
   `.30`'un diskinde duruyorsa, `.30` gittiğinde yedek de gider.
2. **Bu, ES snapshot'ı değil.** Incremental değil, tek bir node'a geri konup
   "bitti" denemez (bkz. R6). Son çare aracı — asıl rollback yolları R1–R5.
3. **Migration'dan sonra bu yöntem GEÇERSİZ olur.** Data node sayısı 4'e çıkıp
   kopyalar iki lokasyona dağılınca hiçbir tek node tüm veriyi taşımaz. Yeni
   topolojide gerçek snapshot'a (NFS veya S3/MinIO) geçilmeli — açık madde.

---

## 1. Geri dönüş noktaları

| Faz | Yapılan | Geri dönüş | Maliyet | Veri kaybı riski |
|---|---|---|---|---|
| 0 · rolling restart *(opsiyonel)* | yaml değişikliği (`seed_hosts`, `initial_master_nodes`) | R5 | Node başına 1 restart | Yok |
| 0 · backup | Soğuk kopya (`.30` + `.26`) | — | 2 node × birkaç dakika, cluster yellow | Yok |
| 1 · join | ANK node'ları katıldı | R1 | ANK'ta veri yoksa anında | Yok |
| 2 · awareness | Shard'lar ANK'a taşındı | R2 + R1 | Taşınan veri kadar geri kopyalama | Yok |
| 3 · decommission | `.28` çıkarıldı | R4 | Dakikalar | Yok |
| — | Metadata/veri bozulması | **R6** | Saatler + yedekten sonraki yazmalar kaybolur | **VAR** |

**Faz 3'e kadar hiçbir adım veri kaybettirmez.** Tüm veri her an İstanbul'da
tam kopya halinde durur. Yedek, "beklenmedik bir şey olursa" içindir — normal
akışta kullanılmaz.

---

## 2. Abort kriterleri

Aşağıdakilerden biri gerçekleşirse **durun, bir sonraki faza geçmeyin**:

- Cluster bir fazın sonunda `green` olmuyor ve nedeni `_cluster/allocation/explain`
  ile 30 dakika içinde açıklanamıyor
- İstanbul node'larından biri migration sırasında beklenmedik şekilde düşüyor
- IST↔ANK hattı doyuyor ve **canlı uygulama latency'si** bozuluyor
- Doküman sayısı beklenmedik biçimde **azalıyor**
- Ankara node'ları katılıyor ama `cluster_name` veya `rack` değeri yanlış
- Yedek tar dosyaları eksik/bozuk, ya da `02-backup.sh` tam kopya doğrulamasında
  "hiçbir data node tüm shard'ları taşımıyor" dedi

---

## 3. Senaryolar

### R1 — Ankara node'ları sorunlu, geri dönmek istiyoruz
**Ne zaman:** Faz 1 veya Faz 2'de; ANK node'ları kararsız, ağ yetersiz,
sertifika/versiyon sorunu çözülemiyor.

**Veri kaybı:** Yok. İstanbul'da tam kopya var.

```bash
./rollback.sh drain-ankara
```

Script sırayla: awareness'ı kapatır → `allocation.exclude._name` ile ANK
node'larını dışlar → shard'ların İstanbul'a dönmesini bekler → Ankara'nın
gerçekten boşaldığını **doğrular** → siz node'ları kapattıktan sonra exclude
listesini temizler.

**Süre:** Ankara'ya taşınmış veri miktarı kadar geri kopyalama.

> Ankara node'ları henüz join olmadıysa (Faz 1'in başı) hiçbir şey yapmanıza
> gerek yok — servisleri durdurun, `path.data`'yı silin, bitti.

---

### R2 — Awareness açıldı, cluster yellow'da takıldı
**Belirti:** Faz 2 sonrası `unassigned_shards > 0` ve düşmüyor.

**Önce teşhis, sonra rollback:**
```bash
curl -u $ES_USER:$ES_PASSWORD "$ES_URL/_cluster/allocation/explain?pretty"
```

| `explain` çıktısı | Gerçek neden | Çözüm |
|---|---|---|
| `there are too many copies of the shard allocated to nodes with attribute [rack]` | index'in `number_of_replicas` değeri > 1 | `PUT /<index>/_settings {"index":{"number_of_replicas":1}}` |
| `the node is above the high watermark` | Ankara diski dolu | Disk ekle veya `cluster.routing.allocation.disk.watermark.*` |
| `no attempt has been made yet` | Sadece sırada bekliyor | Bekleyin, rollback etmeyin |
| `node_left` / `throttled` | Recovery kuyruğu | Bekleyin veya throttle'ı yükseltin |

Gerçekten geri almak gerekiyorsa:
```bash
./rollback.sh awareness
```
Awareness kuralını kaldırır, shard'lar serbestçe dengelenir. **Veri kaybı yok** —
bu yalnızca bir yerleştirme kuralıdır. Ankara'yı tamamen boşaltmak için ardından
`./rollback.sh drain-ankara`.

---

### R3 — WAN hattı doydu, canlı sistem yavaşladı
**Belirti:** Faz 2 relocation sırasında uygulama latency'si bozuldu.

**Acil frene basın (relocation'ı fiilen durdurur, geri almaz):**
```bash
./rollback.sh throttle       # -> 1) Durdur
```

Sonra ya mesai dışına erteleyin, ya da düşük bir hızla devam edin
(`hosts.env` içindeki `RECOVERY_MAX_BYTES_PER_SEC`'i düşürüp seçenek 3).

Yarım kalmış relocation **sorun değildir** — cluster her iki durumda da
tutarlıdır, sadece bir kısım shard hâlâ tek lokasyondadır.

---

### R4 — `.28` çıkarıldı ama sorun oldu
**Belirti:** Faz 3 sonrası master seçimi kararsız, cluster red/yellow, ya da
`.28`'in çıkarılmasının erken bir karar olduğu anlaşıldı.

**Ön koşul:** `.28` üzerindeki `path.data` **silinmemiş** olmalı.
Bu yüzden `06-decommission-ist-master-3.sh` onu silmenizi açıkça yasaklıyor.

```bash
./rollback.sh readd-master3
```
`voting_config_exclusions`'ı temizler, siz node'u başlattıktan sonra geri
katıldığını doğrular.

> `path.data` silinmişse de sorun değil: `.28` sadece `[master]` rolünde, üzerinde
> shard yok. Boş olarak da katılır.

---

### R5 — Rolling restart sonrası node cluster'a katılmıyor
**Belirti:** Faz 0'da bir node restart sonrası geri gelmiyor.

```bash
tail -200 /app/elasticsearch-*/logs/ddp-prod-cluster.log
```

| Logdaki ifade | Neden | Çözüm |
|---|---|---|
| `cluster UUID mismatch` / node tek başına cluster kurmuş | yaml'da `cluster.initial_master_nodes` bırakıldı | Servisi durdur → satırı sil → **`path.data`'yı sil** (master node'da veri yok; data node ise ASLA silme, yaml'ı düzeltip yeniden başlat) → başlat |
| `failed to establish trust` / `handshake_failure` | Sertifika/keystore parolası uyuşmuyor | `.p12`'yi tekrar kopyala, keystore parolalarını ekle |
| `no route to host` / `connect_timeout` | 9300 kapalı | `nc -z <ip> 9300` ile iki yönlü doğrula |
| `unknown setting` | yaml yazım hatası | Hatalı satırı düzelt |

**En hızlı geri dönüş:** eski yaml'ı yerine koyup restart edin.
```bash
cd /app/elasticsearch-*/config
cp elasticsearch.yml.pre-migration-<tarih> elasticsearch.yml
systemctl restart elasticsearch
```

Kilitler açık kalmışsa:
```bash
./rollback.sh unlock
```

---

### R6 — Veri/metadata bozuldu → soğuk yedekten tam geri yükleme
**Ne zaman:** SON ÇARE. R1–R5 işe yaramadı, index'ler bozuldu veya cluster
metadata'sı kurtarılamıyor.

**Veri kaybı: VAR.** Yedek anından sonraki tüm yazmalar kaybolur.

#### Önce şunu anla — bu bir snapshot restore değil

Soğuk kopyayı tek bir node'a geri koyup "bitti" diyemezsin. Cluster'daki diğer
kopya daha yeni olur; ES senin geri koyduğun eski kopyayı **bayat** sayar ve
üzerine yenisini kopyalar. Hiçbir şey geri gelmez.

Bu yüzden geri yükleme **tüm cluster'ı** kapsar ve `unsafe-bootstrap` gerektirir:

```
1. Yazma trafiğini durdur
2. TÜM node'ları durdur (İstanbul + varsa Ankara)
3. .26  -> cluster state yedekten geri kondu
4. .27, .28 -> master veri klasörleri kenara alındı (state'leri yedekten yeni)
5. .26  -> elasticsearch-node unsafe-bootstrap
6. .30  -> shard verisi yedekten geri kondu
   .29  -> veri klasörü kenara alındı (.30'dan yeniden kopyalanacak)
7. Sırayla başlat: .26 -> .27 -> .28 -> .30 -> .29
```

Script bunu adım adım yürütüyor, her aşamada onay istiyor ve cluster adını
yazdırarak kazara çalıştırmayı engelliyor:

```bash
./restore-from-backup.sh
```

**Eski klasörleri silme, yeniden adlandır** (`.bozuk-*`, `.eski-*`) — geri
dönüşün geri dönüşü lazım olabilir.

#### Daha hafif alternatif: metadata'dan yeniden kurma
Sadece cluster metadata'sı bozulduysa (veri sağlamsa), `artifacts/backup-<ts>/`
altındaki JSON dump'larından index settings, mapping, template, ILM ve alias'ları
elle geri yazmak çoğu zaman daha hızlı ve risksizdir. Önce bunu değerlendirin.

---

### R7 — İSTANBUL KOMPLE KAYIP (migration sonrası operasyonel senaryo)

> Bu bir rollback değil, **kabul edilmiş bir riskin** prosedürü.
> 3 master'ın 2'si İstanbul'da: Ankara'daki tek master quorum (2) sağlayamaz,
> cluster kendi kendine ayağa kalkamaz.
>
> **Ankara kaybında İstanbul sorunsuz devam eder** — bu senaryo sadece
> İstanbul'un kaybı içindir.

**Önce karar verin:** İstanbul saatler içinde geri gelecekse **HİÇBİR ŞEY
YAPMAYIN**. Aşağıdaki prosedür geri dönüşü zordur ve veri kaybı riski taşır.
İstanbul geri geldiğinde cluster kendiliğinden toparlanır.

Geri gelmeyecekse, iki yol var:

#### Yol A — Soğuk yedekten yeni cluster (öngörülebilir ama eski)
Migration öncesi alınan tar dosyaları Ankara'dan erişilebiliyorsa, Ankara
node'larıyla temiz bir cluster kurup R6 prosedürünü uygulayın. Kayıp = yedek
anından sonraki **her şey** — migration'dan bu yana geçen tüm süre.

> Bu yüzden migration sonrası bir an önce gerçek snapshot'a (NFS veya S3/MinIO)
> geçilmeli. Elinizdeki tek yedek migration öncesine ait olduğu sürece Yol A her
> geçen gün daha pahalı hale gelir.

#### Yol B — `unsafe-bootstrap` (mevcut veriyi kullanır, riskli ama güncel)
Ankara'daki mevcut shard kopyalarını kullanır. Kayıp = İstanbul'da onaylanmış
ama Ankara'ya replikasyonu tamamlanmamış yazmalar.

```bash
# 1. Ankara'daki TÜM ES servislerini durdurun
systemctl stop elasticsearch      # ank-master-1, ank-data-1, ank-data-2

# 2. SADECE ank-master-1 üzerinde:
/app/elasticsearch-*/bin/elasticsearch-node unsafe-bootstrap
#    Uyarıyı okuyup onaylayın. Bu node'un cluster state'ini tek doğru kaynak
#    kabul eder ve yeni bir cluster oluşturur.

# 3. ank-data-1 ve ank-data-2 üzerinde:
/app/elasticsearch-*/bin/elasticsearch-node detach-cluster
#    Node'ları eski cluster UUID'sinden koparır, yenisine katılabilir hale getirir.

# 4. Önce ank-master-1'i, sonra data node'ları başlatın
systemctl start elasticsearch

# 5. Cluster ayağa kalkınca awareness'ı KAPATIN — tek zone kaldı,
#    force awareness tüm replica'ları unassigned bırakır:
curl -u $ES_USER:$ES_PASSWORD -XPUT "$ES_URL/_cluster/settings" \
  -H 'Content-Type: application/json' -d '{
    "persistent": {
      "cluster.routing.allocation.awareness.attributes": null,
      "cluster.routing.allocation.awareness.force.rack.values": null
    }}'

# 6. Replica sayısını kalan 2 data node'a göre ayarlayın (rep=1 iki kopya demek,
#    ank-data-1 ve ank-data-2'ye dağılır — bu doğru değerdir):
curl -u $ES_USER:$ES_PASSWORD -XPUT "$ES_URL/_all/_settings" \
  -H 'Content-Type: application/json' -d '{"index":{"number_of_replicas":1}}'

# 7. seed_hosts'u güncelleyip yeni master'lar ekleyin — 1 master ile kalmayın.
```

**Uyarı:** `unsafe-bootstrap` adını hak eder. Sessiz veri kaybı yapabilir.
Yol A mümkünse Yol A'yı seçin.

---

## 4. Hızlı komut kartı

```bash
# Durum
./rollback.sh status
curl -u $ES_USER:$ES_PASSWORD "$ES_URL/_cluster/health?pretty"
curl -u $ES_USER:$ES_PASSWORD "$ES_URL/_cluster/allocation/explain?pretty"
curl -u $ES_USER:$ES_PASSWORD "$ES_URL/_cat/shards?v&s=state"

# Frene bas
./rollback.sh throttle          # WAN trafiğini kes
./rollback.sh awareness         # Awareness kuralını kaldır

# Geri çekil
./rollback.sh drain-ankara      # Ankara'yı boşalt
./rollback.sh readd-master3     # .28'i geri al
./rollback.sh unlock            # allocation/rebalance kilitlerini aç

# Son çare
./restore-from-backup.sh
```

---

## 5. Migration bittikten sonra

### Silinmeyecekler — en az birkaç gün stabil çalışma görmeden

- [ ] `.28` üzerindeki `path.data` — R4'ün ön koşulu
- [ ] `/yedek/es-data-*.tar.gz` ve `/yedek/es-master-*.tar.gz` — R6'nın tek dayanağı
- [ ] `elasticsearch.yml.pre-migration-*` ve `elasticsearch.keystore.pre-migration-*` — R5'in en hızlı yolu
- [ ] `migration/artifacts/` dizini — karşılaştırma referansları

### Açık madde: yedekleme yöntemi değişmeli

Migration'dan sonra data node sayısı 2'den 4'e çıkıyor ve her shard'ın 2 kopyası
iki lokasyona dağılıyor. Artık **hiçbir tek node tüm veriyi taşımıyor** — soğuk
kopya yöntemi bu topolojide geçersiz.

Yapılacak: paylaşımlı disk (NFS) veya S3/MinIO + `repository-s3` kurup gerçek ES
snapshot'ına geçmek. `path.repo` satırları config dosyalarında hazır duruyor,
tek eksik depolama. Bu yapılana kadar elinizdeki tek yedek migration öncesine
ait — her geçen gün değeri düşüyor.
