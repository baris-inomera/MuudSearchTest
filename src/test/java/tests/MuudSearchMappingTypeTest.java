package tests;

import client.MuudSearchApi;
import config.TestConfig;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import report.TypeValidationReportWriter;
import validation.CaseResult;
import validation.MappingTypeValidator;
import validation.TypeMismatch;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MAPPING TİP DOĞRULAMA TESTİ
 *
 *   Amaç: Search API'den dönen response içinde, ES mapping'de tanımlı
 *   alanların gerçek değerlerinin doğru tipte olup olmadığını kontrol eder.
 *
 *   Çalıştırma:
 *     mvn test -Dtest=MuudSearchMappingTypeTest -DexcelReport=true
 *
 *   IntelliJ'de:
 *     Run Configuration → "VM options" → -DexcelReport=true
 *
 *   Mapping kaynağı:
 *     - Varsayılan: src/test/resources/muud_song_flat_v2.mapping.json
 *     - ES URL ile override: -DesUrl=http://127.0.0.1:9200 -DesIndex=muud_song_flat_v2
 *
 *   Excel raporu (iki sheet):
 *     - "Case Summary" — her test case bir satır, ekibe iletilebilir özet
 *     - "All Details"  — tüm alan-bazlı uyuşmazlıkların detayı
 *
 *   ORTAM TOLERANSI:
 *     - Gateway 400 + INDEX-ERR-010 dönerse → SKIP
 *     - Gateway 200 ama veri boş gelirse → SKIP
 *     - Gateway 200 + veri varsa → tip doğrulaması yapılır; FAIL varsa düşer
 *
 *   GÜNCEL INDEX ID'leri (Mayıs 2026):
 *     2 → Albums (muud_album_flat_v2)
 *     3 → Performers (muud_performer_flat_v2)
 *     4 → Playlists (muud_playlist_flat_v2)
 *     5 → Songs (muud_song_flat_v2)
 *     6 → Videos (muud_video_flat_v2)
 */
public class MuudSearchMappingTypeTest extends TestConfig {

    private static final boolean EXCEL_REPORT_ENABLED =
            Boolean.parseBoolean(System.getProperty("excelReport", "false"));

    private static final String DEFAULT_MAPPING_PATH =
            "src/test/resources/muud_song_flat_v2.mapping.json";

    /** Her case için tek bir CaseResult satırı — Excel'in "Case Summary" sheet'inin kaynağı. */
    private static final List<CaseResult> CASE_RESULTS = new ArrayList<>();

    private static MuudSearchApi api;
    private static MappingTypeValidator validator;

    @BeforeAll
    static void setup() throws Exception {
        api = new MuudSearchApi();

        String esUrl = System.getProperty("esUrl", "").trim();
        if (!esUrl.isEmpty()) {
            String index = System.getProperty("esIndex", "muud_song_flat_v2");
            validator = MappingTypeValidator.fromElasticsearch(esUrl, index);
            System.out.println("✓ Mapping ES'den yüklendi: " + esUrl + "/" + index);
        } else {
            Path p = Path.of(System.getProperty("mappingFile", DEFAULT_MAPPING_PATH));
            validator = MappingTypeValidator.fromMappingFile(p);
            System.out.println("✓ Mapping dosyadan yüklendi: " + p);
        }
        validator.withDocumentRootPrefix("data");
        System.out.println("Toplam mapping alanı: " + validator.getFlatExpectedTypes().size());
    }

    // =========================================================================
    // CASE'LER
    // =========================================================================
    static Stream<Arguments> cases() {
        return Stream.of(
                // ── Active-indices üzerinden ────────────────────────────────────
                Arguments.of("MT_001", "sezen",   "active-indices", 20),
                Arguments.of("MT_002", "tarkan",  "active-indices", 20),
                Arguments.of("MT_003", "edis",    "active-indices", 10),
                Arguments.of("MT_004", "duman",   "active-indices", 30),
                Arguments.of("MT_005", "pop",     "active-indices", 20),
                Arguments.of("MT_006", "rihanna", "active-indices", 15),
                Arguments.of("MT_007", "sıla",    "active-indices", 20),
                Arguments.of("MT_008", "eminem",  "active-indices", 20),

                // ── Spesifik index'ler ──────────────────────────────────────────
                Arguments.of("MT_SONG_01",   "sezen",  "5", 20),  // Songs
                Arguments.of("MT_SONG_02",   "duman",  "5", 20),
                Arguments.of("MT_ALBUM_01",  "best",   "2", 15),  // Albums
                Arguments.of("MT_PERF_01",   "tarkan", "3", 15),  // Performers
                Arguments.of("MT_PERF_02",   "sıla",   "3", 15),
                Arguments.of("MT_PLAY_01",   "pop",    "4", 15),  // Playlists
                Arguments.of("MT_PLAY_02",   "rock",   "4", 15),
                Arguments.of("MT_VIDEO_01",  "tarkan", "6", 15)   // Videos
        );
    }

    @DisplayName("Mapping tip doğrulama")
    @ParameterizedTest(name = "[{0}] term={1} index={2} limit={3}")
    @MethodSource("cases")
    void mapping_type_validation(String caseId, String term, String indexId, int limit) {
        Response response = api.search(term, indexId, limit);
        int status = response.statusCode();

        // ── INDEX-ERR-010 → SKIP ────────────────────────────────────────────
        if (status == 400) {
            String body = response.asString();
            if (body != null && body.contains("INDEX-ERR-010")) {
                System.out.printf("[%s] '%s' -> %s | SKIP: INDEX-ERR-010%n", caseId, term, indexId);
                recordCase(caseId, term, indexId, "SKIPPED", List.of());
                assumeTrue(false, caseId + ": INDEX-ERR-010, skip");
                return;
            }
            recordCase(caseId, term, indexId, "FAIL", List.of());
            fail(caseId + ": API 400 ama INDEX-ERR-010 değil. Body: " + body);
        }
        if (status != 200) {
            recordCase(caseId, term, indexId, "FAIL", List.of());
            fail(caseId + ": API beklenmedik HTTP kodu: " + status);
        }

        // ── Tip doğrulama ───────────────────────────────────────────────────
        List<TypeMismatch> results = validator.validateGatewayResponse(response);

        if (results.isEmpty()) {
            System.out.printf("[%s] '%s' -> %s | SKIP: response boş%n", caseId, term, indexId);
            recordCase(caseId, term, indexId, "SKIPPED", List.of());
            assumeTrue(false, caseId + ": response boş, skip");
            return;
        }

        long pass    = results.stream().filter(TypeMismatch::isPass).count();
        long fails   = results.stream().filter(TypeMismatch::isFailure).count();
        long missing = results.stream().filter(m -> m.status() == TypeMismatch.Status.MISSING_IN_MAPPING).count();
        long nulls   = results.stream().filter(m -> m.status() == TypeMismatch.Status.NULL_VALUE).count();

        System.out.printf("[%s] '%s' -> %s | toplam=%d, PASS=%d, FAIL=%d, MISSING=%d, NULL=%d%n",
                caseId, term, indexId, results.size(), pass, fails, missing, nulls);

        String caseStatus = (fails > 0) ? "FAIL" : "PASS";
        recordCase(caseId, term, indexId, caseStatus, results);

        if (fails > 0) {
            String firstFew = results.stream()
                    .filter(TypeMismatch::isFailure)
                    .limit(5)
                    .map(m -> m.fieldPath() + " expected=" + m.expectedEsType()
                            + " actual=" + m.actualJsonType()
                            + " value=" + m.actualValue())
                    .reduce((a, b) -> a + " | " + b)
                    .orElse("");
            fail(caseId + ": " + fails + " adet tip uyuşmazlığı bulundu. İlk 5: " + firstFew);
        }
    }

    private static void recordCase(String caseId, String term, String indexId,
                                   String status, List<TypeMismatch> results) {
        CASE_RESULTS.add(new CaseResult(caseId, term, indexId, status, results));
    }

    @AfterAll
    static void writeReportIfEnabled() {
        if (!EXCEL_REPORT_ENABLED) {
            System.out.println("ℹ Excel raporu kapalı. Açmak için -DexcelReport=true ekle.");
            return;
        }
        if (CASE_RESULTS.isEmpty()) {
            System.out.println("ℹ Hiç doğrulama kaydı toplanmadı, rapor yazılmıyor.");
            return;
        }
        TypeValidationReportWriter.writeStandaloneWithCaseSummary(CASE_RESULTS);
    }
}
