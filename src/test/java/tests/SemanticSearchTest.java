package tests;

import client.MuudSearchApi;
import config.TestConfig;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import report.ExcelTestReportWriter;
import report.TestResultRow;
import util.MuudSearchUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  SEMANTİK ARAMA REGRESYON TESTİ
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  Kullanıcı bir kavramı doğrudan değil, EŞ ANLAMLI bir kelimeyle arar.
 *  Örnek: "mutsuz" yazınca "Üzgünüm" adlı şarkı gelmeli.
 *
 *  Şu an sistemde semantic search YOKTUR — tüm case'ler NOK beklentisiyle
 *  yazılmıştır. Semantic entegre edildikten sonra bu test tekrar koşularak
 *  düzelip düzelmediği doğrulanır.
 *
 *  Test HİÇBİR ZAMAN fail etmez — saf gözlem & kapsayıcı rapor üretir.
 *
 *  Kullanım:
 *    mvn test -Dtest=SemanticSearchTest
 *    Çıktı: proje kök dizininde TestReport_YYYYMMDD_HHmmss.xlsx
 * ─────────────────────────────────────────────────────────────────────────────
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SemanticSearchTest extends TestConfig {

    private static final int    TOP_N = 10;
    private static final Locale TR    = Locale.forLanguageTag("tr");

    // ── Bölüm sabiti ─────────────────────────────────────────────────────────
    private static final String S_SARKI_SEMANTIC = "Şarkı · Semantic";

    private static final List<TestResultRow> ROWS = new ArrayList<>();
    private static MuudSearchApi api;

    // =========================================================================
    // SETUP / TEARDOWN
    // =========================================================================

    @BeforeAll
    static void init() {
        api = new MuudSearchApi();
        System.out.println("✅ SemanticSearchTest başlatıldı — top-" + TOP_N + " değerlendirilecek.");
    }

    @AfterAll
    static void writeReport() {
        System.out.printf("%n📋 Toplam %d semantic case işlendi.%n", ROWS.size());
        ExcelTestReportWriter.writeBulgu(ROWS);
    }

    // =========================================================================
    // CASE TANIMI
    // =========================================================================

    record BulguCase(String caseId, String term, String expArtist, String expTrack, String section, int topN) {
        BulguCase(String caseId, String term, String expArtist, String expTrack, String section) {
            this(caseId, term, expArtist, expTrack, section, 5);
        }
    }

    static Stream<BulguCase> cases() {
        return Stream.of(

            // ═════════════════════════════════════════════════════════════════
            // GRUP 1 — EŞ ANLAMLI KELİME (Synonym Matching)
            // Kullanıcı X yazar, sistemin Y adlı şarkıyı getirmesi beklenir.
            // X ve Y aynı kavramı farklı kelimelerle ifade eder.
            // ═════════════════════════════════════════════════════════════════

            new BulguCase("SEM_001", "mutsuz",           "", "Üzgün",       S_SARKI_SEMANTIC, 5),  // mutsuz ≈ üzgün
            new BulguCase("SEM_002", "mahzun",           "", "Hüzün",       S_SARKI_SEMANTIC, 5),  // mahzun ≈ hüzünlü
            new BulguCase("SEM_003", "ihanet",           "", "Hain",        S_SARKI_SEMANTIC, 5),  // ihanet = hainlik
            new BulguCase("SEM_004", "ağlamak",          "", "Gözyaşı",     S_SARKI_SEMANTIC, 5),  // ağlamak → gözyaşı
            new BulguCase("SEM_005", "yalnızlık",        "", "Kimsesiz",    S_SARKI_SEMANTIC, 5),  // yalnız ≈ kimsesiz
            new BulguCase("SEM_006", "neşeli",           "", "Mutlu",       S_SARKI_SEMANTIC, 5),  // neşeli ≈ mutlu
            new BulguCase("SEM_007", "hür",              "", "Özgür",       S_SARKI_SEMANTIC, 5),  // hür = özgür
            new BulguCase("SEM_008", "acı",              "", "Sızı",        S_SARKI_SEMANTIC, 5),  // acı ≈ sızı
            new BulguCase("SEM_009", "pişman",           "", "Tövbe",       S_SARKI_SEMANTIC, 5),  // pişman → tövbe
            new BulguCase("SEM_010", "kavga",            "", "Kırgınlık",   S_SARKI_SEMANTIC, 5),  // kavga → kırgınlık
            new BulguCase("SEM_011", "sevinmek",         "", "Mutluluk",    S_SARKI_SEMANTIC, 5),  // sevinmek ≈ mutluluk
            new BulguCase("SEM_012", "özlem",            "", "Hasret",      S_SARKI_SEMANTIC, 5),  // özlem = hasret
            new BulguCase("SEM_013", "korku",            "", "Dehşet",      S_SARKI_SEMANTIC, 5),  // korku ≈ dehşet
            new BulguCase("SEM_014", "umut",             "", "Ümit",        S_SARKI_SEMANTIC, 5),  // umut = ümit
            new BulguCase("SEM_015", "üzüntü",           "", "Keder",       S_SARKI_SEMANTIC, 5),  // üzüntü ≈ keder
            new BulguCase("SEM_016", "gözyaşı",          "", "Ağlama",      S_SARKI_SEMANTIC, 5),  // gözyaşı → ağlama
            new BulguCase("SEM_017", "özgürlük",         "", "Hürriyet",    S_SARKI_SEMANTIC, 5),  // özgürlük = hürriyet
            new BulguCase("SEM_018", "sevinç",           "", "Neşe",        S_SARKI_SEMANTIC, 5),  // sevinç ≈ neşe
            new BulguCase("SEM_019", "vefasız",          "", "Dönek",       S_SARKI_SEMANTIC, 5),  // vefasız ≈ dönek
            new BulguCase("SEM_020", "kalp kırıklığı",   "", "Hüzün",       S_SARKI_SEMANTIC, 5),  // kalp kırıklığı → hüzün

            // ═════════════════════════════════════════════════════════════════
            // GRUP 2 — DURUM / HİSSİYAT TANIMI (Situation & Mood Matching)
            // Kullanıcı yaşadığı durumu veya hissini anlatır, sistem o duyguyu
            // işleyen şarkıyı getirmelidir. Sorgu ile şarkı adında kelime
            // örtüşmesi yoktur — saf kavramsal eşleşme gerektirir.
            // ═════════════════════════════════════════════════════════════════

            new BulguCase("SEM_021", "sevgilimden ayrıldım",              "", "Elveda",      S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_022", "aşk acısı çekiyorum",               "", "Sızı",        S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_023", "çok güzel bir gün geçirdim",         "", "Neşe",        S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_024", "annemi özledim",                     "", "Hasret",      S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_025", "hayattan yoruldum",                  "", "Bıktım",      S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_026", "aldatıldım",                         "", "İhanet",      S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_027", "birine aşık oldum",                  "", "Sevda",       S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_028", "birini bekliyorum haber yok",        "", "Hasret",      S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_029", "ağlayarak uyudum",                   "", "Gözyaşı",     S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_030", "sensiz olmak çok zor",               "", "Yalnız",      S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_031", "geçmişi özlüyorum",                  "", "Nostalji",    S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_032", "sana küstüm",                        "", "Kırgınlık",   S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_033", "beni terk etti",                     "", "Yalnız",      S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_034", "uzaklarda olan birine özlem",        "", "Gurbet",      S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_035", "yeniden başlamak istiyorum",         "", "Ümit",        S_SARKI_SEMANTIC, 5),

            // ═════════════════════════════════════════════════════════════════
            // GRUP 3 — BAĞLAM / SENARYO (Contextual & Scenario Matching)
            // Kullanıcı bir sahne veya ortamı tanımlar; sistem o atmosferi
            // yansıtan şarkıyı getirmelidir.
            // ═════════════════════════════════════════════════════════════════

            new BulguCase("SEM_036", "yağmur yağıyor içim sıkıldı",       "", "Hüzün",       S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_037", "güneşli sabahta mutlu uyanmak",      "", "Neşe",        S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_038", "gecenin karanlığında yalnız",        "", "Kimsesiz",    S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_039", "şehrin gürültüsünden kaçmak",        "", "Hürriyet",    S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_040", "arkadaşımı kaybettim",               "", "Özlem",       S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_041", "düğün gecesi dans etmek",            "", "Coşku",       S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_042", "tüm umutlarım bitti",                "", "Hüsran",      S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_043", "çaresiz bir aşk",                    "", "Mecnun",      S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_044", "bu aşk beni mahvetti",               "", "Sızı",        S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_045", "hayaller ve gerçekler arasında",     "", "Hayal",       S_SARKI_SEMANTIC, 5),

            // ═════════════════════════════════════════════════════════════════
            // GRUP 4 — UÇ CASE / ZOR SEMANTİK (Hard Semantic Edge Cases)
            // Sorgu ile beklenen şarkı arasındaki anlam köprüsü daha uzaktır.
            // Semantic model bu köprüyü kurabilmeli.
            // ═════════════════════════════════════════════════════════════════

            new BulguCase("SEM_046", "hayat kısa tadını çıkar",            "", "Coşku",       S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_047", "geceleri uyuyamıyorum",              "", "Huzursuz",    S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_048", "vatanımdan uzaktayım",               "", "Gurbet",      S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_049", "seninle her şey güzel",              "", "Mutluluk",    S_SARKI_SEMANTIC, 5),
            new BulguCase("SEM_050", "herkes beni terk etti yapayalnızım", "", "Kimsesiz",    S_SARKI_SEMANTIC, 5)

        );
    }

    // =========================================================================
    // TEST — ASLA FAIL ETMEZ
    // =========================================================================

    @ParameterizedTest(name = "[{0}] \"{1}\"")
    @MethodSource("cases")
    @Order(1)
    void run(BulguCase bc) {
        String[] result = new String[]{"(beklenen tanımlanmamış)", "NOK", "API hatası"};

        try {
            Response res = api.search(bc.term(), "active-indices", TOP_N);
            JsonPath jp  = res.jsonPath();
            result = evaluate(bc, jp);
        } catch (Exception e) {
            System.err.printf("⚠️  API hatası [%s '%s']: %s%n",
                    bc.caseId(), bc.term(), e.getMessage());
            result[2] = "API hatası: " + e.getMessage();
        }

        System.out.printf("[%s] %-10s | %-30s | %s%n",
                result[1], bc.caseId(), "\"" + bc.term() + "\"", result[2]);

        ROWS.add(new TestResultRow(
                bc.caseId(),
                "\"" + bc.term() + "\" araması yapılır",
                "Semantic arama: '" + bc.term() + "' → beklenen eş anlamlı şarkı: '" + bc.expTrack() + "'",
                result[0],
                bc.section(),
                "active-indices",
                result[1],
                result[2]
        ));
    }

    // =========================================================================
    // KURAL DEĞERLENDİRME
    // =========================================================================

    private String[] evaluate(BulguCase bc, JsonPath jp) {
        List<Object> list = MuudSearchUtils.resultsList(jp);
        String       base = MuudSearchUtils.getBasePath(jp);

        if (list.isEmpty()) {
            return new String[]{
                    "Arama sonucunda en az 1 kayıt dönmesi beklenir.",
                    "NOK",
                    "API boş sonuç döndürdü."};
        }

        return evalArtistAndTrack(bc, jp, base);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOPN_HAS_ARTIST_AND_TRACK — Top-N içinde eş anlamlı şarkı adı bulunmalı
    // ─────────────────────────────────────────────────────────────────────────

    private String[] evalArtistAndTrack(BulguCase bc, JsonPath jp, String base) {
        int    n        = bc.topN();
        String expected = "Top-" + n + " içinde '" + bc.expTrack() + "' adını içeren şarkı gelmeli. " +
                          "(Semantic: '" + bc.term() + "' → '" + bc.expTrack() + "' eş anlamlı eşleşme)";

        int idx = MuudSearchUtils.findArtistAndTrackIndex(jp, n, bc.expArtist(), bc.expTrack());

        if (idx != -1) {
            String fa = MuudSearchUtils.getPerformerName(jp, base + "[" + idx + "].data");
            String ft = MuudSearchUtils.safeStr(jp.getString(base + "[" + idx + "].data.songName"));
            String label = fa.isEmpty() ? ft : fa + "' – '" + ft;
            return new String[]{expected, "OK",
                    "Başarılı — " + (idx + 1) + ". sırada: '" + label + "'."};
        }

        int    fullIdx = MuudSearchUtils.findArtistAndTrackIndex(jp, TOP_N, bc.expArtist(), bc.expTrack());
        String where   = fullIdx == -1
                ? "top-" + TOP_N + "'da da bulunamadı"
                : (fullIdx + 1) + ". sırada bulundu";
        return new String[]{expected, "NOK",
                "Top-" + n + "'da yok — '" + bc.expTrack() + "': " + where + ".\n"
                        + top5Desc(jp, base)};
    }

    // =========================================================================
    // YARDIMCI
    // =========================================================================

    private String itemDesc(JsonPath jp, String base, int i) {
        String song      = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.songName"));
        String album     = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.albumName"));
        String playlist  = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.playlistName"));
        String performer = MuudSearchUtils.getPerformerName(jp, base + "[" + i + "].data");

        if (!song.isEmpty())
            return performer.isEmpty() ? "'" + song + "'" : "'" + performer + " – " + song + "'";
        if (!album.isEmpty())
            return "[Albüm] '" + album + "'" + (performer.isEmpty() ? "" : " – '" + performer + "'");
        if (!playlist.isEmpty())
            return "[Playlist] '" + playlist + "'";
        if (!performer.isEmpty())
            return "[Sanatçı] '" + performer + "'";
        return "(boş)";
    }

    private String top5Desc(JsonPath jp, String base) {
        StringBuilder sb = new StringBuilder("İlk 5 sonuç:");
        for (int i = 0; i < 5; i++) {
            sb.append("\n  ").append(i + 1).append(". ").append(itemDesc(jp, base, i));
        }
        return sb.toString();
    }
}
