package tests;

import client.MuudSearchApi;
import config.TestConfig;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import report.MappingDiscoveryReportWriter;
import validation.DiscoveryCoverage;
import validation.DiscoveryFieldSummary;
import validation.DiscoveryViolation;
import validation.MappingTypeValidator;
import validation.TypeMismatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MAPPING DISCOVERY TESTİ — Geniş Tarama / Otomatik Bug Bulma
 *
 *  Amaç:
 *    Sabit "tarkan / sezen" gibi manuel arama term'lerine bağlı kalmak yerine,
 *    geniş bir term + index kombinasyonu üzerinden tarama yapar.
 *    Bulunan TÜM tip uyuşmazlıkları (FAIL) tekilleştirilerek raporlanır.
 *
 *  Çıktı (Excel):
 *    1) Field Summary  — her benzersiz (index, field, expected→actual) bir satır
 *                        ve kaç farklı doc'ta görüldüğü
 *    2) All Violations — ham (tüm) kayıtlar, debug için
 *    3) Coverage       — index başına arama / doc / unique / violation istatistiği
 *    4) Terms          — kullanılan arama term'leri
 *
 *  Çalıştırma:
 *    mvn test -Dtest=MuudSearchMappingDiscoveryTest \
 *             -DesUrl=http://127.0.0.1:9200 \
 *             -DexcelReport=true
 *
 *  Override edilebilir parametreler:
 *    -DesUrl           ES URL                       (default http://127.0.0.1:9200)
 *    -DlimitPerSearch  Her aramada kaç doc istenir (default 50)
 *    -DexcelReport     true/false                   (default false)
 *
 *  INDEX ID'leri (Mayıs 2026):
 *    2 → Albums, 3 → Performers, 4 → Playlists, 5 → Songs, 6 → Videos
 *
 *  GÜVENLİK NOTU:
 *    Bu test READ-ONLY. ES'i değiştirmez, sadece okuma yapar.
 *    Prod ortamda da güvenle çalıştırılabilir (sadece okuma + biraz gateway yükü).
 */
public class MuudSearchMappingDiscoveryTest extends TestConfig {

    private static final boolean EXCEL_REPORT_ENABLED =
            Boolean.parseBoolean(System.getProperty("excelReport", "false"));

    private static final String ES_URL =
            System.getProperty("esUrl", "http://127.0.0.1:9200");

    private static final int LIMIT_PER_SEARCH =
            Integer.parseInt(System.getProperty("limitPerSearch", "50"));

    // -------------------------------------------------------------------------
    // INDEXLER
    // -------------------------------------------------------------------------
    private record IndexDef(String indexId, String esIndex, String label) {}

    private static final List<IndexDef> INDICES = List.of(
            new IndexDef("5", "muud_song_flat_v2",      "Songs"),
            new IndexDef("2", "muud_album_flat_v2",     "Albums"),
            new IndexDef("3", "muud_performer_flat_v2", "Performers"),
            new IndexDef("4", "muud_playlist_flat_v2",  "Playlists"),
            new IndexDef("6", "muud_video_flat_v2",     "Videos")
    );

    // -------------------------------------------------------------------------
    // TERM LİSTESİ — geniş tarama için çeşitli kelimeler
    //  * Tek harfler farklı doc'ları getirir (her harfle başlayan şarkı/sanatçı vardır)
    //  * Yaygın türkçe ve ingilizce kelimeler
    //  * Bilinen sanatçı isimleri
    // -------------------------------------------------------------------------
    private static final List<String> TERMS = List.of(
            // Türk alfabesi (tek harf)
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "k",
            "l", "m", "n", "o", "p", "r", "s", "t", "u", "y", "z",
            // Yaygın Türkçe kelimeler
            "aşk", "sevgi", "dans", "gece", "yaz", "kış", "mavi", "gül", "yıldız",
            // Sanatçı isimleri
            "tarkan", "sezen", "sıla", "edis", "duman", "ajda", "hadise",
            "mfö", "manga", "athena", "teoman",
            // İngilizce
            "love", "rock", "pop", "rap", "summer", "party", "remix"
    );

    // -------------------------------------------------------------------------
    // STATE
    // -------------------------------------------------------------------------
    private static MuudSearchApi api;
    private static final Map<String, MappingTypeValidator> validators = new HashMap<>();
    private static final List<DiscoveryViolation> ALL_VIOLATIONS = new ArrayList<>();
    private static final List<DiscoveryCoverage> COVERAGE = new ArrayList<>();

    @BeforeAll
    static void setup() {
        api = new MuudSearchApi();

        System.out.println("=== MAPPING DISCOVERY TEST ===");
        System.out.println("ES URL          : " + ES_URL);
        System.out.println("Limit/search    : " + LIMIT_PER_SEARCH);
        System.out.println("Term sayısı     : " + TERMS.size());
        System.out.println("Index sayısı    : " + INDICES.size());
        System.out.println("Toplam search   : " + (TERMS.size() * INDICES.size()));
        System.out.println();

        // Her index için validator yükle (ES mapping'i çek)
        for (IndexDef idx : INDICES) {
            try {
                MappingTypeValidator v = MappingTypeValidator.fromElasticsearch(ES_URL, idx.esIndex);
                v.withDocumentRootPrefix("data");
                validators.put(idx.indexId, v);
                System.out.printf("✓ %-11s (%s) mapping yüklendi: %d alan%n",
                        idx.label, idx.esIndex, v.getFlatExpectedTypes().size());
            } catch (Exception e) {
                System.out.printf("✗ %-11s mapping yüklenemedi: %s%n", idx.label, e.getMessage());
            }
        }
        System.out.println();
    }

    @Test
    @DisplayName("Tüm indekslerde tip uyuşmazlığı tara (discovery)")
    void discover_all_type_mismatches() {
        Set<String> globalSeenDocs = new HashSet<>();
        long startMs = System.currentTimeMillis();

        for (IndexDef idx : INDICES) {
            MappingTypeValidator validator = validators.get(idx.indexId);
            if (validator == null) {
                System.out.printf("[%s] mapping yok, atlanıyor%n", idx.label);
                continue;
            }

            int searchCount      = 0;
            int passedSearches   = 0;
            int docEvaluations   = 0;
            int violationCount   = 0;
            Set<String> uniqueDocsThisIdx = new HashSet<>();

            for (String term : TERMS) {
                Response response;
                try {
                    response = api.search(term, idx.indexId, LIMIT_PER_SEARCH);
                } catch (Exception e) {
                    System.out.printf("[%s '%s'] search hatası: %s%n",
                            idx.label, term, e.getMessage());
                    continue;
                }
                searchCount++;

                int status = response.statusCode();
                if (status != 200) {
                    // INDEX-ERR-010 vs. — sessizce geç, count'a yansımasın
                    continue;
                }
                passedSearches++;

                List<TypeMismatch> mismatches = validator.validateGatewayResponse(response);
                if (mismatches.isEmpty()) continue;

                // Bu response'taki benzersiz doc'ları say
                Set<String> docsInThisResponse = new HashSet<>();
                for (TypeMismatch tm : mismatches) {
                    docsInThisResponse.add(tm.docId());
                }
                docEvaluations += docsInThisResponse.size();
                for (String d : docsInThisResponse) {
                    uniqueDocsThisIdx.add(d);
                    globalSeenDocs.add(idx.esIndex + "|" + d);
                }

                // Sadece FAIL'leri biriktir
                for (TypeMismatch tm : mismatches) {
                    if (tm.status() != TypeMismatch.Status.FAIL) continue;
                    ALL_VIOLATIONS.add(new DiscoveryViolation(
                            idx.label, idx.esIndex, idx.indexId, term,
                            tm.docId(), tm.fieldPath(),
                            tm.expectedEsType(), tm.actualJsonType(),
                            String.valueOf(tm.actualValue()),
                            tm.note()
                    ));
                    violationCount++;
                }
            }

            COVERAGE.add(new DiscoveryCoverage(
                    idx.label, idx.esIndex, idx.indexId,
                    searchCount, docEvaluations,
                    uniqueDocsThisIdx.size(), violationCount, passedSearches
            ));

            System.out.printf("[%-11s] search=%d (ok=%d) docs=%d unique=%d FAIL=%d%n",
                    idx.label, searchCount, passedSearches,
                    docEvaluations, uniqueDocsThisIdx.size(), violationCount);
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        System.out.println();
        System.out.println("=== DISCOVERY ÖZETİ ===");
        System.out.printf("Toplam süre        : %.1f sn%n", elapsedMs / 1000.0);
        System.out.printf("Toplam violation   : %d (raw)%n", ALL_VIOLATIONS.size());
        System.out.printf("Toplam benzersiz doc: %d%n", globalSeenDocs.size());

        // -- Field-bazlı dedup ----------------------------------------------------
        Map<String, DiscoveryFieldSummary> fieldSummaries = aggregateByField(ALL_VIOLATIONS);
        System.out.printf("Benzersiz field-bazlı bug: %d%n", fieldSummaries.size());

        // En çok etkilenen ilk 15
        System.out.println();
        System.out.println("=== EN ÇOK ETKİLENEN ALANLAR ===");
        fieldSummaries.values().stream()
                .sorted((a, b) -> Integer.compare(b.affectedDocs().size(), a.affectedDocs().size()))
                .limit(15)
                .forEach(fs -> System.out.printf("  [%-11s] %s  (%s → %s)  %d doc%n",
                        fs.indexLabel(), fs.fieldPath(),
                        fs.expectedType(), fs.actualType(),
                        fs.affectedDocs().size()));

        // Test PASS kabul edilir — discovery raporlama amaçlı, bug bulması doğal
        // (CI'da fail etmesini istersen aşağıdaki Assertions.fail satırını aç)
        // if (!fieldSummaries.isEmpty()) {
        //     org.junit.jupiter.api.Assertions.fail("Tip uyuşmazlığı tespit edildi: " + fieldSummaries.size());
        // }
    }

    /**
     * (esIndex, fieldPath, expectedType, actualType) tuple'ı üzerinde aggregate.
     */
    public static Map<String, DiscoveryFieldSummary> aggregateByField(List<DiscoveryViolation> violations) {
        Map<String, DiscoveryFieldSummary> map = new LinkedHashMap<>();
        for (DiscoveryViolation v : violations) {
            DiscoveryFieldSummary fs = map.computeIfAbsent(
                    keyOf(v),
                    k -> new DiscoveryFieldSummary(
                            v.indexLabel(), v.esIndex(), v.fieldPath(),
                            v.expectedType(), v.actualType()));
            fs.addOccurrence(v.docId(), v.actualValue(), v.term());
        }
        return map;
    }

    private static String keyOf(DiscoveryViolation v) {
        return v.esIndex() + "|" + v.fieldPath() + "|" + v.expectedType() + "|" + v.actualType();
    }

    @AfterAll
    static void writeReportIfEnabled() {
        if (!EXCEL_REPORT_ENABLED) {
            System.out.println("ℹ Excel raporu kapalı. Açmak için -DexcelReport=true ekle.");
            return;
        }
        Map<String, DiscoveryFieldSummary> summaries = aggregateByField(ALL_VIOLATIONS);
        MappingDiscoveryReportWriter.write(
                new ArrayList<>(summaries.values()),
                ALL_VIOLATIONS,
                COVERAGE,
                TERMS
        );
    }
}
