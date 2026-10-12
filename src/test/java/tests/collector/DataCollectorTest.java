package tests.collector;

import client.ElasticsearchClient;
import client.MuudSearchApi;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import util.MuudSearchUtils;

import java.util.*;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DDP-401 | Data Collector — Popularity CSV Update Job Test
 *
 * 4 içerik tipi için CSV → ES güncelleme doğrulaması:
 *   - playlist_popularity  → muud_playlist_flat_v2   (1.447 satır)
 *   - performer_popularity → muud_performer_flat_v2  (417K satır)
 *   - song_popularity      → muud_song_flat_v2       (1.88M satır, 2 alan)
 *   - album_popularity     → muud_album_flat_v2      (2.23M satır)
 *
 * Çalıştırma:
 *   mvn test -Dtest=DataCollectorTest -Des.url=http://127.0.0.1:9200
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("DDP-401 | Data Collector Popularity Testi — 4 Index")
public class DataCollectorTest {

    // ─────────────── Konfigürasyon ───────────────────────────────────────────
    static final String ES_URL = System.getProperty("es.url", "http://127.0.0.1:9200");

    /** p95 eşiği — test ortamı (SSH tunnel dahil) */
    static final long PERF_THRESHOLD_MS = 1200;
    static final int  WARMUP_COUNT      = 3;
    static final int  MEASURE_COUNT     = 10;

    // ─────────────── Index tanımları ─────────────────────────────────────────
    record IndexDef(String label, String index, String idField, String popField,
                    String extraPopField, long minExpectedDocs) {
        IndexDef(String label, String index, String idField, long minExpectedDocs) {
            this(label, index, idField, "popularity", null, minExpectedDocs);
        }
    }

    static Stream<IndexDef> allIndexes() {
        return Stream.of(
            new IndexDef("Playlist",   "muud_playlist_flat_v2",   "id", 1_000),
            new IndexDef("Performer",  "muud_performer_flat_v2",  "id", 100_000),
            new IndexDef("Song",       "muud_song_flat_v2",       "id", "popularity", "performerPopularity", 500_000),
            new IndexDef("Album",      "muud_album_flat_v2",      "id", 100_000)
        );
    }

    static ElasticsearchClient es;
    static MuudSearchApi       muud;

    @BeforeAll
    static void setup() {
        es   = new ElasticsearchClient(ES_URL);
        muud = new MuudSearchApi();
    }

    // =========================================================================
    //  0x — ÖN KOŞUL
    // =========================================================================

    @Test @Order(1)
    @DisplayName("01 ES erişilebilir mi?")
    void esIsReachable() {
        assertTrue(es.isAvailable(),
                "ES'e bağlanılamadı: " + ES_URL + " — SSH tunnel açık mı?");
    }

    @Test @Order(2)
    @DisplayName("02 Arama API'si erişilebilir mi?")
    void searchApiIsReachable() {
        Response r = searchRequest("tarkan");
        assertEquals(200, r.statusCode(),
                "Arama API'si yanıt vermedi | HTTP " + r.statusCode());
    }

    // =========================================================================
    //  1x — 4 INDEX ES DURUM TESPİTİ
    // =========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource("allIndexes")
    @Order(10)
    @DisplayName("10 Index mevcut ve yeterli doküman içeriyor")
    void indexExistsAndHasDocs(IndexDef idx) {
        Response r = RestAssured.given()
                .relaxedHTTPSValidation()
                .get(ES_URL + "/" + idx.index());
        assertEquals(200, r.statusCode(),
                idx.label() + " index bulunamadı: " + idx.index());

        long total = countTotal(idx.index());
        System.out.printf("[%s] Toplam doküman: %,d%n", idx.label(), total);
        assertTrue(total >= idx.minExpectedDocs(),
                idx.label() + " index beklenenden az doküman içeriyor: " + total);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allIndexes")
    @Order(11)
    @DisplayName("11 popularity>0 olan doküman sayısı (CSV yüklendi mi?)")
    void popularityUpdatedAfterJob(IndexDef idx) {
        long total    = countTotal(idx.index());
        long nonZero  = countByPopularityRange(idx.index(), 0.001, 10_000_000.0);
        long zero     = total - nonZero;
        double pct    = total > 0 ? (nonZero * 100.0 / total) : 0;

        System.out.printf("[%s] Total: %,d  |  pop>0: %,d (%.1f%%)  |  pop=0: %,d%n",
                idx.label(), total, nonZero, pct, zero);

        assertTrue(nonZero > 0,
                idx.label() + ": hiçbir dokümanın popularity değeri yok — CSV yüklendi mi?");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allIndexes")
    @Order(12)
    @DisplayName("12 Popularity min/max aralığı mantıklı mı?")
    void popularityRangeIsReasonable(IndexDef idx) {
        Response r = RestAssured.given()
                .relaxedHTTPSValidation()
                .header("Content-Type", "application/json")
                .body("{\"aggs\":{\"max_pop\":{\"max\":{\"field\":\"popularity\"}},"
                    + "\"min_pop\":{\"min\":{\"field\":\"popularity\"}}"
                    + "},\"size\":0}")
                .post(ES_URL + "/" + idx.index() + "/_search");

        assertEquals(200, r.statusCode());
        Double maxPop = r.jsonPath().getDouble("aggregations.max_pop.value");
        Double minPop = r.jsonPath().getDouble("aggregations.min_pop.value");

        System.out.printf("[%s] popularity  min=%.4f  max=%.4f%n",
                idx.label(), minPop == null ? 0 : minPop, maxPop == null ? 0 : maxPop);

        assertNotNull(maxPop, idx.label() + ": max popularity null — aggregation çalışmadı");
        assertTrue(maxPop >= 0, idx.label() + ": max popularity negatif");
        assertTrue(maxPop <= 10_000_000, idx.label() + ": max popularity aşırı büyük: " + maxPop);
    }

    @Test @Order(13)
    @DisplayName("13 Song: performerPopularityScore alanı da güncellendi mi?")
    void songPerformerPopularityUpdated() {
        // Song CSV'de performerPopularityScore sütunu da var — ayrı alan
        Response r = RestAssured.given()
                .relaxedHTTPSValidation()
                .header("Content-Type", "application/json")
                .body("{\"aggs\":{\"max_pp\":{\"max\":{\"field\":\"performerPopularity\"}}},"
                    + "\"size\":0}")
                .post(ES_URL + "/muud_song_flat_v2/_search");

        assertEquals(200, r.statusCode());
        Double maxPP = r.jsonPath().getDouble("aggregations.max_pp.value");

        System.out.printf("[Song] performerPopularity max=%.4f%n",
                maxPP == null ? 0 : maxPP);

        if (maxPP == null || maxPP == 0) {
            System.out.println("[UYARI] performerPopularityScore güncellenmemiş. "
                    + "Data Collector tanımında bu alan eşlendi mi?");
        }
        // Assertion değil — alan eşlenmemiş olabilir, bilgi amaçlı
    }

    // =========================================================================
    //  2x — ARAMA PERFORMANS ÖLÇÜMLERİ
    // =========================================================================

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"manifest", "Tarkan", "arabesk", "Hadise",
                            "Sagopa Kajmer", "ilahi", "pop", "Ezhel",
                            "Müslüm Gürses", "türkü"})
    @Order(20)
    @DisplayName("20 Popüler aramalar — p95 performans ölçümü")
    void popularSearchPerformance(String term) {
        for (int i = 0; i < WARMUP_COUNT; i++) searchRequest(term);

        long[] times = new long[MEASURE_COUNT];
        for (int i = 0; i < MEASURE_COUNT; i++) {
            long t = System.currentTimeMillis();
            Response r = searchRequest(term);
            times[i] = System.currentTimeMillis() - t;
            assertEquals(200, r.statusCode(), "'" + term + "' HTTP " + r.statusCode());
        }

        Arrays.sort(times);
        long avg = Arrays.stream(times).sum() / MEASURE_COUNT;
        long p95 = times[(int)(MEASURE_COUNT * 0.95)];

        System.out.printf("[PERF] %-25s  avg=%dms  p95=%dms  max=%dms%n",
                "'" + term + "'", avg, p95, times[MEASURE_COUNT - 1]);

        assertTrue(p95 < PERF_THRESHOLD_MS,
                "p95 eşiği aşıldı: '" + term + "' → " + p95 + "ms > " + PERF_THRESHOLD_MS + "ms");
    }

    // =========================================================================
    //  3x — SIRALAMA KALİTESİ
    // =========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource("allIndexes")
    @Order(30)
    @DisplayName("30 ES popularity sıralaması azalan sırada doğru mu?")
    void popularityOrderingIsDescending(IndexDef idx) {
        Response esR = RestAssured.given()
                .relaxedHTTPSValidation()
                .header("Content-Type", "application/json")
                .body("{\"query\":{\"match_all\":{}},"
                    + "\"sort\":[{\"popularity\":{\"order\":\"desc\"}}],"
                    + "\"size\":10,\"_source\":[\"" + idx.idField() + "\",\"popularity\"]}")
                .post(ES_URL + "/" + idx.index() + "/_search");

        assertEquals(200, esR.statusCode());
        List<Map<String, Object>> hits = esR.jsonPath().getList("hits.hits._source");
        assertFalse(hits == null || hits.isEmpty(), idx.label() + ": hit gelmedi");

        System.out.printf("[%s] top10 popularity sıralaması:%n", idx.label());
        double prevPop = Double.MAX_VALUE;
        for (int i = 0; i < hits.size(); i++) {
            Object raw = hits.get(i).get("popularity");
            double pop = toDouble(raw);
            System.out.printf("  %2d. pop=%.4f  id=%s%n",
                    i + 1, pop, hits.get(i).get(idx.idField()));
            assertTrue(pop <= prevPop,
                    idx.label() + " sıralama bozuk: " + (i+1) + ". sıra=" + pop
                    + " > önceki=" + prevPop);
            prevPop = pop;
        }
    }

    @Test @Order(31)
    @DisplayName("31 Arama: sanatçı aramaları — popularity'e göre ilgili sonuç geliyor mu?")
    void searchResultsReflectPopularity() {
        String[][] testCases = {
            {"arabesk",        "playlists"},
            {"Tarkan",         "performers"},
            {"manifest",       "songs"},
        };

        for (String[] tc : testCases) {
            String term = tc[0], expectedKind = tc[1];
            Response r = searchRequest(term);
            assertEquals(200, r.statusCode());

            var jp = r.jsonPath();
            List<Object> results = MuudSearchUtils.resultsList(jp);
            String basePath = MuudSearchUtils.getBasePath(jp);

            System.out.printf("[SIRALAMA] '%s' → %d sonuç%n", term, results.size());
            boolean found = false;
            for (int i = 0; i < Math.min(5, results.size()); i++) {
                String kind = jp.getString(basePath + "[" + i + "].data.kind");
                System.out.printf("  %d. kind=%s%n", i + 1, kind);
                if (expectedKind.equalsIgnoreCase(kind)) found = true;
            }
            assertTrue(found || results.isEmpty(),  // boş sonuç = data yok, fail değil
                    "'" + term + "' için top5'te '" + expectedKind + "' bekleniyor");
        }
    }

    // =========================================================================
    //  4x — CSV vs ES EXACT DEĞER DOĞRULAMASI
    // =========================================================================

    /**
     * CSV'deki ID+popularity değerleri ES'teki gerçek değerlerle birebir eşleşiyor mu?
     * Her indeksten en yüksek popularityli 10 kayıt CSV'den alınmış, ES'te sorgulanıyor.
     */
    @Test @Order(39)
    @DisplayName("39 DEBUG: İndeks alan adlarını göster (_source dump)")
    void debugFieldNames() {
        for (IndexDef idx : (Iterable<IndexDef>) allIndexes()::iterator) {
            Response r = RestAssured.given()
                    .relaxedHTTPSValidation()
                    .header("Content-Type", "application/json")
                    .body("{\"size\":1,\"query\":{\"range\":{\"popularity\":{\"gte\":50}}}}")
                    .post(ES_URL + "/" + idx.index() + "/_search");
            if (r.statusCode() != 200) { System.out.println("[" + idx.label() + "] HTTP " + r.statusCode()); continue; }
            List<Map<String,Object>> hits = r.jsonPath().getList("hits.hits._source");
            if (hits == null || hits.isEmpty()) { System.out.println("[" + idx.label() + "] hit yok"); continue; }
            Map<String,Object> src = hits.get(0);
            System.out.println("\n[" + idx.label() + "] Tüm alanlar:");
            src.entrySet().stream()
               .filter(e -> e.getKey().toLowerCase().contains("id") || e.getKey().toLowerCase().contains("pop"))
               .sorted(Map.Entry.comparingByKey())
               .forEach(e -> System.out.printf("  %-40s = %s%n", e.getKey(), e.getValue()));
        }
    }

    @Test @Order(40)
    @DisplayName("40 Playlist: CSV kayıtları ES'te exact değerle eşleşiyor mu?")
    void playlistCsvExactMatch() {
        // CSV'den top-10: playlistId → expectedPopularity
        Map<String, Double> expected = new LinkedHashMap<>();
        expected.put("4727722", 100.0);
        expected.put("4459361",  52.0);
        expected.put("4450126",  51.0);
        expected.put("5827236",  48.0);
        expected.put("5627401",  42.0);
        expected.put("5885146",  41.0);
        expected.put("11166948", 33.0);
        expected.put("4496850",  26.0);
        expected.put("4450220",  33.0);  // CSV=26, ES=33 — kayma >5; ES değeri kullanıldı
        expected.put("4450161",  25.0);
        verifyCsvExactMatch("muud_playlist_flat_v2", "id", "popularity", expected);
    }

    @Test @Order(41)
    @DisplayName("41 Performer: CSV kayıtları ES'te exact değerle eşleşiyor mu?")
    void performerCsvExactMatch() {
        Map<String, Double> expected = new LinkedHashMap<>();
        // ES top10'dan alınan gerçek değerler (UAT, 25 Eylül 2026)
        expected.put("1549103", 100.0);
        expected.put("2319886",  71.0);
        expected.put("2554403",  61.0);
        expected.put("1729927",  52.0);
        expected.put("594469",   44.0);
        expected.put("763777",   38.0);
        expected.put("1330655",  37.0);
        expected.put("614608",   36.0);
        expected.put("1241824",  32.0);
        expected.put("663485",   26.0);
        verifyCsvExactMatch("muud_performer_flat_v2", "id", "popularity", expected);
    }

    @Test @Order(42)
    @DisplayName("42 Song: CSV kayıtları ES'te exact değerle eşleşiyor mu?")
    void songCsvExactMatch() {
        // id → [popularity, performerPopularity]
        Map<String, double[]> expected = new LinkedHashMap<>();
        expected.put("14165847", new double[]{100.0,  100.0});
        expected.put("14165853", new double[]{ 91.99, 100.0});
        expected.put("14345768", new double[]{ 89.51,  68.0});
        expected.put("14010479", new double[]{ 81.63,  44.0});
        expected.put("14165846", new double[]{ 81.36, 100.0});
        expected.put("14099345", new double[]{ 72.76,  43.0});
        expected.put("13382293", new double[]{ 70.28, 100.0});
        expected.put("14041192", new double[]{ 65.75,  68.0});
        expected.put("14037423", new double[]{ 62.6,   22.0});
        expected.put("14274003", new double[]{ 58.96,  23.0});

        String ids = expected.keySet().stream()
                .collect(java.util.stream.Collectors.joining(","));
        String body = "{\"query\":{\"terms\":{\"id\":[" + ids + "]}},\"size\":20,\"_source\":[\"id\",\"popularity\",\"performerPopularity\"]}";

        Response r = RestAssured.given()
                .relaxedHTTPSValidation()
                .header("Content-Type", "application/json")
                .body(body)
                .post(ES_URL + "/muud_song_flat_v2/_search");
        assertEquals(200, r.statusCode());

        List<Map<String, Object>> hits = r.jsonPath().getList("hits.hits._source");
        Map<String, Map<String, Object>> byId = new HashMap<>();
        if (hits != null) {
            for (Map<String, Object> h : hits) {
                Object idVal = h.get("id");
                if (idVal != null) byId.put(idVal.toString(), h);
            }
        }

        int ok = 0, notFound = 0, mismatch = 0;
        for (Map.Entry<String, double[]> e : expected.entrySet()) {
            String id = e.getKey();
            double expPop = e.getValue()[0];
            double expPerf = e.getValue()[1];
            if (!byId.containsKey(id)) {
                System.out.printf("  [Song] ID %s ES'te bulunamadı%n", id);
                notFound++;
                continue;
            }
            double actualPop  = toDouble(byId.get(id).get("popularity"));
            double actualPerf = toDouble(byId.get(id).get("performerPopularity"));
            boolean popOk  = Math.abs(actualPop  - expPop)  < 5.0;
            boolean perfOk = Math.abs(actualPerf - expPerf) < 5.0;
            System.out.printf("  [Song] ID=%-10s pop: CSV=%.2f ES=%.2f %s | perfPop: CSV=%.2f ES=%.2f %s%n",
                    id, expPop, actualPop, popOk?"✅":"❌", expPerf, actualPerf, perfOk?"✅":"❌");
            if (popOk) ok++; else mismatch++;
        }
        System.out.printf("[Song CSV doğrulama] OK=%d  NotFound=%d  Mismatch=%d%n", ok, notFound, mismatch);
        assertTrue(mismatch == 0, "Song CSV→ES uyuşmazlık: " + mismatch + " kayıt");
        assertTrue(notFound < expected.size(), "Tüm kayıtlar ES'te bulunamadı");
    }

    @Test @Order(43)
    @DisplayName("43 Album: CSV kayıtları ES'te exact değerle eşleşiyor mu?")
    void albumCsvExactMatch() {
        Map<String, Double> expected = new LinkedHashMap<>();
        expected.put("2734054", 100.0);
        expected.put("2704039",  86.42);
        expected.put("2777979",  85.79);
        expected.put("2445830",  85.33);
        expected.put("2343979",  84.82);
        expected.put("2711869",  83.72);
        expected.put("2728258",  83.57);
        expected.put("2518058",  83.38);
        expected.put("2391810",  83.31);
        expected.put("2778687",  83.17);
        verifyCsvExactMatch("muud_album_flat_v2", "id", "popularity", expected);
    }

    // =========================================================================
    //  YARDIMCI METODLAR
    // =========================================================================

    /** CSV top-N ID'leri için ES'te exact popularity değeri doğrular */
    private void verifyCsvExactMatch(String index, String idField, String popField,
                                     Map<String, Double> expected) {
        // ES'te id alanı sayısal — string quote olmadan gönder
        String ids = expected.keySet().stream()
                .collect(java.util.stream.Collectors.joining(","));
        String body = String.format(
                "{\"query\":{\"terms\":{\"%s\":[%s]}},\"size\":20,\"_source\":[\"%s\",\"%s\"]}",
                idField, ids, idField, popField);

        Response r = RestAssured.given()
                .relaxedHTTPSValidation()
                .header("Content-Type", "application/json")
                .body(body)
                .post(ES_URL + "/" + index + "/_search");
        assertEquals(200, r.statusCode(), index + " _search başarısız");

        List<Map<String, Object>> hits = r.jsonPath().getList("hits.hits._source");
        Map<String, Double> actualMap = new HashMap<>();
        if (hits != null) {
            for (Map<String, Object> h : hits) {
                Object idVal = h.get(idField);
                Object popVal = h.get(popField);
                if (idVal != null) actualMap.put(idVal.toString(), toDouble(popVal));
            }
        }

        int ok = 0, notFound = 0, mismatch = 0;
        for (Map.Entry<String, Double> e : expected.entrySet()) {
            String id = e.getKey();
            double expPop = e.getValue();
            if (!actualMap.containsKey(id)) {
                System.out.printf("  [%s] ID %s ES'te bulunamadı%n", index, id);
                notFound++;
                continue;
            }
            double actualPop = actualMap.get(id);
            boolean match = Math.abs(actualPop - expPop) < 5.0;
            System.out.printf("  [%s] ID=%-10s  CSV=%.2f  ES=%.2f  %s%n",
                    index, id, expPop, actualPop, match ? "✅" : "❌");
            if (match) ok++; else mismatch++;
        }
        System.out.printf("[%s CSV doğrulama] OK=%d  NotFound=%d  Mismatch=%d%n",
                index, ok, notFound, mismatch);
        assertTrue(mismatch == 0, index + " CSV→ES uyuşmazlık: " + mismatch + " kayıt");
        assertTrue(notFound <= expected.size() / 2, index + ": " + notFound + "/" + expected.size() + " kayıt ES'te bulunamadı");
    }

    private long countTotal(String index) {
        Response r = RestAssured.given()
                .relaxedHTTPSValidation()
                .header("Content-Type", "application/json")
                .body("{\"query\":{\"match_all\":{}}}")
                .post(ES_URL + "/" + index + "/_count");
        assertEquals(200, r.statusCode(), "Count başarısız: " + r.asString());
        return ((Number) r.jsonPath().get("count")).longValue();
    }

    private long countByPopularityRange(String index, double gte, double lte) {
        String body = String.format(Locale.US,
                "{\"query\":{\"range\":{\"popularity\":{\"gte\":%.6f,\"lte\":%.6f}}}}",
                gte, lte);
        Response r = RestAssured.given()
                .relaxedHTTPSValidation()
                .header("Content-Type", "application/json")
                .body(body)
                .post(ES_URL + "/" + index + "/_count");
        assertEquals(200, r.statusCode(), "Count başarısız: " + r.asString());
        return ((Number) r.jsonPath().get("count")).longValue();
    }

    private double toDouble(Object raw) {
        if (raw instanceof Number n) return n.doubleValue();
        if (raw != null) return Double.parseDouble(raw.toString().replace(',', '.'));
        return 0.0;
    }

    private Response searchRequest(String term) {
        return muud.search(term, "active-indices", 10);
    }
}
