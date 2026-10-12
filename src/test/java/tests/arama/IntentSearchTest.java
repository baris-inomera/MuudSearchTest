package tests.arama;

import config.TestConfig;
import client.MuudSearchApi;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import report.ExcelTestReportWriter;
import report.TestResultRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * INTENT SEARCH SIRALAMA TESTİ — DDP-398 / DDP-399
 *
 * Amaç: Intent Search kademe sisteminin UAT (hmuud tenant) üzerinde doğru
 * çalıştığını doğrular.
 *
 * Beklenen sıralama kuralları (Arama_Siralama_Kurallari.docx):
 *   Tip 1 — Sanatçı tam adı  → 1. Sanatçı sayfası, 2+ sanatçının şarkıları
 *   Tip 5 — Sanatçı + Şarkı  → 1. Şarkı, 2. Sanatçı sayfası, 3+ diğer şarkılar
 *
 * Kimlik: MuudSearchApi üzerinden — hmuud tenant token MuudSearchApi.java'da tanımlı.
 *
 * Test HİÇBİR ZAMAN fail etmez — saf gözlem & rapor üretir.
 *
 * Çalıştırmak:
 *   mvn test -Dtest=IntentSearchTest
 *
 * Çıktı: reports/intent-search/IntentSearchTest_dd.MM.yyyy_HH.mm_ORTAM.xlsx
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IntentSearchTest extends TestConfig {

    private static final int    TOP_N = 20;
    private static final Locale TR    = Locale.forLanguageTag("tr");

    private static final List<TestResultRow> ROWS = new ArrayList<>();
    private static MuudSearchApi api;

    // ── Bölüm etiketleri ——————————————————————————————————————————————————
    private static final String SEC_ARTIST_ONLY    = "Tip 1 · Sanatçı Tam Ad";
    private static final String SEC_ARTIST_PARTIAL = "Tip 2 · Sanatçı Kısmi Ad";
    private static final String SEC_ARTIST_SONG    = "Tip 5 · Sanatçı + Şarkı";

    // =========================================================================
    // CASE TANIMI
    // =========================================================================

    /**
     * @param caseId     Benzersiz kimlik (IS_xx)
     * @param term       Arama terimi
     * @param intentType "ARTIST_ONLY" | "ARTIST_SONG"
     * @param expKind1   1. sırada beklenen kind ("performers" | "songs")
     * @param expArtist  Beklenen sanatçı adı (kısmi eşleşme, boş = kontrol yok)
     * @param expSong    Beklenen şarkı adı (kısmi eşleşme, boş = kontrol yok)
     * @param section    Rapor bölümü
     */
    public record IntentCase(
            String caseId, String term, String intentType,
            String expKind1, String expArtist, String expSong, String section) {}

    static Stream<IntentCase> cases() {
        return Stream.of(
            // ── Tip 1: Sanatçı tam adı → 1. sanatçı sayfası beklenir ─────
            new IntentCase("IS_01", "sezen aksu",      "ARTIST_ONLY", "performers", "Sezen Aksu",    "",              SEC_ARTIST_ONLY),
            new IntentCase("IS_02", "tarkan",           "ARTIST_ONLY", "performers", "Tarkan",        "",              SEC_ARTIST_ONLY),
            new IntentCase("IS_03", "mabel matiz",      "ARTIST_ONLY", "performers", "Mabel Matiz",   "",              SEC_ARTIST_ONLY),
            new IntentCase("IS_04", "hadise",           "ARTIST_ONLY", "performers", "Hadise",        "",              SEC_ARTIST_ONLY),
            new IntentCase("IS_05", "ajda pekkan",      "ARTIST_ONLY", "performers", "Ajda Pekkan",   "",              SEC_ARTIST_ONLY),
            new IntentCase("IS_06", "müslüm gürses",    "ARTIST_ONLY", "performers", "Müslüm Gürses", "",              SEC_ARTIST_ONLY),

            // ── Tip 2: Sanatçı kısmi ad → sanatçı sayfası üst sıralarda ──
            new IntentCase("IS_07", "sezen",            "ARTIST_ONLY", "performers", "Sezen Aksu",    "",              SEC_ARTIST_PARTIAL),
            new IntentCase("IS_08", "mabel",            "ARTIST_ONLY", "performers", "Mabel Matiz",   "",              SEC_ARTIST_PARTIAL),

            // ── Tip 5: Sanatçı + Şarkı → 1. şarkı, 2. sanatçı sayfası ───
            new IntentCase("IS_09", "mabel kömür",                "ARTIST_SONG", "songs", "Mabel Matiz", "Kömür",         SEC_ARTIST_SONG),
            new IntentCase("IS_10", "tarkan şımarık",             "ARTIST_SONG", "songs", "Tarkan",      "Şımarık",       SEC_ARTIST_SONG),
            new IntentCase("IS_11", "sezen aksu hadi bakalım",    "ARTIST_SONG", "songs", "Sezen Aksu",  "Hadi Bakalım",  SEC_ARTIST_SONG),
            new IntentCase("IS_12", "hadise düm tek tek",         "ARTIST_SONG", "songs", "Hadise",      "Düm Tek Tek",   SEC_ARTIST_SONG),
            new IntentCase("IS_13", "ajda pekkan super star",     "ARTIST_SONG", "songs", "Ajda Pekkan", "Super Star",    SEC_ARTIST_SONG),
            new IntentCase("IS_14", "sıla rüzgar",                "ARTIST_SONG", "songs", "Sıla",        "Rüzgar",        SEC_ARTIST_SONG)
        );
    }

    // =========================================================================
    // SETUP / TEARDOWN
    // =========================================================================

    @BeforeAll
    static void init() {
        api = new MuudSearchApi();
        System.out.println("▶ IntentSearchTest başlatıldı — hmuud tenant (DDP-398/399)");
        System.out.println("  URL : " + MuudSearchApi.baseUrl());
        System.out.println("  Top-N: " + TOP_N);
    }

    @AfterAll
    static void writeReport() {
        System.out.printf("%n📋 Toplam %d case işlendi.%n", ROWS.size());
        ExcelTestReportWriter.writeBulgu(ROWS, "reports/intent-search", MuudSearchApi.ortam(), "IntentSearchTest");
    }

    // =========================================================================
    // TEST
    // =========================================================================

    @DisplayName("[Intent] Sanatçı & Şarkı arama sıralama kontrolü")
    @ParameterizedTest(name = "[{0}] \"{1}\"")
    @MethodSource("cases")
    @Order(1)
    void intent_arama_siralama(IntentCase ic) {
        Response res = api.search(ic.term(), "active-indices", TOP_N);

        String testName = ic.section() + " | \"" + ic.term() + "\"";
        String expected  = buildExpectedDesc(ic);

        if (res.statusCode() != 200) {
            ROWS.add(row(ic, "NOK", testName, expected,
                    "❌ API " + res.statusCode() + " döndü. Body: "
                    + res.body().asString().substring(0, Math.min(300, res.body().asString().length()))));
            return;
        }

        JsonPath jp     = res.jsonPath();
        String base     = detectBasePath(jp);
        List<Item> items = extractItems(jp, base, TOP_N);

        if (items.isEmpty()) {
            ROWS.add(row(ic, "NOK", testName, expected, "❌ Sonuç dönmedi. basePath=" + base));
            return;
        }

        String status = evaluate(ic, items);
        String detail  = buildDetail(ic, items, base);
        ROWS.add(row(ic, status, testName, expected, detail));
    }

    // =========================================================================
    // YARDIMCI — Analiz
    // =========================================================================

    private String detectBasePath(JsonPath jp) {
        try { if (!jp.getList("topHits").isEmpty()) return "topHits"; } catch (Exception ignored) {}
        try { if (!jp.getList("content").isEmpty())  return "content"; }  catch (Exception ignored) {}
        return "topHits";
    }

    private List<Item> extractItems(JsonPath jp, String base, int n) {
        List<Item> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String kind = safeStr(jp.getString(base + "[" + i + "].data.kind"));
            if (kind.isEmpty()) kind = safeStr(jp.getString(base + "[" + i + "].data.itemKind"));
            if (kind.isEmpty()) break;
            String artist = safeStr(jp.getString(base + "[" + i + "].data.performerName"));
            if (artist.isEmpty()) artist = safeStr(jp.getString(base + "[" + i + "].data.name"));
            String song = safeStr(jp.getString(base + "[" + i + "].data.songName"));
            double score = 0;
            try { score = jp.getDouble(base + "[" + i + "].score"); } catch (Exception ignored) {}
            list.add(new Item(i + 1, kind, artist, song, score));
        }
        return list;
    }

    /**
     * ARTIST_ONLY: 1. sıra performers + sanatçı adı eşleşmeli
     * ARTIST_SONG:  1. sıra songs + sanatçı+şarkı eşleşmeli + top-5'te performers
     */
    private String evaluate(IntentCase ic, List<Item> items) {
        Item first    = items.get(0);
        boolean kindOk   = ic.expKind1().equals(first.kind());
        boolean artistOk = ic.expArtist().isEmpty()
                || first.artist().toLowerCase(TR).contains(ic.expArtist().toLowerCase(TR));
        boolean songOk   = ic.expSong().isEmpty()
                || first.song().toLowerCase(TR).contains(ic.expSong().toLowerCase(TR));

        if ("ARTIST_SONG".equals(ic.intentType())) {
            boolean artistPageInTop5 = items.stream().limit(5)
                    .anyMatch(it -> "performers".equals(it.kind())
                            && (ic.expArtist().isEmpty()
                                || it.artist().toLowerCase(TR).contains(ic.expArtist().toLowerCase(TR))));
            return (kindOk && artistOk && songOk && artistPageInTop5) ? "OK" : "NOK";
        }
        return (kindOk && artistOk) ? "OK" : "NOK";
    }

    private String buildDetail(IntentCase ic, List<Item> items, String base) {
        Item first    = items.get(0);
        boolean kindOk   = ic.expKind1().equals(first.kind());
        boolean artistOk = ic.expArtist().isEmpty()
                || first.artist().toLowerCase(TR).contains(ic.expArtist().toLowerCase(TR));
        boolean songOk   = ic.expSong().isEmpty()
                || first.song().toLowerCase(TR).contains(ic.expSong().toLowerCase(TR));

        StringBuilder sb = new StringBuilder();
        sb.append("1. sıra: [").append(first.kind()).append("] ").append(first.artist());
        if (!first.song().isEmpty()) sb.append(" — ").append(first.song());
        sb.append(" | score=").append(String.format("%.2f", first.score())).append("\n");
        sb.append("  kind=").append(kindOk ? "✅" : "❌ (beklenen: " + ic.expKind1() + ")");
        if (!ic.expArtist().isEmpty()) sb.append("  sanatçı=").append(artistOk ? "✅" : "❌ (beklenen: " + ic.expArtist() + ")");
        if (!ic.expSong().isEmpty())   sb.append("  şarkı=").append(songOk ? "✅" : "❌ (beklenen: " + ic.expSong() + ")");

        if ("ARTIST_SONG".equals(ic.intentType())) {
            boolean artistPageInTop5 = items.stream().limit(5)
                    .anyMatch(it -> "performers".equals(it.kind())
                            && (ic.expArtist().isEmpty()
                                || it.artist().toLowerCase(TR).contains(ic.expArtist().toLowerCase(TR))));
            sb.append("  sanatçı-sayfası-top5=").append(artistPageInTop5 ? "✅" : "❌");
        }

        sb.append("\n\nİlk ").append(items.size()).append(" sonuç:\n");
        for (Item it : items) {
            sb.append(String.format("  %2d. [%-12s] %s%s | score=%.2f%n",
                    it.pos(), it.kind(), it.artist(),
                    it.song().isEmpty() ? "" : " — " + it.song(), it.score()));
        }
        return sb.toString().trim();
    }

    private String buildExpectedDesc(IntentCase ic) {
        if ("ARTIST_SONG".equals(ic.intentType())) {
            return "1. sıra: songs (" + ic.expSong() + " by " + ic.expArtist() + "); "
                    + "top-5'te: performers (" + ic.expArtist() + ")";
        }
        return "1. sıra: performers (" + ic.expArtist() + "); 2+ sıra: sanatçının şarkıları";
    }

    private TestResultRow row(IntentCase ic, String status,
                              String testName, String expected, String detail) {
        return new TestResultRow(ic.caseId(), testName,
                "\"" + ic.term() + "\" → " + ic.intentType(),
                expected, ic.section(), "active-indices", status, detail);
    }

    private record Item(int pos, String kind, String artist, String song, double score) {}

    private static String safeStr(String s) { return s == null ? "" : s.trim(); }
}
