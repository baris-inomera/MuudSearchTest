# BulguAnalyzer

Test koşusu bittikten sonra NOK satırlarını otomatik analiz edip Excel raporuna
**8 sütun** ve bir **AI Koşu Özeti** sayfası ekler. Elle "Notlar" yazma işini bitirir.

**API anahtarı olmadan da çalışır** — kural motoru sıfır maliyetle etiketler.

## Üç katman

**Katman 0 — deterministik olgular (`RunSignals`).** Hesapla bulunur, hiçbir modele
sorulmaz:

- **Ağırlıklı skor.** Raporda yazan skor, o sonucun kendi index'i içindeki skorudur
  ve index'ler arası karşılaştırılamaz. Gerçek sırayı `skor × topHitsScoreWeight`
  belirler (`performers 0.38 · playlists 0.335 · songs 0.27 · playlist_groups 0.18
  · albums 0.15 · videos 0.00`). Bu model 01.09.2026 koşusundaki 31 NOK'un 30'unda
  gözlenen sırayı birebir üretti.
- **Ağırlık devrilmesi** — ham skoru yüksek kaydın farklı index'ten düşük skorlunun
  altında kalması
- **Duplikasyon** — aynı kaydın top-N'de birden fazla slot yemesi
- **Ölü sinyal** — bir index'teki *tüm* sonuçlarda popülerliğin 0/boş olması
- **Birebir başlık eşleşmesi** — tier/exact bonusu göstergesi
- **Regresyon** — önceki raporda OK olan case'in artık NOK olması

**Katman 1 — kural motoru (`KuralMotoru`), maliyetsiz.** Katman 0'ın olgularını
`kurallar.json`'daki kurallarla eşleştirir. 01.09.2026 koşusunda **31/31 satırı**
etiketledi: 28'i yüksek güvenle, 3'ü "inceleme önerilir" olarak.

**Katman 2 — LLM (opsiyonel).** Yalnızca **hiçbir kuralın eşleşmediği** satırlar için
çağrılır. Anahtar yoksa o satırlar `INCELENMELI` kalır, koşu normal biter.

Ayrıca `analyzer.runSummary=true` iken koşu başına **tek** bir güçlü model çağrısı
koşu geneli örüntüleri özetler.

## Kurulum

Anahtar istemiyorsan hiçbir şey yapma — kural motoru zaten çalışır.

LLM katmanını da açmak için proje köküne `analyzer.properties` koy
(`analyzer.properties.example`'ı kopyala) ve `analyzer.apiKey=` satırını doldur.
Dosya `.gitignore`'da. Alternatif: `ANTHROPIC_API_KEY` ortam değişkeni.

Test class'larında `writeBulgu`'nun altındaki tek satır entegrasyonu sağlar:

```java
ExcelTestReportWriter.writeBulgu(ROWS, "reports/main-cases");
BulguAnalyzer.annotateLatest("reports/main-cases");
```

Elde duran bir raporu analiz etmek için:

```
mvn -q test-compile org.codehaus.mojo:exec-maven-plugin:3.1.0:java ^
  -Dexec.classpathScope=test -Dexec.mainClass=analyzer.BulguAnalyzer ^
  -Dexec.args="reports/main-cases"
```

Klasör verirsen en yeni raporu bulur ve bir öncekiyle karşılaştırıp regresyonları
işaretler.

## Bilinen istisnalar — kuralların yapamadığı yargı

Kural motoru mekanizmayı teşhis eder ama **yargı veremez**: "beklenti hatalı mı?",
"içerik ortamda var mı?" gibi sorular insan kararı gerektirir. Bir kez karar verilen
case'i her koşuda yeniden tartışmamak için karar
`src/test/resources/analyzer/bilinen-istisnalar.json` dosyasına yazılır. Bu liste
**tüm kurallardan önce** bakılır ve onları ezer.

```json
{
  "caseId": "SARKI_TAM_42",
  "aktif": true,
  "durum": "BLOCKED",
  "kategori": "YANLIS_BEKLENTI",
  "aciklama": "İçerik prod ortamında yok, case koşulamıyor.",
  "karar": "Ekip notu, 04.08.2026 koşusu",
  "gecerlilik": "İçerik prod'a yüklendiğinde bu satır silinmeli."
}
```

`durum` değerleri: `BLOCKED` (içerik yok), `YANLIS_BEKLENTI` (test hatalı),
`KABUL_EDILDI` (bulgu gerçek ama bilinçli kabul edildi — tek bulgu=true olan durum).

`aktif: false` olan kayıtlar **yok sayılır** — onay bekleyen öneriler böyle tutulur.
Dosyada şu an üç öneri `aktif: false` ile duruyor; incelendikten sonra `true` yapın.

Özet sayfası istisnaları ayrı sayar ve "motor performansı değerlendirilirken bunlar
NOK sayısından düşülmeli" uyarısı basar — daha önce elle yaptığınız BLOCKED ayrımı
artık otomatik.

## Kuralları büyütmek — "biz eğiteceğiz" döngüsü

Bilgi kodda değil, `src/test/resources/analyzer/kurallar.json` içinde. Ekip bir yorum
ürettiğinde oraya kural ekler; **kod değişmez, yeniden derleme gerekmez.**

Bir kural iki parçadan oluşur: `kosullar` (hepsi VE ile bağlı) ve `sonuc`.

```json
{
  "id": "K14-ornek-kural",
  "oncelik": 87,
  "aciklama": "Ne zaman tetiklendiğini bir cümleyle yaz.",
  "kosullar": { "testTipiIcerir": "Lyrics", "duplikasyon": true },
  "sonuc": {
    "bulgu": true,
    "kategori": "RANKING",
    "kokNeden": "kisa etiket — ayni mekanizmadakiler ayni metni almali",
    "guven": 0.8,
    "cozum": "NEREDE: <index tanımı + id>\nNE YAP: <alan yolu>: <mevcut> → <öneri>\nDOĞRULA: <hangi sorgu, ne gözlenmeli, hangi aile tekrar koşulmalı>",
    "devNotu": "NEDEN: hangi skor terimi / ağırlık / ayar bu sırayı üretti."
  }
}
```

`cozum` **aksiyon**, `devNotu` **analiz** — bkz. yukarıdaki "İki sütunu
karıştırmayın". En sık yapılan hata ikisini ters yazmaktır; o zaman rapor
teşhis dolu ama uygulanabilir hiçbir şey içermez.

Kurallar **önceliğe göre büyükten küçüğe** denenir, **ilk eşleşen kazanır**. Yeni
kuralın mevcut bir aileyi yutmaması için önceliği dikkatli seç — genel kurallar
düşük (50–80), spesifik kurallar yüksek (90+) olmalı.

**Kullanılabilir koşullar:**

| Koşul | Tip | Anlamı |
|---|---|---|
| `testTipiIcerir` | metin | Test tipi bu metni içeriyor mu (Türkçe duyarsız) |
| `sorguRegex` | regex | Sorgu bu kalıba uyuyor mu |
| `ilkIndex` | metin | 1. sıradaki sonucun index tipi |
| `bulunamadi` | bool | Hedef top-10'da hiç yok mu |
| `beklenenSiraEnAz` / `beklenenSiraEnCok` | sayı | Hedefin bulunduğu sıra aralığı |
| `agirlikDevrilmesi` | bool | Cross-index sıra devrilmesi var mı |
| `devrilenUstIndex` / `devrilenAltIndex` | metin | Devrilmede üstte/altta kalanın index'i |
| `duplikasyon` | bool | Aynı kayıt birden fazla mı |
| `oluPopulerlik` | metin | Bu index'te popülerlik tamamen 0 mı |
| `populerlikEksikEnAz` | sayı | Popülerliği boş kaç kayıt var |
| `birebirEslesmeEnAz` | sayı | Adı sorguyla birebir aynı kaç sonuç var |
| `indexCesidiEnAz` | sayı | Kaç farklı index'ten sonuç geliyor |
| `ustteperformerVar` | bool | Hedefin üstünde performer kaydı var mı |
| `ilkSonucSorguylaAyni` | bool | 1. sıradaki sonucun adı sorguyla harfi harfine aynı mı |
| `regresyon` | bool | Önceki koşuda OK muydu |

**Metin yer tutucuları:** `{sorgu}` `{testTipi}` `{konum}` `{beklenenSira}` `{ilkIndex}`
`{ustIndex}` `{altIndex}` `{oluIndex}` `{birebirSayisi}` `{ustAgirlik}` `{altAgirlik}`

Konum yazarken **`{konum}` kullanın** — hedef bulunduysa "3. sırada", bulunamadıysa
"top-10 dışında" üretir. `{beklenenSira}. sırada` yazarsanız hedefin bulunamadığı
satırlarda cümle bozulur ("top-10 dışı. sırada").

**Nasıl doğrularsın:** kuralı ekle, `BulguAnalyzer`'ı elindeki bir rapor üzerinde
çalıştır, **Kaynak** sütununa bak — hangi satırın hangi kuraldan geldiğini gösterir.
Yanlış kural tetikleniyorsa ya koşulu daralt ya önceliği düşür.

`few-shot.json` ise LLM katmanı için aynı işi yapar; oradaki her kayıt prompt'a örnek
olarak girer.

## Otonomi kuralları

1. **Hiçbir satır gizlenmez.** `bulgu=false` sadece bir etikettir.
2. **Düşük güvenli "bulgu değil" kararı yükseltilir** (`EVET`'e çevrilir, çözüm alanına
   `[DÜŞÜK GÜVEN]` eklenir).
3. **Analiz edilemeyen satır kaybolmaz** — `INCELENMELI` olarak işaretlenir,
   deterministik olgular yine yazılır. Analyzer testi asla patlatmaz.

## Sütunlar

`Gerçek Bulgu?` · `Kategori` · `Kök Neden` · **`NE YAPILMALI (aksiyon)`** ·
`NEDEN OLDU (analiz)` · `Güven` · `Kaynak` (kural id'si / `LLM` / `-`) ·
`Ağırlıklı Skor`

Sıra bilerek böyle: önce karar, sonra **aksiyon**, sonra analiz. Ağırlıklı skor
bloğu geniş olduğu için en sağda — okurken ilk bakılması gereken şey o değil.

### İki sütunu karıştırmayın

Bu ayrım bu dosyadaki en önemli kural. `NE YAPILMALI` bir geliştiricinin okuyup
doğrudan uygulayabileceği aksiyon; `NEDEN OLDU` mekanizmanın anlatımı.

**`NE YAPILMALI` şablonu** (kuralın `cozum` alanı):

```
NEREDE:   Performers index tanımı (id 3)
NE YAP:   extraBonusFields → performerName.exact → weight: 10 → 4
YAPMA:    topHitsScoreWeight 0.38'i düşürme — Sanatçı · Kısmi Ad'daki 44 case'i bozar
DOĞRULA:  "selda" sorgusunda Selda Bağcan 1. sıraya çıkmalı; sonra Sanatçı ailesini tekrar koş
```

`NEREDE` / `NE YAP` / `DOĞRULA` zorunlu, `YAPMA` gerektiğinde. Değişiklik bir
regresyon riski taşıyorsa `DOĞRULA` hangi case ailesinin tekrar koşulacağını da
söylemeli.

**Yasak ifadeler:** "gözden geçirin", "iyileştirilmeli", "optimize edilmeli",
"uygun şekilde ayarlanmalı". Somut alan adı ve değer yazamıyorsan bunun yerine
**hangi bilginin eksik olduğunu ve nasıl elde edileceğini** yaz — "index
tanımındaki `scriptSource`'u açıp `pNorm`'un hangi alandan okunduğuna bakın" da
bir aksiyondur.

Motor tarafında yapılacak bir şey olmayan satırlarda (`YANLIS_BEKLENTI`, `BLOCKED`)
sütun boş bırakılmaz; "MOTOR TARAFINDA AKSIYON YOK" diye başlayıp aksiyonun nerede
olduğunu söyler — istisna listesine ekleme, beklenti güncelleme, iş birimiyle karar.

**`NEDEN OLDU` (kuralın `devNotu` alanı)** hangi skor teriminin, hangi ağırlığın,
hangi ayarın bu sırayı ürettiğini anlatır. Buraya aksiyon yazılmaz.

## Maliyet

Kural motoru bedava. LLM yalnızca kuralsız satırlar için çalışır ve
`reports/.analyzer-cache.json` ile aynı sorgu+sonuç imzası tekrar sorulmaz.

## Dosyalar

| Dosya | İş |
|---|---|
| `IndexWeights` | topHitsScoreWeight tablosu — ağırlık değişirse **sadece burası** |
| `SonucParser` / `SearchHit` / `ReportRow` | Rapor metnini yapısal veriye çevirir (eski + yeni format) |
| `RunSignals` / `RowContext` | Katman 0 — deterministik olgular |
| `IstisnaListesi` + `bilinen-istisnalar.json` | Ekibin kalıcı kararları — her şeyden önce |
| `KuralMotoru` + `kurallar.json` | Katman 1 — maliyetsiz etiketleme |
| `PromptBuilder` + `few-shot.json` | Katman 2 prompt'u (arama mimarisi bilgisi + örnekler) |
| `ClaudeClient` | Messages API, `temperature=0`, zorunlu şema, backoff |
| `RunSummarizer` | Koşu geneli örüntü analizi |
| `BulguAnalyzer` | Orkestrasyon + CLI |
| `ExcelReport` / `ReportWriter` | Raporu okuma ve sütun ekleme (POI) |
| `AnalysisCache` | İmza tabanlı önbellek |

## Ekip politikası — synonym'den kaçınmak

Kurallar bilerek **sorgu bazlı synonym önermez.** Bir case'i synonym ile yeşile
çevirmek motoru iyileştirmez, yalnızca başarı oranını yapay olarak yükseltir:
04.08 → 01.09 arasındaki %87 → %96 sıçramasının ~25/27'si bu tür yamalardı, gerçek
motor başarısı ≈ %86. Bu yüzden `K03` (tier normalizasyonu), `K05` (fuzzy/levenstein
ağırlığı) ve `K15` (char_filter/mapping) kurallarının `devNotu` alanları çözümü
mekanizmaya yönlendirir ve synonym'i açıkça dışlar. Yeni kural yazarken aynı çizgiyi
koruyun: `cozum` alanı **hangi ayar** değişecek onu söylesin, hangi kelime
eklenecek onu değil.

## Bilinen sınırlar

- **Kurallar niyet okuyamaz.** "Beklenti hatalı mı?" gibi sorular yargı gerektirir.
  Çözüm `bilinen-istisnalar.json` — bir kez karar verin, her koşuda geçerli olsun.
  İstisna listesine girmemiş böyle bir satır için kural mekanizmayı gösterir ama
  `YANLIS_BEKLENTI` diyemez.
- `PLAYLIST_15` tipi satırlarda ağırlıklı model sırayı üretemiyor — playlists
  index'inde `intentBoostEnabled=true` olduğu için loglanmayan ek bir boost var.
