# MUUD Search Test Otomasyonu — BulguAnalyzer Yeni Sohbet Briefing'i

## Sen Kimsin / Proje Nedir?

Ben Barış Durak, Türk Telekom'da Software Test & Quality Engineer olarak çalışıyorum.
Elimde Java tabanlı bir test otomasyon projesi var: **MuudSearchTest**.
Bu proje, MUUD müzik platformunun arama motorunu (search API'sini) test ediyor.

---

## Teknik Stack

- **Dil:** Java 17
- **Build:** Maven
- **Test Framework:** JUnit 5 (Jupiter) — `@ParameterizedTest`, `@MethodSource`, `@BeforeAll`, `@AfterAll`
- **HTTP Client:** Rest Assured
- **Raporlama:** Apache POI (Excel / .xlsx)
- **Proje dizini:** `src/test/java/`

---

## API Bilgileri

### İç Gateway (Test/UAT)
- **URL:** `https://mirketgateway.apps.erdek.paas.turktelekom.intra/gateway/search/rest/v10/active-indices/search`
- **Method:** POST
- **Auth:** `Basic V1BQZUhMeWc6NUt5Y09ESlp4aFFxQXZtNQ==`
- **X-SEARCH-APP-KEY:** `muudelk9`
- **Body:**
```json
{
  "text": "<sorgu>",
  "filters": null,
  "fields": null,
  "suggestion": true,
  "correction": true,
  "limit": 10,
  "offset": 0
}
```

### Production API
- **URL:** `https://searchapi.turktelekom.com.tr/gateway/search/rest/v10/active-indices/search`
- **Auth:** `Basic QkRHVTc5a206ckpnNzBZaTd6VGxreVF2UQ==`
- **X-SEARCH-APP-KEY:** `muud`

### Response Yapısı
```json
{
  "topHits": [
    {
      "id": "12345",
      "index": "muud_artist_flat_v1",
      "data": {
        "performerName": "Tarkan",
        "trackName": "Şımarık",
        "name": "Türkçe Pop"
      }
    }
  ]
}
```
- `topHits` → genel aramada
- `content` → bazı index'lerde

---

## Proje Yapısı

```
src/test/java/
├── client/
│   ├── MuudSearchApi.java          ← REST client (correction/suggestion parametre alır)
│   └── TtComtrSearchApi.java       ← TT.com.tr search client
├── config/
│   └── TestConfig.java
├── report/
│   ├── ExcelTestReportWriter.java  ← Ana Excel rapor yazıcı (writeBulgu + subDir desteği var)
│   ├── TestResultRow.java          ← Test sonuç satırı modeli
│   └── TtComtrUrlReportWriter.java
├── util/
│   └── MuudSearchUtils.java        ← findArtistIndex, findArtistAndTrackIndex, containsTRInsensitive
└── tests/
    ├── MainCases.java              → reports/main-cases/
    ├── YazimTolansiCases.java      → reports/yazim-tolansi/
    ├── MuudTypoTest.java           → reports/muud-typo/
    ├── SemanticSearchTest.java     → reports/semantic/
    ├── Bulgu_UAT_OrtakExcel.java   → reports/bulgu-uat/
    └── ttcomtr/
        ├── TtComtrSearchTest.java  → reports/ttcomtr/
        └── TtComtrDetailUrlTest.java
```

---

## TestResultRow Yapısı (8 alan)

```java
new TestResultRow(
    "UAT_001",                        // testId
    "\"pop\" araması yapılır",        // adim (test adımı)
    "İlk 3 sonuçta Tarkan çıkmalı",  // beklenen
    "Beklenen",                       // referans
    "MUUD Arama",                     // modul
    "active-indices",                 // indexId
    "OK",                             // sonuc: "OK" veya "NOK"
    "1. sırada bulundu: Tarkan"       // detay (API yanıtı özeti)
)
```

---

## ExcelTestReportWriter.writeBulgu — Overload'lar

```java
// Eski (proje köküne kaydeder — artık kullanılmıyor)
ExcelTestReportWriter.writeBulgu(ROWS);

// Yeni (alt klasöre kaydeder — tüm test class'larında bu var)
ExcelTestReportWriter.writeBulgu(ROWS, "reports/main-cases");
```

---

## MuudSearchUtils — Kritik Metodlar

```java
// Türkçe karakter duyarsız içerik araması (ı→i, İ→i, Ş→ş vb.)
MuudSearchUtils.containsTRInsensitive(String text, String keyword)

// topHits içinde sanatçı sırasını bulur
MuudSearchUtils.findArtistIndex(JsonPath jp, int expectedRank, String artistName)

// topHits içinde sanatçı+şarkı kombinasyonu sırasını bulur
MuudSearchUtils.findArtistAndTrackIndex(JsonPath jp, int expectedRank, String artist, String track)

// basePath: topHits mi content mi — response'a göre otomatik seçer
MuudSearchUtils.getBasePath(JsonPath jp)
```

---

## Var Olan Test Class'ları — Ne Test Ediyorlar?

### MainCases.java
Ana arama senaryoları. Sanatçı, şarkı, playlist aramaları. `kind` alanına göre değerlendirme:
- `artist` → `findArtistIndex`
- `song` → `findArtistAndTrackIndex`
- `artist_or_playlist` → performer veya playlist name kontrolü

### YazimTolansiCases.java
Türkçe karakter yazım toleransı (ş→s, ğ→g, ü→u vb.). Yanlış yazılmış sorgular doğru sonucu döndürüyor mu?

### MuudTypoTest.java
110 case. `typo/queries.txt` + `typo/expected_results.txt` kaynaklı.
Format: `no|query|test_type|logic_category` ve `no|kind|rank|field1|field2`

### TtComtrSearchTest.java
Türk Telekom web sitesi (turktelekom.com.tr) arama motoru UAT testi. 214 case.
Kaynak: `ttcomtr/uat_search_cases.csv` (no|query|keyword|rank)
Değerlendirme: `title` veya `url` içinde keyword var mı?

---

## Şu An Yapmak İstediğimiz Yeni Şey: BulguAnalyzer

### Arka Plan
Daha önce Excel raporuna LLM yorumu ekliyorduk (çözüm önerisi, geliştirici notu).
Sonra kaldırdık çünkü hatalar oluyordu / tutarsız sonuçlar veriyordu.

### Yeni Vizyon
Test koştuktan sonra NOK olan satırları LLM'e analiz ettirmek istiyoruz.
LLM şunu yapacak:
1. **Gerçek bulgu mu?** (true/false) — Beklenti hatalı mı yoksa gerçekten search motoru hatası mı?
2. **Kategori ne?** — Aşağıdaki kategorilerden biri:
   - `RANKING` — Sonuç var ama yanlış sırada
   - `INDEX_MISSING` — Sorgu sonuç döndürmüyor
   - `SINONIM` — Yazım/sinonim kuralı eksik
   - `AGIRLIK` — Relevance weight ayarı gerekiyor
   - `YENI_GELISTIRME` — Yeni özellik gerekiyor
   - `YANLIS_BEKLENTI` — Test case'in expected'ı hatalı
3. **Çözüm önerisi** — 1-2 cümle
4. **Geliştirici notu** — Teknik aksiyon önerisi

### Çalışma Prensibi
- Test bitince `@AfterAll`'da NOK satırlar filtrelenir
- Her NOK satır için LLM API'ye istek gönderilir
- Sonuç Excel'e ekstra sütunlar olarak yazılır
- İnsan onayı olmadan çalışabilmeli (autonomous)

### LLM Eğitme Yaklaşımı (Seviye 1 → 2)
**Şu an:** Few-shot prompting — prompt içinde 5-10 örnek karar veriyoruz
**Sonra:** RAG — geçmiş kararları JSON'a kaydedip benzer case'leri context olarak ekliyoruz
**İleride:** Fine-tuning — yeterli etiketli veri biriktikten sonra

### Beklenen Çıktı — Java Sınıfı
`src/test/java/analyzer/BulguAnalyzer.java` — şunu yapacak:

```java
// Her NOK satır için:
BulguAnalysis analysis = BulguAnalyzer.analyze(row);
// analysis.isBulgu()       → true/false
// analysis.getKategori()   → "RANKING", "INDEX_MISSING" vb.
// analysis.getCozum()      → "Ağırlık ayarı öneriliyor..."
// analysis.getDevNotu()    → "Elasticsearch boost değeri kontrol edilmeli"
```

Excel'e bu 4 alan ekstra sütun olarak eklenecek.

### LLM API Seçeneği
- **Claude API** (Anthropic) — önerilen, Haiku ile ucuz
- **OpenAI API** — alternatif
- Java'da `HttpClient` veya `OkHttp` ile çağrılacak

---

## Eski Excel Formatından Örnek Sütunlar

| Test ID | Adım | Beklenen | Referans | Modül | Index | Sonuç | Detay |
|---------|------|----------|----------|-------|-------|-------|-------|
| UAT_001 | "pop" araması yapılır | İlk 3'te playlist çıkmalı | Keyword: pop | TT Comtr | smartsearch | NOK | Top-10'da bulunamadı |

**Eklenecek sütunlar:**
| Gerçek Bulgu? | Kategori | Çözüm Önerisi | Geliştirici Notu |
|---------------|----------|---------------|-----------------|
| EVET | RANKING | "Pop playlist'leri daha yüksek boost'lanmalı" | "muud_playlist_flat_v1 index'inde weight artır" |

---

## Önemli Notlar

1. **Türkçe karakter hassasiyeti:** ı/i, ş/s, ğ/g vb. ayrımı kritik. `containsTRInsensitive` metodu `ROOT` ve `TR` locale'ini dener.
2. **Test asla fail etmez:** Tüm sonuçlar Excel'e yazılır, JUnit assertion'ı yok. NOK = bilgi, test durdurmaz.
3. **LLM yanlış karar verirse:** Log'a yazılır, insan sonradan gözden geçirir. Otonom mod için threshold gerekebilir (confidence score).
4. **API key güvenliği:** LLM API key'i environment variable veya `config.properties`'den okunacak, koda yazılmayacak.

---

## İstenen İlk Çıktı

1. `BulguAnalyzer.java` — LLM API'ye istek atan, yanıtı parse eden sınıf
2. `BulguAnalysis.java` — Sonuç modeli (record veya POJO)
3. `ExcelTestReportWriter.java` — `writeBulgu` metoduna 4 yeni sütun eklenmesi
4. Örnek prompt template (few-shot örneklerle)

