package tests;

import client.ElasticsearchClient;
import client.MuudSearchApi;
import config.TestConfig;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import report.CacheBehaviorReportWriter;
import util.MuudSearchUtils;
import validation.CacheBehaviorResult;
import validation.CacheBehaviorResult.Verdict;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MUUD SEARCH API — CACHE DAVRANIŞ TESTİ (kara kutu, ES update tabanlı)
 *
 *   Salih'in önerisiyle: search-api'ye dokunmaz, sadece ES'de veri değiştirip
 *   API'nin ne döndürdüğüne bakar.
 *
 *   Akış (her case):
 *     1. Search yap, dokümanın takip edilebilecek alanını bul (numPlays,
 *        popularityScore, manualScoreBoost vs. — ilk dolu olanı seç)
 *     2. ES'de o alanı güncelle (örn 100 → 999100)
 *     3. Hemen tekrar search → cevap eski mi (HIT) yeni mi (DISABLED)
 *     4. TTL bekle (11 sn)
 *     5. Tekrar search → cevap yeni mi (TTL doğru) eski mi (TTL bozuk)
 *     6. CLEANUP: ES'de eski değere döndür (sadece update başarılıysa)
 */
public class MuudSearchCacheBehaviorTest extends TestConfig {

    private static final boolean EXCEL_REPORT_ENABLED =
            Boolean.parseBoolean(System.getProperty("excelReport", "false"));

    private static final String ES_URL =
            System.getProperty("esUrl", "http://127.0.0.1:9200");

    private static final long TTL_WAIT_MS =
            Long.parseLong(System.getProperty("cacheTtlMs", "33000"));

    /**
     * Takip edilebilecek aday alanlar — öncelik sırası ile.
     * Doc'da hangisi dolu olarak gelirse onu kullanırız.
     * Hepsi long mapping'inde, etkisi search'ten görülür, veri bütünlüğü için kritik değil.
     */
    private static final List<String> CANDIDATE_FIELDS = List.of(
            "numPlays",            // şarkılarda her zaman var
            "popularityScore",     // şarkılar + albümler
            "performerPopularity", // performer ve şarkılar
            "popularSongCount",    // performer
            "manualScoreBoost"     // manuel boost edilmişler (Sezen Aksu gibi)
    );

    private static final List<CacheBehaviorResult> RESULTS = new ArrayList<>();
    private static MuudSearchApi searchApi;
    private static ElasticsearchClient esClient;
    private static boolean esAvailable;

    @BeforeAll
    static void setup() {
        searchApi = new MuudSearchApi();
        esClient = new ElasticsearchClient(ES_URL);

        System.out.println("=== CACHE BEHAVIOR TEST ===");
        System.out.println("ES URL          : " + ES_URL);
        System.out.println("TTL bekleme süresi: " + TTL_WAIT_MS + "ms");
        System.out.println("Aday alanlar    : " + CANDIDATE_FIELDS);
        System.out.println();

        System.out.print("→ ES erişimi kontrol ediliyor... ");
        esAvailable = esClient.isAvailable();
        System.out.println(esAvailable ? "✓ erişilebilir" : "✗ erişilemiyor (tunnel kapalı?)");
        System.out.println();
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("CB_SONG_01",   "sezen",  "5",              "muud_song_flat_v2"),
                Arguments.of("CB_SONG_02",   "duman",  "5",              "muud_song_flat_v2"),
                Arguments.of("CB_ACTIVE_01", "tarkan", "active-indices", null),
                Arguments.of("CB_ACTIVE_02", "rihanna","active-indices", null)
        );
    }

    @DisplayName("Cache davranış doğrulama (ES update + verify)")
    @ParameterizedTest(name = "[{0}] term={1} index={2}")
    @MethodSource("cases")
    void cache_behavior(String caseId, String term, String indexId, String esIndexHint) {
        assumeTrue(esAvailable, "ES erişimi yok, test atlanıyor.");

        // === 1. Initial search ===
        Response r1 = searchApi.search(term, indexId, 10);
        if (r1.statusCode() != 200) {
            recordSkip(caseId, term, indexId, "Initial search HTTP " + r1.statusCode());
            assumeTrue(false, "Search başarısız");
            return;
        }

        DocInfo docInfo = extractFirstDocInfo(r1.jsonPath(), esIndexHint);
        if (docInfo == null) {
            recordSkip(caseId, term, indexId,
                    "Search sonucunda " + CANDIDATE_FIELDS + " alanlarından hiçbiri dolu doküman bulunamadı");
            assumeTrue(false, "Test için doküman yok");
            return;
        }

        System.out.printf("[%s] Doc: id=%s, esIndex=%s, alan=%s, mevcut değer=%s%n",
                caseId, docInfo.id, docInfo.esIndex, docInfo.field, docInfo.originalValue);

        Long originalLong = toLong(docInfo.originalValue);
        Long updatedLong = originalLong + 999_000L;
        Object originalValueForRecord = docInfo.originalValue;

        boolean updateApplied = false;
        Object afterUpdate = null;
        Object afterTtl = null;

        try {
            // === 2. ES update ===
            esClient.updateField(docInfo.esIndex, docInfo.id, docInfo.field, updatedLong);
            updateApplied = true;
            System.out.printf("[%s] ES update: %s = %s%n", caseId, docInfo.field, updatedLong);

            // === 3. Hemen search → HIT testi ===
            Thread.sleep(500);
            Response r2 = searchApi.search(term, indexId, 10);
            afterUpdate = findFieldForDoc(r2.jsonPath(), docInfo.id, docInfo.field);
            boolean hitDetected = equalsValue(afterUpdate, originalValueForRecord);
            System.out.printf("[%s] ES update sonrası search → %s = %s  → %s%n",
                    caseId, docInfo.field, afterUpdate,
                    hitDetected ? "HIT (cache eski koruyor) ✓" : "Cache eski korumadı");

            // === 4. TTL bekle ===
            System.out.printf("[%s] %dms bekleniyor (TTL aşımı için)...%n", caseId, TTL_WAIT_MS);
            Thread.sleep(TTL_WAIT_MS);

            // === 5. Tekrar search → TTL testi ===
            Response r3 = searchApi.search(term, indexId, 10);
            afterTtl = findFieldForDoc(r3.jsonPath(), docInfo.id, docInfo.field);
            boolean ttlDetected = equalsValue(afterTtl, updatedLong);
            System.out.printf("[%s] TTL sonrası search → %s = %s  → %s%n",
                    caseId, docInfo.field, afterTtl,
                    ttlDetected ? "TAZE (cache expire, ES'den geldi) ✓" : "Hala eski (TTL beklenmedik)");

            Verdict verdict;
            String note;
            if (hitDetected && ttlDetected) {
                verdict = Verdict.CACHE_WORKING;
                note = "Beklendiği gibi: cache eski veriyi tuttu, TTL sonrası taze veri geldi.";
            } else if (!hitDetected && ttlDetected) {
                verdict = Verdict.CACHE_DISABLED;
                note = "ES update sonrası ANINDA taze veri geldi. cacheEnabled=false gibi davranıyor.";
            } else if (hitDetected) {
                verdict = Verdict.TTL_NOT_EXPIRED;
                note = "Hit kanıtı var ama TTL aşıldığında hala eski veri geldi.";
            } else {
                verdict = Verdict.CACHE_PARTIAL;
                note = "Beklenmedik: hit yok ama TTL sonrası eski veri var.";
            }
            System.out.printf("[%s] → %s | %s%n%n", caseId, verdict, note);

            RESULTS.add(new CacheBehaviorResult(
                    caseId, term, indexId, docInfo.esIndex, docInfo.id, docInfo.field,
                    originalValueForRecord, updatedLong, afterUpdate, afterTtl,
                    hitDetected, ttlDetected, verdict, note));

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            recordError(caseId, term, indexId, docInfo, originalValueForRecord, "Test kesildi");
            fail("Test kesildi");
        } catch (Exception e) {
            recordError(caseId, term, indexId, docInfo, originalValueForRecord, e.getMessage());
            fail(caseId + ": " + e.getMessage());
        } finally {
            // === 6. CLEANUP — sadece update başarılı olduysa ===
            if (updateApplied) {
                try {
                    esClient.updateField(docInfo.esIndex, docInfo.id, docInfo.field, originalLong);
                    System.out.printf("[%s] Cleanup: %s = %s'a döndürüldü%n%n",
                            caseId, docInfo.field, originalLong);
                } catch (Exception cleanupEx) {
                    System.err.printf("[%s] ⚠ CLEANUP BAŞARISIZ — manuel rollback gerek! doc=%s, %s=%s%n",
                            caseId, docInfo.id, docInfo.field, originalLong);
                }
            }
        }
    }

    // =========================================================================
    // YARDIMCI
    // =========================================================================

    private static class DocInfo {
        final String id;
        final String esIndex;
        final String field;
        final Object originalValue;
        DocInfo(String id, String esIndex, String field, Object originalValue) {
            this.id = id;
            this.esIndex = esIndex;
            this.field = field;
            this.originalValue = originalValue;
        }
    }

    /**
     * Search response'undan ilk uygun dokümanı bulur:
     *   - id ve esIndex dolu olmalı
     *   - esIndexHint verilmişse o index'ten olmalı
     *   - CANDIDATE_FIELDS'tan en az birinde dolu değer olmalı
     */
    private DocInfo extractFirstDocInfo(JsonPath jp, String esIndexHint) {
        String basePath = MuudSearchUtils.getBasePath(jp);
        Integer size = jp.get(basePath + ".size()");
        if (size == null || size == 0) return null;

        for (int i = 0; i < size; i++) {
            String id = jp.getString(basePath + "[" + i + "].id");
            String esIdx = jp.getString(basePath + "[" + i + "].index");

            if (id == null || esIdx == null) continue;
            if (esIndexHint != null && !esIndexHint.equals(esIdx)) continue;

            // CANDIDATE_FIELDS'tan ilk dolu olanı bul
            for (String field : CANDIDATE_FIELDS) {
                Object val = jp.get(basePath + "[" + i + "].data." + field);
                if (val != null) {
                    return new DocInfo(id, esIdx, field, val);
                }
            }
        }
        return null;
    }

    /**
     * Belirli bir docId'ye sahip dokümanın belirli alanını search response'undan bulur.
     */
    private Object findFieldForDoc(JsonPath jp, String docId, String field) {
        String basePath = MuudSearchUtils.getBasePath(jp);
        Integer size = jp.get(basePath + ".size()");
        if (size == null || size == 0) return null;

        for (int i = 0; i < size; i++) {
            String id = jp.getString(basePath + "[" + i + "].id");
            if (docId.equals(id)) {
                return jp.get(basePath + "[" + i + "].data." + field);
            }
        }
        return null;
    }

    private static Long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }

    private static boolean equalsValue(Object actual, Object expected) {
        if (actual == null && expected == null) return true;
        if (actual == null || expected == null) return false;
        if (actual instanceof Number a && expected instanceof Number e) {
            return a.longValue() == e.longValue();
        }
        return actual.toString().equals(expected.toString());
    }

    private static void recordSkip(String caseId, String term, String indexId, String reason) {
        RESULTS.add(new CacheBehaviorResult(
                caseId, term, indexId, null, null, "(none)",
                null, null, null, null,
                false, false, Verdict.SKIPPED, reason));
    }

    private static void recordError(String caseId, String term, String indexId,
                                    DocInfo info, Object original, String error) {
        RESULTS.add(new CacheBehaviorResult(
                caseId, term, indexId,
                info != null ? info.esIndex : null,
                info != null ? info.id : null,
                info != null ? info.field : "(none)",
                original, null, null, null,
                false, false, Verdict.ERROR, error));
    }

    @AfterAll
    static void writeReport() {
        System.out.println();
        System.out.println("=== CACHE BEHAVIOR ÖZETİ ===");
        long working = RESULTS.stream().filter(r -> r.verdict() == Verdict.CACHE_WORKING).count();
        long disabled = RESULTS.stream().filter(r -> r.verdict() == Verdict.CACHE_DISABLED).count();
        long ttlBad = RESULTS.stream().filter(r -> r.verdict() == Verdict.TTL_NOT_EXPIRED).count();
        long partial = RESULTS.stream().filter(r -> r.verdict() == Verdict.CACHE_PARTIAL).count();
        long errors = RESULTS.stream().filter(r -> r.verdict() == Verdict.ERROR).count();
        long skipped = RESULTS.stream().filter(r -> r.verdict() == Verdict.SKIPPED).count();

        System.out.println("Toplam case: " + RESULTS.size());
        System.out.println("  CACHE_WORKING    : " + working);
        System.out.println("  CACHE_DISABLED   : " + disabled);
        System.out.println("  TTL_NOT_EXPIRED  : " + ttlBad);
        System.out.println("  CACHE_PARTIAL    : " + partial);
        System.out.println("  ERROR            : " + errors);
        System.out.println("  SKIPPED          : " + skipped);
        System.out.println();

        if (!EXCEL_REPORT_ENABLED) {
            System.out.println("ℹ Excel kapalı. Açmak için -DexcelReport=true ekle.");
            return;
        }
        if (RESULTS.isEmpty()) {
            System.out.println("ℹ Hiç sonuç yok.");
            return;
        }
        CacheBehaviorReportWriter.writeStandalone(RESULTS);
    }
}
