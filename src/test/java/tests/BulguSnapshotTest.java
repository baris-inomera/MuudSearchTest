package tests;

import client.MuudSearchApi;
import config.TestConfig;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import report.BulguSnapshotWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  BULGU SNAPSHOT TESTİ — Top-10 Regresyon Takipçisi
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  Amaç:
 *    Her deployment öncesi/sonrası çalıştırılır.
 *    124 arama terimi için Muud active-indices top-10 sonuçlarını çeker.
 *    Beklenen içeriğin hangi sırada geldiğini Excel'e yazar.
 *    Test HİÇBİR ZAMAN fail etmez — saf gözlem & regresyon raporu üretir.
 *
 *  CagkanBulgular.java ile farkı:
 *    CagkanBulgular → pass/fail (belirli bir sonuç bekleniyor mu?)
 *    BulguSnapshotTest → gözlem (şu an top-10'da gerçekte ne var?)
 *
 *  Kullanım:
 *    mvn test -Dtest=BulguSnapshotTest
 *    Çıktı: proje kök dizininde BulguSnapshot_YYYYMMDD_HHmmss.xlsx
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BulguSnapshotTest extends TestConfig {

    private static final int TOP_N = 10;
    private static final Locale TR = Locale.forLanguageTag("tr");

    private static final List<BulguSnapshotWriter.SnapshotRow> ROWS = new ArrayList<>();
    private static MuudSearchApi api;

    // =========================================================================
    // SETUP / TEARDOWN
    // =========================================================================

    @BeforeAll
    static void init() {
        api = new MuudSearchApi();
        System.out.println("✅ BulguSnapshotTest başlatıldı — " + TOP_N + " sonuç toplanacak.");
    }

    @AfterAll
    static void writeReport() {
        System.out.printf("%n📋 Toplam %d case işlendi.%n", ROWS.size());
        BulguSnapshotWriter.write(ROWS);
    }

    // =========================================================================
    // CASE TANIMLAMASI
    // =========================================================================

    /**
     * @param caseId    Bulgu numarası
     * @param term      Arama terimi
     * @param expArtist Beklenen sanatçı (boş bırakılabilir)
     * @param expTrack  Beklenen şarkı/albüm adı (boş bırakılabilir)
     * @param section   Hangi bölüme ait (raporlama için)
     */
    record BulguCase(String caseId, String term, String expArtist, String expTrack, String section) {}

    static Stream<BulguCase> cases() {
        return Stream.of(

                // ─────────────────────────────────────────────────────────────
                // BÖLÜM 1 — İÇERİK HİÇ GELMİYOR
                // (Şarkı sistemde var ama arama sonuçlarında çıkmıyor)
                // ─────────────────────────────────────────────────────────────
                new BulguCase("BULGU_001", "Hermes",               "Batuflex",        "Hermès 2.0",                   "İçerik Yok"),
                new BulguCase("BULGU_002", "a canım",              "Mabel Matiz",     "A Canım",                      "İçerik Yok"),
                new BulguCase("BULGU_003", "acanım",               "Mabel Matiz",     "A Canım",                      "İçerik Yok"),
                new BulguCase("BULGU_010", "maraton",              "Ati242",          "Maraton",                      "İçerik Yok"),
                new BulguCase("BULGU_013", "meğerse",              "Liner",           "Meğerse",                      "İçerik Yok"),
                new BulguCase("BULGU_014", "çok pardon",           "Lvbel C5",        "COOOK PARDON",                 "İçerik Yok"),
                new BulguCase("BULGU_032", "dame un grr",          "",                "Dame Un Grr",                  "İçerik Yok"),
                new BulguCase("BULGU_034", "vidrado em",           "",                "Vidrado",                      "İçerik Yok"),
                new BulguCase("BULGU_036", "can efendim",          "",                "Can Efendim",                  "İçerik Yok"),
                new BulguCase("BULGU_037", "çıt çıt",              "",                "Çıt Çıt",                      "İçerik Yok"),
                new BulguCase("BULGU_038", "çıt çıt çedene",       "",                "Çıt Çıt",                      "İçerik Yok"),
                new BulguCase("BULGU_040", "hav hav",              "Lvbel C5",        "Havhavhav",                    "İçerik Yok"),
                new BulguCase("BULGU_046", "can ozan",             "Canozan",         "",                             "İçerik Yok"),
                new BulguCase("BULGU_053", "kök$l",                "Kök",             "",                             "İçerik Yok"),
                new BulguCase("BULGU_054", "just the way you are", "",                "Just The Way You Are",         "İçerik Yok"),
                new BulguCase("BULGU_057", "y",                    "Poizi",           "Y",                            "İçerik Yok"),
                new BulguCase("BULGU_058", "y poizi",              "Poizi",           "Y",                            "İçerik Yok"),
                new BulguCase("BULGU_062", "yaramızda kalsın",     "Merve Özbey",     "Yaramızda Kalsın",             "İçerik Yok"),
                new BulguCase("BULGU_110", "lvc5",                 "Lvbel C5",        "",                             "İçerik Yok"),
                new BulguCase("BULGU_115", "pozi",                 "Poizi",           "",                             "İçerik Yok"),
                new BulguCase("BULGU_116", "level c5",             "Lvbel C5",        "",                             "İçerik Yok"),
                new BulguCase("BULGU_117", "levelc5",              "Lvbel C5",        "",                             "İçerik Yok"),

                // ─────────────────────────────────────────────────────────────
                // BÖLÜM 2 — EXACT MATCH İLK SIRAYA GELEMİYOR
                // (Şarkı var ama başka içerik önde geliyor)
                // ─────────────────────────────────────────────────────────────
                new BulguCase("BULGU_004", "olmazlara vuruluyorum", "Mert Demir",     "Olmazlara Vuruluyorum",        "Sıralama"),
                new BulguCase("BULGU_005", "çıkmaz bir sokakta",    "",               "Çıkmaz Bir Sokakta",           "Sıralama"),
                new BulguCase("BULGU_011", "geri ver",              "Wegh",           "Geri Ver",                     "Sıralama"),
                new BulguCase("BULGU_012", "saygımdan",             "Bengü",          "Saygımdan",                    "Sıralama"),
                new BulguCase("BULGU_015", "dacia",                 "Lvbel C5",       "Dacia",                        "Sıralama"),
                new BulguCase("BULGU_020", "yalnızlığın çaresini bulmuşlar", "",      "Yalnızlığın Çaresini Bulmuşlar","Sıralama"),
                new BulguCase("BULGU_023", "yapar mısın",           "Poizi",          "Yapar Mısın",                  "Sıralama"),
                new BulguCase("BULGU_024", "yerinde",               "Sefo",           "Yerinde Dur",                  "Sıralama"),
                new BulguCase("BULGU_025", "yerinde dur",           "Sefo",           "Yerinde Dur",                  "Sıralama"),
                new BulguCase("BULGU_028", "ey aşk",                "Sezen Aksu",     "Ey Aşk",                       "Sıralama"),
                new BulguCase("BULGU_031", "giderim kırağınan",     "Onur Şan",       "Giderim Kırağınan",            "Sıralama"),
                new BulguCase("BULGU_033", "ara beni lütfen",       "Funda Arar",     "Ara Beni Lütfen",              "Sıralama"),
                new BulguCase("BULGU_035", "aşk yok olmaktır",      "",               "Aşk Yok Olmaktır",             "Sıralama"),
                new BulguCase("BULGU_039", "çıkar biri karşıma",    "Poizi",          "Çıkar Biri Karşıma",           "Sıralama"),
                new BulguCase("BULGU_041", "messy lola young",      "Lola Young",     "Messy",                        "Sıralama"),
                new BulguCase("BULGU_042", "sen yanlış yaptın",     "Şahin Kendirci", "Sen Yanlış Yaptın",            "Sıralama"),
                new BulguCase("BULGU_043", "vay dayı",              "Aynur Polat",    "Vay Dayı",                     "Sıralama"),
                new BulguCase("BULGU_044", "silinmez",              "Mansur Ark",     "Silinmez",                     "Sıralama"),
                new BulguCase("BULGU_045", "halbuki",               "Yalın",          "Halbuki",                      "Sıralama"),
                new BulguCase("BULGU_047", "duydun mu",             "Yusuf Güney",    "Duydun Mu",                    "Sıralama"),
                new BulguCase("BULGU_048", "sana güvenmiyorum",     "Dedüblüman",     "Sana Güvenmiyorum",            "Sıralama"),
                new BulguCase("BULGU_049", "yasemen",               "Afra",           "Yasemen",                      "Sıralama"),
                new BulguCase("BULGU_050", "düşer o",               "İzel",           "Düşer O",                      "Sıralama"),
                new BulguCase("BULGU_051", "kömür",                 "Mabel Matiz",    "Kömür",                        "Sıralama"),
                new BulguCase("BULGU_052", "mabel kömür",           "Mabel Matiz",    "Kömür",                        "Sıralama"),
                new BulguCase("BULGU_056", "snap",                  "Manifest",       "Snap",                         "Sıralama"),
                new BulguCase("BULGU_059", "ama başaramadım",       "Burak Bulut",    "Ama Başaramadım",              "Sıralama"),
                new BulguCase("BULGU_060", "kts manifest",          "Manifest",       "KTS",                          "Sıralama"),
                new BulguCase("BULGU_061", "adına bir çizik çektim","",               "Adına Bir Çizik Çektim",       "Sıralama"),
                new BulguCase("BULGU_063", "sev yeter",             "",               "Sev Yeter",                    "Sıralama"),
                new BulguCase("BULGU_065", "kaybolurum gülüşünde",  "İkilem",         "Kaybolurum Gülüşünde",         "Sıralama"),
                new BulguCase("BULGU_066", "bak ben yara gibiyim",  "Emir Can İğrek", "Nalan",                        "Sıralama"),
                new BulguCase("BULGU_071", "ağlama ben ağlarım",    "Canozan",        "Ağlama Ben Ağlarım",           "Sıralama"),
                new BulguCase("BULGU_072", "ağlama ben",            "Canozan",        "Ağlama Ben Ağlarım",           "Sıralama"),
                new BulguCase("BULGU_074", "erik",                  "",               "Erik Dalı",                    "Sıralama"),
                new BulguCase("BULGU_077", "karakedi",              "Melis Fis",      "Kara Kedi",                    "Sıralama"),
                new BulguCase("BULGU_080", "şikayetim var",         "",               "Şikayetim Var",                "Sıralama"),
                new BulguCase("BULGU_081", "bunca yıl",             "Dedüblüman",     "Bunca Yıl",                    "Sıralama"),
                new BulguCase("BULGU_082", "düldül",                "Mabel Matiz",    "Düldül",                       "Sıralama"),
                new BulguCase("BULGU_086", "perde",                 "Poizi",          "Perde",                        "Sıralama"),
                new BulguCase("BULGU_092", "sonbahar",              "Era7Capone",     "SONBAHAR",                     "Sıralama"),
                new BulguCase("BULGU_093", "acem kızı",             "",               "Acem Kızı",                    "Sıralama"),
                new BulguCase("BULGU_094", "hacel obası",           "",               "Hacel Obası",                  "Sıralama"),
                new BulguCase("BULGU_095", "yalan",                 "",               "Yalan",                        "Sıralama"),
                new BulguCase("BULGU_096", "bana sor",              "Ferdi Tayfur",   "Bana Sor",                     "Sıralama"),
                new BulguCase("BULGU_097", "rüya manifest",         "Manifest",       "Rüya",                         "Sıralama"),
                new BulguCase("BULGU_098", "rüya",                  "Manifest",       "Rüya",                         "Sıralama"),
                new BulguCase("BULGU_100", "ara",                   "Zeynep Bastık",  "Ara",                          "Sıralama"),
                new BulguCase("BULGU_101", "14 bahar",              "Mert Demir",     "14 Bahar",                     "Sıralama"),
                new BulguCase("BULGU_103", "ela mana",              "",               "Ela Mana",                     "Sıralama"),
                new BulguCase("BULGU_105", "erik dalı",             "",               "Erik Dalı",                    "Sıralama"),
                new BulguCase("BULGU_106", "elfida",                "",               "Elfida",                       "Sıralama"),
                new BulguCase("BULGU_107", "yazan kalem siyah",     "",               "Yazan Kalem Siyah",            "Sıralama"),
                new BulguCase("BULGU_111", "merdo",                 "",               "Merdo",                        "Sıralama"),
                new BulguCase("BULGU_121", "misket",                "",               "Misket",                       "Sıralama"),
                new BulguCase("BULGU_122", "kara sevda",            "",               "Kara Sevda",                   "Sıralama"),
                new BulguCase("BULGU_123", "parla",                 "",               "Parla",                        "Sıralama"),
                new BulguCase("BULGU_124", "kırmızı balık",         "",               "Kırmızı Balık",                "Sıralama"),

                // ─────────────────────────────────────────────────────────────
                // BÖLÜM 3 — LYRİC / YAZIM HATALI ARAMALAR
                // ─────────────────────────────────────────────────────────────
                new BulguCase("BULGU_067", "çölüme yağmur oldun",   "Müslüm Gürses",  "Affet",                        "Lyric Arama"),
                new BulguCase("BULGU_068", "sana hastayım anlasana","Derya Uluğ",     "Yansıma",                      "Lyric Arama"),
                new BulguCase("BULGU_070", "Dua Lipa Shine",        "Dua Lipa",       "Shine",                        "Lyric Arama"),
                new BulguCase("BULGU_075", "hadi ya",               "Melis Kar",      "Yatıya",                       "Lyric Arama"),
                new BulguCase("BULGU_076", "babalar",               "Blok3",          "PATLAT",                       "Lyric Arama"),
                new BulguCase("BULGU_079", "silemez o beni",        "Yıldız Tilbe",   "Dizine Dursun",                "Lyric Arama"),
                new BulguCase("BULGU_083", "çetin ceviz şerbetli mayam","Melike Şahin","Canın Beni Çekti",            "Lyric Arama"),
                new BulguCase("BULGU_084", "bir motive",            "Motive",         "",                             "Lyric Arama"),

                // ─────────────────────────────────────────────────────────────
                // BÖLÜM 4 — SANATÇI SIRALAMA SORUNLARI
                // (Beklenen sanatçı 1. sırada gelmeli)
                // ─────────────────────────────────────────────────────────────
                new BulguCase("BULGU_008", "mfö",                   "MFÖ",            "",                             "Sanatçı Sıralama"),
                new BulguCase("BULGU_009", "mfo",                   "MFÖ",            "",                             "Sanatçı Sıralama"),
                new BulguCase("BULGU_016", "Manifest",              "Manifest",       "",                             "Sanatçı Sıralama"),
                new BulguCase("BULGU_017", "semicenk",              "Semicenk",       "",                             "Sanatçı Sıralama"),
                new BulguCase("BULGU_018", "Semicenk",              "Semicenk",       "",                             "Sanatçı Sıralama"),
                new BulguCase("BULGU_055", "utku akkaya",           "Utku Akkaya",    "",                             "Sanatçı Sıralama"),
                new BulguCase("BULGU_088", "derya bedavacı",        "Derya Bedavacı", "",                             "Sanatçı Sıralama"),
                new BulguCase("BULGU_090", "ceza",                  "Ceza",           "",                             "Sanatçı Sıralama"),
                new BulguCase("BULGU_091", "Ceza",                  "Ceza",           "",                             "Sanatçı Sıralama"),
                new BulguCase("BULGU_099", "çakal",                 "çakal",          "",                             "Sanatçı Sıralama"),
                new BulguCase("BULGU_102", "yaşar",                 "Yaşar",          "",                             "Sanatçı Sıralama"),
                new BulguCase("BULGU_112", "Gökhan Özen",           "Gökhan Özen",    "",                             "Sanatçı Sıralama"),
                new BulguCase("BULGU_118", "çelik",                 "Çelik",          "",                             "Sanatçı Sıralama"),
                new BulguCase("BULGU_119_B","Haluk Levent",         "Haluk Levent",   "",                             "Sanatçı Sıralama"),
                new BulguCase("BULGU_120", "Mustafa Yıldızdoğan",  "Mustafa Yıldızdoğan","",                          "Sanatçı Sıralama"),

                // ─────────────────────────────────────────────────────────────
                // BÖLÜM 5 — TOLERANS / KISALTMA ARAMALARI
                // ─────────────────────────────────────────────────────────────
                new BulguCase("BULGU_006", "blok",                  "Blok3",          "",                             "Tolerans"),
                new BulguCase("BULGU_007", "kusura bakma",          "Blok3",          "Kusura Bakma",                 "Tolerans"),
                new BulguCase("BULGU_021", "tarkn",                 "Tarkan",         "",                             "Tolerans"),
                new BulguCase("BULGU_029", "simarik",               "Tarkan",         "Şımarık",                      "Tolerans"),
                new BulguCase("BULGU_030", "şımarık",               "Tarkan",         "Şımarık",                      "Tolerans"),

                // ─────────────────────────────────────────────────────────────
                // BÖLÜM 6 — PLAYLİST / KATEGORİ ARAMALARI
                // ─────────────────────────────────────────────────────────────
                new BulguCase("BULGU_026", "90 lar",                "",               "",                             "Playlist"),
                new BulguCase("BULGU_027", "çocuk",                 "",               "",                             "Playlist"),
                new BulguCase("BULGU_064", "pop",                   "",               "",                             "Playlist"),
                new BulguCase("BULGU_069", "yabancı",               "",               "",                             "Playlist"),
                new BulguCase("BULGU_087", "akustik",               "",               "",                             "Playlist"),

                // ─────────────────────────────────────────────────────────────
                // BÖLÜM 7 — AUTO-CORRECT / TÜRKÇEye ÇEKİLME
                // ─────────────────────────────────────────────────────────────
                new BulguCase("BULGU_073", "arabam",                "",               "",                             "Auto-Correct"),
                new BulguCase("BULGU_078", "doğuştan",              "Lvbel C5",       "Doğuştan Beri Haklıyım",       "Auto-Correct"),
                new BulguCase("BULGU_104", "yekten",                "",               "",                             "Auto-Correct"),
                new BulguCase("BULGU_113", "sigara",                "",               "",                             "Auto-Correct"),
                new BulguCase("BULGU_114", "dandini",               "",               "",                             "Auto-Correct"),
                new BulguCase("BULGU_119", "hşdra",                 "Hidra",          "",                             "Auto-Correct"),
                new BulguCase("BULGU_119_C","farzet",               "",               "",                             "Auto-Correct"),

                // ─────────────────────────────────────────────────────────────
                // BÖLÜM 8 — ÖNERİ / ARAMA GÖZLEM
                // ─────────────────────────────────────────────────────────────
                new BulguCase("BULGU_019", "yalnızlığın çaresini",  "",               "",                             "Öneri Gözlem"),
                new BulguCase("BULGU_022", "teo",                   "",               "",                             "Öneri Gözlem"),
                new BulguCase("BULGU_085", "remix",                 "",               "",                             "Öneri Gözlem"),
                new BulguCase("BULGU_089", "phonk",                 "",               "",                             "Öneri Gözlem"),

                // ─────────────────────────────────────────────────────────────
                // BÖLÜM 9 — CASE SENSITIVITY
                // ─────────────────────────────────────────────────────────────
                new BulguCase("BULGU_108", "Mihriban",              "",               "Mihriban",                     "Case Sensitivity"),
                new BulguCase("BULGU_109", "mihriban",              "",               "Mihriban",                     "Case Sensitivity")
        );
    }

    // =========================================================================
    // TEST — ASLA FAIL ETMEZ, SADECE VERİ TOPLAR
    // =========================================================================

    @ParameterizedTest(name = "[{0}] \"{1}\"")
    @MethodSource("cases")
    @Order(1)
    void snapshot(BulguCase bc) {
        List<String> top10 = new ArrayList<>();
        int foundAt = 0;

        try {
            Response res = api.search(bc.term(), "active-indices", TOP_N);
            JsonPath jp  = res.jsonPath();
            top10   = extractMuudNames(jp);
            foundAt = findPosition(jp, bc.expArtist(), bc.expTrack());
        } catch (Exception e) {
            System.err.printf("⚠️  API hatası [%s '%s']: %s%n",
                    bc.caseId(), bc.term(), e.getMessage());
        }

        String posStr = foundAt > 0 ? "#" + foundAt
                : (bc.expTrack().isEmpty() && bc.expArtist().isEmpty() ? "N/A" : "YOK");

        System.out.printf("%-15s | %-32s | Konum: %-6s | %s%n",
                bc.caseId(), "\"" + bc.term() + "\"", posStr, bc.section());

        ROWS.add(new BulguSnapshotWriter.SnapshotRow(
                bc.caseId(), bc.term(),
                bc.expArtist(), bc.expTrack(),
                top10, foundAt, bc.section()
        ));
    }

    // =========================================================================
    // YARDIMCI METOTLAR (CompareSearchUtils.extractMuudNames() ile aynı mantık)
    // =========================================================================

    /**
     * Muud active-indices yanıtından top-{TOP_N} display adlarını çıkarır.
     *
     * Format:
     *   Şarkı    → "Şarkı Adı — Sanatçı"
     *   Albüm    → "[Albüm] Albüm Adı — Sanatçı"
     *   Sanatçı  → "[Sanatçı] Sanatçı Adı"
     *   Playlist → "[Playlist] Liste Adı"
     */
    private List<String> extractMuudNames(JsonPath jp) {
        List<String> names = new ArrayList<>();
        String base = jp.get("topHits") != null ? "topHits" : "content";

        for (int i = 0; i < TOP_N * 2 && names.size() < TOP_N; i++) {
            String song      = safeStr(jp.getString(base + "[" + i + "].data.songName"));
            String album     = safeStr(jp.getString(base + "[" + i + "].data.albumName"));
            String playlist  = safeStr(jp.getString(base + "[" + i + "].data.playlistName"));
            String performer = safeStr(jp.getString(base + "[" + i + "].data.performerName"));

            String display;
            if (!song.isEmpty()) {
                display = performer.isEmpty() ? song : song + " — " + performer;
            } else if (!album.isEmpty()) {
                String b = "[Albüm] " + album;
                display = performer.isEmpty() ? b : b + " — " + performer;
            } else if (!playlist.isEmpty()) {
                display = "[Playlist] " + playlist;
            } else if (!performer.isEmpty()) {
                display = "[Sanatçı] " + performer;
            } else {
                break; // sonuç bitti
            }
            names.add(display);
        }
        return names;
    }

    /**
     * Beklenen (artist + track) kombinasyonunun top-{TOP_N}'deki 1-tabanlı konumunu döndürür.
     * Her ikisi de boşsa 0 döner (gözlem case'i).
     * Bulunamazsa 0 döner.
     */
    private int findPosition(JsonPath jp, String expArtist, String expTrack) {
        if (expTrack.isEmpty() && expArtist.isEmpty()) return 0;

        String base  = jp.get("topHits") != null ? "topHits" : "content";
        String expTl = expTrack.toLowerCase(TR);
        String expAl = expArtist.toLowerCase(TR);

        for (int i = 0; i < TOP_N; i++) {
            String song      = safeStr(jp.getString(base + "[" + i + "].data.songName"));
            String album     = safeStr(jp.getString(base + "[" + i + "].data.albumName"));
            String performer = safeStr(jp.getString(base + "[" + i + "].data.performerName"));

            // Şarkı adı yoksa albüm adını kullan (albüm ezme bug'larını da yakalar)
            String trackName = song.isEmpty() ? album : song;
            String tl = trackName.toLowerCase(TR);
            String pl = performer.toLowerCase(TR);

            boolean trackOk  = expTl.isEmpty() || tl.contains(expTl);
            boolean artistOk = expAl.isEmpty() || pl.contains(expAl);

            if (trackOk && artistOk && !trackName.isEmpty()) {
                return i + 1; // 1-tabanlı konum
            }
        }
        return 0; // top-10'da bulunamadı
    }

    private static String safeStr(String s) { return s == null ? "" : s; }
}