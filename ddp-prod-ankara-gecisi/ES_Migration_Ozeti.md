# ES Cluster Migration Özeti: İstanbul → İstanbul + Ankara

> Yönetici özeti. Uygulama adımları için [`migration/README.md`](migration/README.md),
> sorun anında [`migration/ROLLBACK.md`](migration/ROLLBACK.md).
>
> **Not:** Bu dosyanın ilk hali gerçek config'lerle uyuşmuyordu ("2 master + 3 data
> İstanbul" yazıyordu). Aşağıdaki tablo `Toluca04-08.txt` dosyalarından okunan
> gerçek duruma göre düzeltilmiştir.

## Mevcut Durum vs. Hedef

| | Mevcut | Hedef |
|---|---|---|
| Master (İstanbul) | 3 — `.26` `.27` `.28` | **2** — `.26` `.27` |
| Master (Ankara) | 0 | **1 (yeni)** |
| Data (İstanbul) | 2 — `.29` `.30` | 2 (değişmiyor) |
| Data (Ankara) | 0 | **2 (yeni)** |
| **Toplam** | **5 node** | **7 node** |

`ddp-prod-ist-master-3` (`10.95.27.28`) decommission edilir.
Master sayısı 3 → **quorum 2**.

Cluster adı `ddp-prod-cluster` **değişmez**. Ankara'da veri yok, node'lar boş
olarak katılır.

## Karar verilenler

| Konu | Karar |
|---|---|
| Awareness attribute | Mevcut `node.attr.rack` kullanılır (`ddp-istanbul` / `ddp-ankara`) — yeni attribute uydurulmaz |
| Awareness ayarı nerede | yaml'da değil, **persistent cluster setting** — dynamic olduğu için rollback tek API çağrısı |
| Quorum | 3 master, 2'si İstanbul'da. İstanbul kaybında otomatik failover **yok** (bilinçli kabul) |
| `.28`'in çıkarılma anı | Ankara master'ı katıldıktan **sonra** — master sayısı hiçbir adımda 3'ün altına inmez |

## Uygulama sırası

| Faz | Script | Kesinti | Not |
|---|---|---|---|
| 0 | `00-preflight.sh` | yok | Salt okunur denetim |
| 0 | `01-rolling-restart.sh` | yok | `path.repo` + final `seed_hosts` (node başına restart) |
| 0 | `02-backup.sh` | yok | **Snapshot — geri dönüş noktası** |
| 1 | `03-join-ankara.sh` | yok | ANK node'ları katılır, `rebalance=none` |
| 2 | `04-enable-awareness.sh` | yok | Veri WAN üzerinden Ankara'ya taşınır (uzun) |
| 3 | `06-decommission-ist-master-3.sh` | yok | Voting exclusion + servis durdurma |
| 4 | `07-post-checks.sh` | yok | Doğrulama |

İzleme: `05-watch-relocation.sh` (salt okunur, ayrı terminal).

### Sıralamayı belirleyen üç kısıt

1. **`path.repo` en başta.** Mevcut config'lerin hiçbirinde yok → şu haliyle
   snapshot alınamıyor, yani yedeksiz migration olurdu. Static ayar, restart şart.
2. **Awareness join'den sonra.** `force.rack.values` ANK node'ları yokken açılırsa
   tüm replica'lar unassigned kalır, cluster anında yellow olur.
3. **`.28` en sonda.** Önce çıkarılsaydı migration'ın en riskli bölümü sıfır
   master toleransıyla geçilirdi.

## Config değişiklikleri

**Mevcut 5 dosya** (`Toluca04-08.txt`) — üç değişiklik:
- `path.repo: ["/backup/elasticsearch-snapshots"]` eklendi
- `discovery.seed_hosts` hedef master listesine güncellendi (`.28` çıktı, ANK master girdi)
- `cluster.initial_master_nodes` yorum satırına alındı (bootstrap sonrası zararlı bir foot-gun)

**Yeni 3 dosya** (`config-ankara/`): `ank-master-1.yml`, `ank-data-1.yml`, `ank-data-2.yml`
— İstanbul config'lerinin birebir kalıbı, sadece `node.name`, `node.attr.rack`,
`network.host` farklı. `cluster.initial_master_nodes` **bilinçli olarak yok**.

`<<ANK_*_IP>>` placeholder'ları doldurulmalıdır.

## Yedekleme

| Ne | Nasıl | Nerede |
|---|---|---|
| Tüm veri + global state | ES snapshot | `/backup/elasticsearch-snapshots` |
| Cluster/index ayarları, mapping, alias | JSON dump | `migration/artifacts/` |
| Doküman sayısı (bütünlük referansı) | `doc-count.txt` | `migration/artifacts/` |
| `elasticsearch.yml` + keystore | **elle `cp`** | node'un kendi disk'i |

Snapshot node config'ini kurtarmaz — yaml/keystore yedeği elle alınır.

**Faz 3'e kadar hiçbir adım veri kaybettirmez**; veri her an İstanbul'da tam
kopya halinde durur. Detaylı senaryolar (R1–R7) `migration/ROLLBACK.md`'de.

## Açık riskler

1. **İstanbul kaybı = cluster durur.** 3 master'ın 2'si İstanbul'da; Ankara'daki
   tek master quorum sağlayamaz. Manuel müdahale gerekir (ROLLBACK.md § R7).
   Ankara kaybında İstanbul sorunsuz devam eder.
   Otomatik iki yönlü failover için 3. lokasyonda bir `voting_only` master
   gerekirdi — kapsam dışı bırakıldı.
2. **`rep=0` index'ler Ankara'ya gitmez**, **`rep>=2` index'ler kalıcı yellow
   kalır.** 2 zone için doğru değer `rep=1`. Preflight ikisini de listeler.
3. **Primary'yi zone'a sabitlemek ES'te mümkün değil** — Ankara'ya düşen
   primary'lere yazma WAN gecikmesini yer.
4. **RTT > 100ms ise stretched cluster yanlış araçtır**, CCR değerlendirilmeli.
   Preflight ölçüp raporlar.
5. **`10.95.27.28:9200`'e bağlanan istemciler** (index-api, search-api, ui, Kibana,
   LB) migration sonrası güncellenmeli.
