# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Proje Amacı

Muud (Türk Telekom) arama API'sini test eden, raporlayan ve analiz eden Java 17 / Maven projesi. Gerçek HTTP istekleri atar, JUnit 5 framework'ü kullanır fakat test metodları **asla fail etmez** — saf gözlem & Excel raporu üretir. İstisna: `PlaylistGroupTest` (deployment tespiti için assert yapar).

## Komutlar

```bash
# Tüm testleri çalıştır
mvn test

# Tek test sınıfı
mvn test -Dtest=AramaKalitesiTest
mvn test -Dtest=PlaylistGroupTest
mvn test -Dtest=YazimToleransiTest
mvn test -Dtest=PlaylistGroupDiscovery

# Prod defect bulguları (25 madde) — UAT'te yeniden üretme + analiz (reports/ddp-bulgu/)
mvn test -Dtest=DdpBulguTest

# Elasticsearch URL'i gerektirenler
mvn test -Dtest=DataCollectorTest -Des.url=http://127.0.0.1:9200

# Web UI başlat (Spring Boot, tarayıcı otomatik açılır)
mvn spring-boot:run
```

Çıktı raporları `reports/<alt-klasör>/TestReport_dd.MM.yyyy_HH.mm_ORTAM.xlsx` formatında üretilir.

## Ortam Değiştirme

Aktif ortam `src/test/java/client/MuudSearchApi.java` tarafından belirlenir. Bu dosya şu an **UAT** (mirketgateway) gösteriyor. Ortam değiştirmek için:

1. `ortam-dosyalari/` klasöründeki hedef dosyayı (`MuudSearchApi_PROD.java.txt`, `_UAT.java.txt` veya `_HMUUD.java.txt`) seç
2. İçeriğini `src/test/java/client/MuudSearchApi.java` üzerine tamamen kopyala
3. Başka hiçbir dosyaya dokunma — ortam etiketi (PROD/UAT) `ortam()` metodundan `BASE_URL`'e bakarak otomatik türetilir; rapor adı da bu etiketi kullanır

Tüm 3 ortam dosyasında `search(term, indexId, limit)` ve `search(term, indexId, limit, boolean correction)` overload'ları mevcuttur.

## Kod Mimarisi

### Katmanlar

```
client/          → HTTP istemcileri (MuudSearchApi, SpotifyApi, ElasticsearchClient)
config/          → TestConfig (tüm test sınıfları bunu extend eder; RestAssured loglama)
util/            → MuudSearchUtils (JSON path seçimi, isim normalizasyonu, kural eşleştirme)
domain/          → Rule, SearchCase veri modelleri
validation/      → ES mapping doğrulama mantığı
report/          → Excel yazıcılar (her test sınıfının özel bir writer'ı var)
analyzer/        → BulguAnalyzer — NOK satırlarını LLM+kural ile analiz eder
tests/arama/     → Kalite testleri (assert yok, rapor üretir)
tests/collector/ → Veri toplama / discovery (assert yok, keşif amaçlı)
tests/analiz/    → Derinlemesine analiz (gürültü, önbellek, semantik)
tests/mapping/   → ES index mapping doğrulaması
tests/ttcomtr/   → TT.com.tr arama karşılaştırması
```

### Kritik Tasarım Kararları

**JSON path dinamizmi:** Gateway bazen `topHits`, bazen `content` döner. `MuudSearchUtils.getBasePath(jp)` bunu soyutlar — yeni test yazarken direkt path yazmak yerine bu metodu kullan.

**Playlist adı uyumluluğu:** `MuudSearchUtils.getPlaylistName()` hem eski schema (`playlistName`) hem yeni schema (`data.itemName`) için fallback içerir. muud_playlist_all_v2 deployment'tan önce/sonra her ikisi de çalışır.

**correction=false:** PlaylistGroupTest ve PlaylistGroupDiscovery n-gram / playlistGroup aramaları için `correction=false` kullanır. Yazım toleransı testleri ise bu parametrenin davranışını test eder.

**PlaylistGroup deployment tespiti:** `PlaylistGroupTest` 3 katmanlı deployment dedektörüdür.
- TEK (`indices/4`): top-20 içinde `itemKind=playlistGroup` var mı? **Assert eder.** Ranking bağımlı.
- AKT (`active-indices`): Tümü sekmesinde grup çıkıyor mu? **Bilgi amaçlı, assert yok.**
- FIL (`indices/4` + `filters:[{field:"itemKind",value:"playlistGroup"}]`): **En net deployment sinyali.** Ranking bağımsız — filtre sayesinde doğrudan group sorgusu yapılır; eski schema'da (itemKind alanı yok) 0 sonuç döner. **Assert eder.**

PROD'da deployment öncesi TEK+FIL 0/30 OK, sonra 30/30 OK. `MuudSearchApi.searchFiltered()` bu filtreli aramayı sağlar.
`PlaylistGroupDiscovery` yeni keyword adayları bulmak için kullanılır; deployment sonrası PROD'da da çalıştırılması önerilir.

### BulguAnalyzer (opsiyonel LLM analizi)

NOK satırlarını üç katmanlı analiz eder:
1. `RunSignals` — ağırlıklı skor, sıra kayması gibi deterministik bulgular
2. `KuralMotoru` — `src/test/resources/analyzer/kurallar.json` ile sıfır maliyetli etiketleme
3. `ClaudeClient` — API key varsa sadece kural eşleşmeyen satırlar için LLM çağrısı

Yapılandırmak için `analyzer.properties.example`'ı proje köküne `analyzer.properties` olarak kopyala ve `analyzer.apiKey` gir. `AramaKalitesiTest` ve `SemanticSearchTest` bitişinde otomatik çağrılır.

### Web UI

`mvn spring-boot:run` ile başlar, `localhost:8090`'da açılır. `src/test/resources/cases.xlsx`'i okur, test çalıştırır, rapor döner. `web/CaseController` REST endpoint'lerini, `web/WebApp` Spring Boot başlangıcını yönetir.
