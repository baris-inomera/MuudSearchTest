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
 *  Her case için:
 *    • Kural otomatik türetilir (FIRST_ARTIST_IS / TOPN_HAS_ARTIST_AND_TRACK
 *                                / TOPN_RELATED_PLAYLIST)
 *    • Sonuç değerlendirilir: OK veya NOK
 *    • NOK ise — neden başarısız / 1. sırada ne görüldü — raporlanır
 *
 *  Test HİÇBİR ZAMAN fail etmez — saf gözlem & kapsayıcı rapor üretir.
 *
 *  Kullanım:
 *    mvn test -Dtest=BulguFinal2
 *    Çıktı: proje kök dizininde TestReport_YYYYMMDD_HHmmss.xlsx
 * ─────────────────────────────────────────────────────────────────────────────
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MainCases extends TestConfig {

    private static final int    TOP_N = 10;
    private static final Locale TR    = Locale.forLanguageTag("tr");

    // ── Bölüm sabitleri ──────────────────────────────────────────────────────
    private static final String S_SARKI_TAM    = "Şarkı · Tam Eşleşme";
    private static final String S_SARKI_KISMI  = "Şarkı · Kısmi Ad";
    private static final String S_SARKI_SANAT  = "Şarkı · Sanatçı + Şarkı";
    private static final String S_SARKI_YAZIM  = "Şarkı · Yazım Toleransı";
    private static final String S_SARKI_LYRICS = "Şarkı · Lyrics";
    private static final String S_SANAT_TAM    = "Sanatçı · Tam Eşleşme";
    private static final String S_SANAT_KISMI  = "Sanatçı · Kısmi Ad";
    private static final String S_SANAT_YAZIM  = "Sanatçı · Yazım Toleransı";
    private static final String S_SANAT_ALIAS  = "Sanatçı · Kısaltma / Alias";
    private static final String S_PLAYLIST     = "Playlist";

    private static final List<TestResultRow> ROWS = new ArrayList<>();
    private static MuudSearchApi api;

    // =========================================================================
    // SETUP / TEARDOWN
    // =========================================================================

    @BeforeAll
    static void init() {
        api = new MuudSearchApi();
        System.out.println("✅ BulguFinal2 başlatıldı — top-" + TOP_N + " değerlendirilecek.");
    }

    @AfterAll
    static void writeReport() {
        System.out.printf("%n📋 Toplam %d case işlendi.%n", ROWS.size());
        ExcelTestReportWriter.writeBulgu(ROWS);
    }

    // =========================================================================
    // CASE TANIMLAMASI — BulguSnapshotV2Detail'den entegre edildi
    // =========================================================================

    record BulguCase(String caseId, String term, String expArtist, String expTrack, String section, int topN) {
        BulguCase(String caseId, String term, String expArtist, String expTrack, String section) {
            this(caseId, term, expArtist, expTrack, section, 10);
        }
    }

    static Stream<BulguCase> cases() {
        return Stream.of(

                // ═════════════════════════════════════════════════════════════════
                // ŞARKİ · TAM EŞLEŞMe
                // Kullanıcı şarkı adını birebir yazmış (case-insensitive)
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("BULGU_002", "a canım",                        "Mabel Matiz",          "A Canım",                        S_SARKI_TAM, 3),
                new BulguCase("BULGU_004", "olmazlara vuruluyorum",          "Mert Demir",           "Olmazlara Vuruluyorum",          S_SARKI_TAM, 3),
                new BulguCase("BULGU_005", "çıkmaz bir sokakta",             "",                     "Çıkmaz Bir Sokakta",             S_SARKI_TAM, 3),
                new BulguCase("BULGU_007", "kusura bakma",                   "Blok3",                "Kusura Bakma",                   S_SARKI_TAM, 3),
                new BulguCase("BULGU_010", "maraton",                        "Ati242",               "Maraton",                        S_SARKI_TAM, 3),

                new BulguCase("BULGU_011", "geri ver",                       "Wegh",                 "Geri Ver",                       S_SARKI_TAM, 1),
                new BulguCase("BULGU_012", "saygımdan",                      "Bengü",                "Saygımdan",                      S_SARKI_TAM, 1),
                new BulguCase("BULGU_013", "meğerse",                        "Linet",                "Meğerse",                        S_SARKI_TAM, 3),
                new BulguCase("BULGU_015", "dacia",                          "Lvbel C5",             "DACIA",                          S_SARKI_TAM, 3),
                new BulguCase("BULGU_020", "yalnızlığın çaresini bulmuşlar", "",                     "Yalnızlığın Çaresini Bulmuşlar", S_SARKI_TAM, 3),
                new BulguCase("BULGU_023", "yapar mısın",                    "Poizi",                "YAPAR MISIN?",                   S_SARKI_TAM, 1),
                new BulguCase("BULGU_025", "yerinde dur",                    "Sefo",                 "Yerinde Dur",                    S_SARKI_TAM, 1),
                new BulguCase("BULGU_028", "ey aşk",                         "Sezen Aksu",           "Ey Aşk",                         S_SARKI_TAM, 1),
                new BulguCase("BULGU_030", "şımarık",                        "Tarkan",               "Şımarık",                        S_SARKI_TAM, 1),
                new BulguCase("BULGU_031", "giderim kırağınan",              "Onur Şan",             "Giderim Kırağınan",              S_SARKI_TAM, 3),
                new BulguCase("BULGU_033", "ara beni lütfen",                "Kenan Doğulu",         "Ara Beni Lütfen",                S_SARKI_TAM, 1),
                new BulguCase("BULGU_035", "aşk yok olmaktır",               "",                     "Aşk Yok Olmaktır",               S_SARKI_TAM, 1),
                new BulguCase("BULGU_036", "can efendim",                    "",                     "Can Efendim",                    S_SARKI_TAM, 3),
                new BulguCase("BULGU_038", "çıt çıt çedene",                 "Barış Manço",          "Çıt Çıt Çedene",                 S_SARKI_TAM, 3),
                new BulguCase("BULGU_039", "çıkar biri karşıma",             "Poizi",                "Çıkar Biri Karşıma",             S_SARKI_TAM, 3),
                new BulguCase("BULGU_042", "sen yanlış yaptın",              "Şahin Kendirci",       "Sen Yanlış Yaptın",              S_SARKI_TAM, 3),
                new BulguCase("BULGU_043", "vay dayı",                       "Aynur Polat",          "Vay Dayı",                       S_SARKI_TAM, 3),
                new BulguCase("BULGU_044", "silinmez",                       "Mansur Ark",           "Silinmez",                       S_SARKI_TAM, 1),
                new BulguCase("BULGU_045", "halbuki",                        "Yalın",                "Halbuki",                        S_SARKI_TAM, 3),
                new BulguCase("BULGU_047", "duydun mu",                      "Yusuf Güney",          "Duydun Mu?",                     S_SARKI_TAM, 1),
                new BulguCase("BULGU_048", "sana güvenmiyorum",              "Dedublüman",           "Sana Güvenmiyorum",              S_SARKI_TAM, 1),
                new BulguCase("BULGU_049", "yasemen",                        "Afra",                 "Yasemen",                        S_SARKI_TAM, 1),
                new BulguCase("BULGU_050", "düşer o",                        "İzel",                 "Düşer O",                        S_SARKI_TAM, 3),
                new BulguCase("BULGU_051", "kömür",                          "Mabel Matiz",          "Kömür",                          S_SARKI_TAM, 1),
                new BulguCase("BULGU_054", "just the way you are",           "",                     "Just The Way You Are",           S_SARKI_TAM, 3),
                new BulguCase("BULGU_056", "snap",                           "Manifest",             "Snap",                           S_SARKI_TAM, 3),
                new BulguCase("BULGU_059", "ama başaramadım",                "Burak Bulut",          "Ama Başaramadım",                S_SARKI_TAM, 1),
                new BulguCase("BULGU_061", "adına bir çizik çektim",         "",                     "Adına Bir Çizik Çektim",         S_SARKI_TAM, 1),
                new BulguCase("BULGU_062", "yaramızda kalsın",               "Merve Özbey",          "Yaramızda Kalsın",               S_SARKI_TAM, 1),
                new BulguCase("BULGU_063", "sev yeter",                      "",                     "Sev Yeter",                      S_SARKI_TAM, 3),
                new BulguCase("BULGU_065", "kaybolurum gülüşünde",           "İkilem",               "Kaybolurum Gülüşünde",           S_SARKI_TAM, 1),
                new BulguCase("BULGU_071", "ağlama ben ağlarım",             "Canozan",              "Ağlama Ben Ağlarım",             S_SARKI_TAM, 1),
                new BulguCase("BULGU_080", "şikayetim var",                  "",                     "Şikayetim Var",                  S_SARKI_TAM, 3),
                new BulguCase("BULGU_081", "bunca yıl",                      "Dedublüman",           "Bunca Yıl",                      S_SARKI_TAM, 3),
                new BulguCase("BULGU_082", "düldül",                         "Mabel Matiz",          "Düldül",                         S_SARKI_TAM, 3),
                new BulguCase("BULGU_086", "perde",                          "Poizi",                "Perde",                          S_SARKI_TAM, 1),
                new BulguCase("BULGU_089", "phonk",                          "DEHA INC.",            "Phonk",                          S_SARKI_TAM, 3),
                new BulguCase("BULGU_092", "sonbahar",                       "Era7Capone",           "SONBAHAR",                       S_SARKI_TAM, 3),
                new BulguCase("BULGU_093", "acem kızı",                      "",                     "Acem Kızı",                      S_SARKI_TAM, 3),
                new BulguCase("BULGU_094", "hacel obası",                    "",                     "Hacel Obası",                    S_SARKI_TAM, 3),
                new BulguCase("BULGU_095", "yalan",                          "",                     "Yalan",                          S_SARKI_TAM, 1),
                new BulguCase("BULGU_096", "bana sor",                       "Ferdi Tayfur",         "Bana Sor",                       S_SARKI_TAM, 3),
                new BulguCase("BULGU_098", "rüya",                           "Manifest",             "Rüya",                           S_SARKI_TAM, 3),
                new BulguCase("BULGU_100", "ara",                            "Zeynep Bastık",        "Ara",                            S_SARKI_TAM, 1),
                new BulguCase("BULGU_101", "14 bahar",                       "Mert Demir",           "14 Bahar",                       S_SARKI_TAM, 3),
                new BulguCase("BULGU_103", "ela mana",                       "",                     "Ela Mana",                       S_SARKI_TAM, 1),
                new BulguCase("BULGU_104", "yekten",                         "Demet Akalın",         "Yekten",                         S_SARKI_TAM, 3),
                new BulguCase("BULGU_105", "erik dalı",                      "",                     "Erik Dalı",                      S_SARKI_TAM, 3),
                new BulguCase("BULGU_106", "elfida",                         "Haluk Levent",                     "Elfida",                         S_SARKI_TAM, 1),
                new BulguCase("BULGU_107", "yazan kalem siyah",              "",                     "Yazan Kalem Siyah",              S_SARKI_TAM, 3),
                new BulguCase("BULGU_108", "Mihriban",                       "",                     "Mihriban",                       S_SARKI_TAM, 3),
                new BulguCase("BULGU_111", "merdo",                          "",                     "Merdo",                          S_SARKI_TAM, 3),
                new BulguCase("BULGU_113", "sigara",                         "",                     "Sigara",                         S_SARKI_TAM, 3),
                new BulguCase("BULGU_119_C","farzet",                        "İlyas Yalçıntaş",      "Farzet",                         S_SARKI_TAM, 3),
                new BulguCase("BULGU_121", "misket",                         "",                     "Misket",                         S_SARKI_TAM, 1),
                new BulguCase("BULGU_122", "kara sevda",                     "",                     "Kara Sevda",                     S_SARKI_TAM, 3),
                new BulguCase("BULGU_123", "parla",                          "",                     "Parla",                          S_SARKI_TAM, 3),
                new BulguCase("BULGU_124", "kırmızı balık",                  "",                     "Kırmızı Balık",                  S_SARKI_TAM, 3),
                // UAT
                new BulguCase("UAT_011",  "nasır",                           "Melike Şahin",         "Nasır",                          S_SARKI_TAM, 1),
                new BulguCase("UAT_019",  "bodrum",                          "Yüzyüzeyken Konuşuruz","Bodrum",                          S_SARKI_TAM, 1),
                new BulguCase("UAT_126",  "gözlerime bak",                   "Mert Demir",           "Gözlerime Bak",                  S_SARKI_TAM, 3),

                // ═════════════════════════════════════════════════════════════════
                // ŞARKİ · KISMİ AD
                // Kullanıcı şarkı adının yalnızca başını veya bir bölümünü yazmış
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("BULGU_001", "Hermes",                         "Batuflex",             "Hermès",                     S_SARKI_KISMI, 1),
                new BulguCase("BULGU_019", "yalnızlığın çaresini",           "gripin",               "Yalnızlığın Çaresini Bulmuşlar", S_SARKI_KISMI, 3),
                new BulguCase("BULGU_024", "yerinde",                        "Sefo",                 "Yerinde Dur",                    S_SARKI_KISMI, 1),
                new BulguCase("BULGU_034", "vidrado em",                     "Dj Guuga",             "Vidrado Em Você",                S_SARKI_KISMI, 3),
                new BulguCase("BULGU_037", "çıt çıt",                        "Barış Manço",          "Çıt Çıt Çedene",                 S_SARKI_KISMI, 3),
                new BulguCase("BULGU_072", "ağlama ben",                     "Canozan",              "Ağlama Ben Ağlarım",             S_SARKI_KISMI, 1),
                new BulguCase("BULGU_074", "erik",                           "",                     "Erik Dalı",                      S_SARKI_KISMI, 1),
                new BulguCase("BULGU_078", "doğuştan",                       "Lvbel C5",             "Doğuştan Beri Haklıyım",         S_SARKI_KISMI, 3),
                new BulguCase("BULGU_114", "dandini",                        "Ninni Bebek",          "Dandini Dandini Dastana",        S_SARKI_KISMI, 3),
                // UAT
                new BulguCase("UAT_017",  "sus",                             "Ceza",                 "Suspus",                         S_SARKI_KISMI, 1),
                new BulguCase("UAT_018",  "pus",                             "Ceza",                 "Suspus",                         S_SARKI_KISMI, 5),
                new BulguCase("UAT_141",  "kalbimin sahibine",               "İrem Derici",          "Kalbimin Tek Sahibine",          S_SARKI_KISMI, 3),

                // ═════════════════════════════════════════════════════════════════
                // ŞARKİ · SANATÇI + ŞARKİ
                // Kullanıcı sanatçı adı + şarkı adını birlikte yazmış
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("BULGU_041", "messy lola young",               "Lola Young",           "Messy",                          S_SARKI_SANAT, 3),
                new BulguCase("BULGU_052", "mabel kömür",                    "Mabel Matiz",          "Kömür",                          S_SARKI_SANAT, 1),
                new BulguCase("BULGU_058", "y poizi",                        "Poizi",                "Y",                              S_SARKI_SANAT, 3),
                new BulguCase("BULGU_060", "kts manifest",                   "Manifest",             "KTS",                            S_SARKI_SANAT, 1),
                new BulguCase("BULGU_070", "Dua Lipa Shine",                 "Cédric",             "Shine",                          S_SARKI_SANAT, 3),
                new BulguCase("BULGU_097", "rüya manifest",                  "Manifest",             "Rüya",                           S_SARKI_SANAT, 1),

                // ═════════════════════════════════════════════════════════════════
                // ŞARKİ · YAZIM TOLERANSI
                // Yazım yanlışı / eksik/yanlış karakter / Türkçe karakter eksikliği /
                // bitişik yazım / kelime sırası / çekim eki farklılığı
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("BULGU_003", "acanım",                         "Mabel Matiz",          "A Canım",                        S_SARKI_YAZIM, 3),
                new BulguCase("BULGU_014", "çok pardon",                     "Lvbel C5",             "COOOK PARDON",                   S_SARKI_YAZIM, 3),
                new BulguCase("BULGU_029", "simarik",                        "Tarkan",               "Şımarık",                        S_SARKI_YAZIM, 1),
                new BulguCase("BULGU_032", "dame un grr",                    "Fantomel",             "Dame Un Grrr",                   S_SARKI_YAZIM, 3),
                new BulguCase("BULGU_040", "hav hav",                        "Lvbel C5",             "Havhavhav",                      S_SARKI_YAZIM, 3),
                new BulguCase("BULGU_077", "karakedi",                       "Melis Fis",            "Kara Kedi",                      S_SARKI_YAZIM, 3),
                // UAT
                new BulguCase("UAT_054",  "illede sen",                      "Azer Bülbül",          "İlle De Sen",                    S_SARKI_YAZIM, 1),
                new BulguCase("UAT_068",  "arabam",                          "Sefo",                 "Araba",                          S_SARKI_YAZIM, 3),
                new BulguCase("UAT_145",  "lacivert eceler",                 "Ferhat Göçer",         "Lacivert Geceler",               S_SARKI_YAZIM, 3),
                new BulguCase("UAT_148",  "güldün ne güzel",                 "Pinhani",              "Ne Güzel Güldün",                S_SARKI_YAZIM, 3),

                // ═════════════════════════════════════════════════════════════════
                // ŞARKİ · LYRİCS
                // Kullanıcı şarkı sözü parçasıyla arama yapmış
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("BULGU_067", "çölüme yağmur oldun",            "Müslüm Gürses",        "Affet",                          S_SARKI_LYRICS, 3),
                new BulguCase("BULGU_075", "hadi ya",                        "Melis Kar",            "Yatıya",                         S_SARKI_LYRICS, 3),
                new BulguCase("BULGU_076", "babalar",                        "Blok3",                "PATLAT",                         S_SARKI_LYRICS, 1),
                new BulguCase("BULGU_083", "çetin ceviz şerbetli mayam",     "Melike Şahin",         "Canın Beni Çekti",               S_SARKI_LYRICS, 3),
                new BulguCase("BULGU_084", "bir motive",                     "Motive",               "bir",                            S_SARKI_LYRICS, 3),
                // UAT
                new BulguCase("UAT_015",  "yandım ah",                       "Sakiler",              "Yalanı Bırak",                   S_SARKI_LYRICS, 1),
                new BulguCase("UAT_022",  "bak ben yara gibiyim",            "Emir Can İğrek",       "Nalan",                          S_SARKI_LYRICS, 1),
                new BulguCase("UAT_024",  "zaten aşklar hep yalan dolan",    "Yıldız Tilbe",         "Sana Değer",                     S_SARKI_LYRICS, 1),
                new BulguCase("UAT_034",  "sana hastayım anlasana",          "Derya Uluğ",           "Yansıma",                        S_SARKI_LYRICS, 1),
                new BulguCase("UAT_128",  "sarışınlar",                      "Derya Uluğ",           "Esmerin Adı Oya",                S_SARKI_LYRICS, 3),
                new BulguCase("UAT_136",  "silemez o beni",                  "Yıldız Tilbe",         "Dizine Dursun",                  S_SARKI_LYRICS, 1),
                new BulguCase("UAT_137",  "babalar sözünü tutar",            "Blok3",                "PATLAT",                         S_SARKI_LYRICS, 3),
                new BulguCase("UAT_143",  "çok geç şmdi",                    "Edis",                 "Yalan",                          S_SARKI_LYRICS, 3),
                new BulguCase("UAT_144",  "affet bu gece istedim ölmek",     "Model",                "Pembe Mezarlık",                 S_SARKI_LYRICS, 3),

                // ═════════════════════════════════════════════════════════════════
                // SANATÇI · TAM EŞLEŞMe
                // Kullanıcı sanatçı adını birebir yazmış
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("BULGU_008", "mfö",                            "MFÖ",                  "",                               S_SANAT_TAM, 1),
                new BulguCase("BULGU_0088", "mfo",                            "MFÖ",                  "",                               S_SANAT_TAM, 1),
                new BulguCase("BULGU_016", "Manifest",                       "Manifest",             "",                               S_SANAT_TAM, 1),
                new BulguCase("BULGU_017", "semicenk",                       "Semicenk",             "",                               S_SANAT_TAM, 1),
                new BulguCase("BULGU_055", "utku akkaya",                    "Utku Akkaya",          "",                               S_SANAT_TAM, 1),
                new BulguCase("BULGU_088", "derya bedavacı",                  "Derya Bedavacı",       "",                               S_SANAT_TAM, 1),
                new BulguCase("BULGU_090", "ceza",                           "Ceza",                 "",                               S_SANAT_TAM, 3),
                new BulguCase("BULGU_102", "yaşar",                          "Yaşar",                "",                               S_SANAT_TAM, 1),
                new BulguCase("BULGU_112", "Gökhan Özen",                    "Gökhan Özen",          "",                               S_SANAT_TAM, 1),
                new BulguCase("BULGU_118", "çelik",                          "Çelik",                "",                               S_SANAT_TAM, 1),
                new BulguCase("BULGU_119_B","Haluk Levent",                  "Haluk Levent",         "",                               S_SANAT_TAM, 1),
                new BulguCase("BULGU_120", "Mustafa Yıldızdoğan",            "Mustafa Yıldızdoğan",  "",                               S_SANAT_TAM, 1),
                // UAT
                new BulguCase("UAT_001",  "u2",                              "u2",                   "",                               S_SANAT_TAM, 1),
                new BulguCase("UAT_010",  "edis",                            "Edis",                 "",                               S_SANAT_TAM, 1),
                new BulguCase("UAT_013",  "uzi",                             "UZI",                  "",                               S_SANAT_TAM, 1),
                new BulguCase("UAT_030",  "sıla",                            "Sıla",                 "",                               S_SANAT_TAM, 1),
                new BulguCase("UAT_045",  "eminem",                          "Eminem",               "",                               S_SANAT_TAM, 1),
                new BulguCase("UAT_051",  "güneş",                           "Güneş",                "",                               S_SANAT_TAM, 1),
                new BulguCase("UAT_052",  "dua lipa",                        "Dua Lipa",             "",                               S_SANAT_TAM, 1),
                new BulguCase("UAT_059",  "mero",                            "Mero",                 "",                               S_SANAT_TAM, 3),
                new BulguCase("UAT_061",  "murda",                           "Murda",                "",                               S_SANAT_TAM, 3),
                new BulguCase("UAT_064",  "inna",                            "Inna",                 "",                               S_SANAT_TAM, 3),
                new BulguCase("UAT_067",  "adele",                           "Adele",                "",                               S_SANAT_TAM, 3),
                new BulguCase("UAT_070",  "mor ve ötesi",                    "mor ve ötesi",          "",                               S_SANAT_TAM, 3),
                new BulguCase("UAT_075",  "patron",                          "Patron",               "",                               S_SANAT_TAM, 3),
                new BulguCase("UAT_076",  "tefo",                            "Tefo",                 "",                               S_SANAT_TAM, 3),
                new BulguCase("UAT_077",  "doğuş",                           "Doğuş",                "",                               S_SANAT_TAM, 3),
                new BulguCase("UAT_084",  "ben fero",                        "Ben Fero",             "",                               S_SANAT_TAM, 3),
                new BulguCase("UAT_086",  "inji",                            "INJI",                 "",                               S_SANAT_TAM, 3),
                new BulguCase("UAT_087",  "rihanna",                         "Rihanna",              "",                               S_SANAT_TAM, 3),
                new BulguCase("UAT_089",  "mavi",                            "Mavi",                 "",                               S_SANAT_TAM, 3),
                new BulguCase("UAT_090",  "velet",                           "Velet",                "",                               S_SANAT_TAM, 3),
                new BulguCase("UAT_091",  "adamlar",                         "Adamlar",              "",                               S_SANAT_TAM, 3),
                new BulguCase("UAT_102",  "blackpink",                       "BLACKPINK",            "",                               S_SANAT_TAM, 3),
                new BulguCase("UAT_104",  "sia",                             "Sia",                  "",                               S_SANAT_TAM, 3),
                new BulguCase("UAT_110",  "shakira",                         "Shakira",              "",                               S_SANAT_TAM, 3),
                new BulguCase("UAT_139",  "madonna",                         "Madonna",              "",                               S_SANAT_TAM, 3),

                // ═════════════════════════════════════════════════════════════════
                // SANATÇI · KISMİ AD
                // Kullanıcı sanatçının adını / soyadını / ilk kelimesini yazmış
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("BULGU_006", "blok",                           "Blok3",                "",                               S_SANAT_KISMI, 1),
                new BulguCase("BULGU_022", "teo",                            "Teoman",               "",                               S_SANAT_KISMI, 1),

                // UAT
                new BulguCase("UAT_016",  "aleyna",                          "Aleyna Tilki",         "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_025",  "ferdi",                           "Ferdi Tayfur",         "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_026",  "mabel",                           "Mabel Matiz",          "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_027",  "yıldız",                          "Yıldız Tilbe",         "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_028",  "azer",                            "Azer Bülbül",          "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_033",  "serdar",                          "Serdar Ortaç",         "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_036",  "cengiz",                          "Cengiz Kurtoğlu",      "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_038",  "neşet",                           "Neşet Ertaş",          "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_039",  "melike",                          "Melike Şahin",         "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_042",  "orhan",                           "Orhan Gencebay",       "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_049",  "soner",                           "Soner Sarıkabadayı",   "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_050",  "ati",                             "Ati242",               "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_053",  "norm",                            "Norm Ender",           "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_055",  "sibel",                           "Sibel Can",            "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_056",  "irem",                            "İrem Derici",          "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_058",  "musa",                            "Musa Eroğlu",          "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_062",  "kurtuluş",                        "Kurtuluş Kuş",         "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_063",  "cash",                            "Cash Flow",            "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_071",  "reyn",                            "Reynmen",              "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_072",  "mahsun",                          "Mahsun Kırmızıgül",    "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_078",  "funda",                           "Funda Arar",           "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_080",  "sura",                            "Sura İskenderli",      "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_081",  "rafet",                           "Rafet El Roman",       "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_085",  "haluk",                           "Haluk Levent",         "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_088",  "lvbel",                           "Lvbel C5",             "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_092",  "zerrin",                          "Zerrin Özer",          "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_093",  "selda",                           "Selda Bağcan",         "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_094",  "bilal",                           "Bilal Sonses",         "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_096",  "gülden",                          "Gülden Karaböcek",     "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_097",  "ibrahim tat",                     "İbrahim Tatlıses",     "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_098",  "engin",                           "Engin Nurşani",        "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_099",  "şebnem",                          "Şebnem Ferah",         "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_100",  "ayaz",                            "Ayaz Erdoğan",         "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_101",  "ajda",                            "Ajda Pekkan",          "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_107",  "aynur",                           "Aynur Aydın",          "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_109",  "hayko",                           "Hayko Cepkin",         "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_113",  "koray",                           "Koray Avcı",           "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_114",  "ümit",                            "Ümit Besen",           "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_115",  "elif buse",                       "Elif Buse Doğan",      "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_123",  "özcan",                           "Özcan Deniz",          "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_125",  "deha",                            "DEHA INC.",            "",                               S_SANAT_KISMI, 1),
                new BulguCase("UAT_129",  "taylor",                          "Taylor Swift",         "",                               S_SANAT_KISMI, 1),

                // ═════════════════════════════════════════════════════════════════
                // SANATÇI · YAZIM TOLERANSI
                // Yazım yanlışı / fonetik benzerlik / Türkçe karakter eksikliği /
                // bitişik yazım / kelime sırası farklılığı (sanatçı araması)
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("BULGU_021", "tarkn",                          "Tarkan",               "",                               S_SANAT_YAZIM, 1),
                new BulguCase("BULGU_053", "kök$l",                          "kök$vl",               "",                               S_SANAT_YAZIM, 3),
                new BulguCase("BULGU_099", "çakal",                          "cakal",                "",                               S_SANAT_YAZIM, 1),
                new BulguCase("BULGU_115", "pozi",                           "Poizi",                "",                               S_SANAT_YAZIM, 1),
                new BulguCase("BULGU_119", "hşdra",                          "Hidra",                "",                               S_SANAT_YAZIM, 3),
                // UAT
                new BulguCase("UAT_003",  "goksel",                          "Göksel",               "",                               S_SANAT_YAZIM, 1),
                new BulguCase("UAT_014",  "can ozan",                        "Canozan",              "",                               S_SANAT_YAZIM, 1),
                new BulguCase("UAT_020",  "ezel",                            "Ezhel",                "",                               S_SANAT_YAZIM, 1),
                new BulguCase("UAT_035",  "reymen",                          "Reynmen",              "",                               S_SANAT_YAZIM, 3),
                new BulguCase("UAT_047",  "emircan",                         "Emir Can İğrek",       "",                               S_SANAT_YAZIM, 1),
                new BulguCase("UAT_074",  "emircan iğrek",                   "Emir Can İğrek",       "",                               S_SANAT_YAZIM, 3),
                new BulguCase("UAT_105",  "izel",                            "İzel",                 "",                               S_SANAT_YAZIM, 3),
                new BulguCase("UAT_118",  "hejan",                           "Heijan",               "",                               S_SANAT_YAZIM, 3),
                new BulguCase("UAT_127",  "Semicek",                         "Semicenk",             "",                               S_SANAT_YAZIM, 1),
                new BulguCase("UAT_130",  "beyonce",                         "Beyoncé",              "",                               S_SANAT_YAZIM, 3),
                new BulguCase("UAT_131",  "emre gel",                        "Emre Fel",             "",                               S_SANAT_YAZIM, 3),
                new BulguCase("UAT_133",  "sibelcan",                        "Sibel Can",            "",                               S_SANAT_YAZIM, 3),
                new BulguCase("UAT_135",  "kofn",                            "KÖFN",                 "",                               S_SANAT_YAZIM, 3),
                new BulguCase("UAT_142",  "sertap",                          "Sertab Erener",        "",                               S_SANAT_YAZIM, 3),
                new BulguCase("UAT_146",  "mr ve ötei",                      "Mor ve Ötesi",         "",                               S_SANAT_YAZIM, 3),
                new BulguCase("UAT_147",  "dolu kadhi tut",                  "Dolu Kadehi Ters Tut", "",                               S_SANAT_YAZIM, 3),

                // ═════════════════════════════════════════════════════════════════
                // SANATÇI · KISALTMA / ALİAS
                // Resmi ismin kısaltması, rumuz, sayısal alias, içiçe yazım
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("BULGU_110", "lvc5",                           "Lvbel C5",             "",                               S_SANAT_ALIAS, 3),
                new BulguCase("BULGU_116", "level c5",                       "Lvbel C5",             "",                               S_SANAT_ALIAS, 3),
                new BulguCase("BULGU_117", "levelc5",                        "Lvbel C5",             "",                               S_SANAT_ALIAS, 3),
                // UAT
                new BulguCase("UAT_002",  "84",                              "seksendört",           "",                               S_SANAT_ALIAS, 1),
                new BulguCase("UAT_046",  "sago",                            "Sagopa Kajmer",        "",                               S_SANAT_ALIAS, 1),
                new BulguCase("UAT_103",  "no1",                             "No.1",                 "",                               S_SANAT_ALIAS, 3),
                new BulguCase("UAT_111",  "halo",                            "Halodayı",             "",                               S_SANAT_ALIAS, 3),
                new BulguCase("UAT_112",  "50",                              "50 Cent",              "",                               S_SANAT_ALIAS, 3),
                new BulguCase("UAT_120",  "no 1",                            "No.1",                 "",                               S_SANAT_ALIAS, 3),
                new BulguCase("UAT_149",  "dktt",                            "Dolu Kadehi Ters Tut", "",                               S_SANAT_ALIAS, 3),

                // ═════════════════════════════════════════════════════════════════
                // PLAYLİST
                // Kullanıcı kategori / tür adıyla çalma listesi arıyor
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("BULGU_087", "akustik",                        "",  "[Playlist] akustik",      S_PLAYLIST, 5),

                // UAT
                new BulguCase("BULGU_064", "pop",                            "",  "[Playlist] pop",          S_PLAYLIST, 5),
                new BulguCase("UAT_005",  "90",               "",  "[Playlist] 90",           S_PLAYLIST, 5),
                new BulguCase("UAT_006",  "90lar",            "",  "[Playlist] 90",           S_PLAYLIST, 5),
                new BulguCase("UAT_007",  "90'lar",           "",  "[Playlist] 90",           S_PLAYLIST, 5),
                new BulguCase("UAT_008",  "90s",              "",  "[Playlist] 90",           S_PLAYLIST, 5),
                new BulguCase("UAT_009",  "90 lar",           "",  "[Playlist] 90",           S_PLAYLIST, 5),
                new BulguCase("UAT_029",  "arabesk",          "",  "[Playlist] arabesk",      S_PLAYLIST, 5),
                new BulguCase("UAT_031",  "ilahi",            "",  "[Playlist] ilahi",        S_PLAYLIST, 5),
                new BulguCase("UAT_037",  "karadeniz",        "",  "[Playlist] karadeniz",    S_PLAYLIST, 5),
                new BulguCase("UAT_040",  "halay",            "",  "[Playlist] halay",        S_PLAYLIST, 5),
                new BulguCase("UAT_043",  "yabancı",          "",  "[Playlist] yabancı",      S_PLAYLIST, 5),
                new BulguCase("UAT_044",  "roman",            "",  "[Playlist] roman",        S_PLAYLIST, 5),
                new BulguCase("UAT_048",  "çocuk",            "",  "[Playlist] çocuk",        S_PLAYLIST, 5),
                new BulguCase("UAT_065",  "oyun hava",        "",  "[Playlist] oyun hava",    S_PLAYLIST, 5),
                new BulguCase("UAT_069",  "spor",             "",  "[Playlist] spor",         S_PLAYLIST, 5),
                new BulguCase("UAT_073",  "klasik",           "",  "[Playlist] klasik",       S_PLAYLIST, 5),
                new BulguCase("UAT_079",  "ankara",           "",  "[Playlist] ankara",       S_PLAYLIST, 5),
                new BulguCase("UAT_083",  "çocuk şarkıları",  "",  "[Playlist] çocuk",        S_PLAYLIST, 5),
                new BulguCase("UAT_106",  "türkçe",           "",  "[Playlist] türkçe",       S_PLAYLIST, 5),
                new BulguCase("UAT_108",  "80",               "",  "[Playlist] 80",           S_PLAYLIST, 5),
                new BulguCase("UAT_116",  "rock",             "",  "[Playlist] rock",         S_PLAYLIST, 5),
                new BulguCase("UAT_121",  "dans",             "",  "[Playlist] dans",         S_PLAYLIST, 5),
                new BulguCase("UAT_122",  "türk sanat",       "",  "[Playlist] türk sanat",   S_PLAYLIST, 5),
                new BulguCase("UAT_134",  "meditasyon",       "",  "[Playlist] meditasyon",   S_PLAYLIST, 5),
                new BulguCase("UAT_138",  "90 lar pop",       "",  "[Playlist] pop",          S_PLAYLIST, 5),
                new BulguCase("UAT_140",  "ramazan",          "",  "[Playlist] ramazan",      S_PLAYLIST, 5),

                // ═════════════════════════════════════════════════════════════════
                // YENİ BULGULAR — 2026-04-22
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("BULGU_125",   "teybi bozuk bir arabayla",                     "Nilipek",   "Geçmiyor Zaman",                      S_SARKI_LYRICS, 3),
                new BulguCase("BULGU_126",   "türk marşı",                                   "Ceza",      "Türk Marşı",                          S_SARKI_TAM,    3),
                new BulguCase("BULGU_127",   "masumiyet müzesi",                             "",          "[Playlist] Masumiyet Müzesi",          S_PLAYLIST,    5),
                new BulguCase("BULGU_128",   "there is a light that never goes out",         "Morrissey", "There Is A Light That Never Goes Out", S_SARKI_TAM,    3),
                new BulguCase("BULGU_128_B", "there is a light that never goes out (live)",  "Morrissey", "There Is A Light That Never Goes Out", S_SARKI_KISMI,  3),

                new BulguCase("CAGKAN_1",   "ölek mi",                                           "",   "Ölek mi?",                      S_SARKI_LYRICS, 3),
                new BulguCase("CAGKAN_2",   "ölek mi?",                                         "",   "Ölek mi?",                      S_SARKI_LYRICS, 3),
                new BulguCase("CAGKAN_3",   "dai",                                              "",   "Dai Dai",                      S_SARKI_LYRICS, 3),
                new BulguCase("CAGKAN_3",   "dia",                                              "",   "Dai Dai",                      S_SARKI_LYRICS, 3),
                new BulguCase("CAGKAN_4",   "Murat yeter magusa",                               "",   "Mağusa Limanı",                      S_SARKI_LYRICS, 3),
                new BulguCase("CAGKAN_5",   "Sen Anlat Geçen Yüzyıl",                           "",   "Sen Anlat Geçen Yüzyıl",                      S_SARKI_LYRICS, 3),
                new BulguCase("CAGKAN_6",   "Sinem Yalçınkaya kenar süsü",                      "",   "Kenar Süsü",                      S_SARKI_LYRICS, 3)

       // new BulguCase("sena_1",   "tarkn",                      "Tarkan",   "",                      S_SARKI_LYRICS, 3),
       // new BulguCase("sena_2",   "sezn aksu",                      "Sezen Aksu",   "",                      S_SARKI_LYRICS, 3),
      //  new BulguCase("sena_3",   "kürtçe",                      "",   "Kürtçe",                      S_SARKI_LYRICS, 3)




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

        System.out.printf("[%s] %-15s | %-38s | %s%n",
                result[1], bc.caseId(), "\"" + bc.term() + "\"", result[2]);

        ROWS.add(new TestResultRow(
                bc.caseId(),
                "\"" + bc.term() + "\" araması yapılır",
                "Arama terimi: '" + bc.term() + "' — Bölüm: " + bc.section(),
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

    /**
     * BulguCase'den kural türetir ve değerlendirme sonucunu döndürür.
     *
     * Dönüş: String[]{ beklenenAçıklama, "OK"/"NOK", detayMesajı }
     *
     * Kural türetme mantığı:
     *   expTrack "[Playlist] ..."  → TOPN_RELATED_PLAYLIST
     *   expArtist dolu, expTrack boş → FIRST_ARTIST_IS
     *   expArtist ve/veya expTrack dolu → TOPN_HAS_ARTIST_AND_TRACK
     */
    private String[] evaluate(BulguCase bc, JsonPath jp) {
        List<Object> list = MuudSearchUtils.resultsList(jp);
        String       base = MuudSearchUtils.getBasePath(jp);

        if (list.isEmpty()) {
            return new String[]{
                    "Arama sonucunda en az 1 kayıt dönmesi beklenir.",
                    "NOK",
                    "API boş sonuç döndürdü."};
        }

        String expTrack  = bc.expTrack();
        String expArtist = bc.expArtist();

        if (expTrack.isEmpty() && expArtist.isEmpty()) {
            return new String[]{"(gözlem)", "OK",
                    "Gözlem case'i — beklenen içerik tanımlanmamış, " + list.size() + " sonuç döndü.\n"
                    + top5Desc(jp, base)};
        }

        if (expTrack.toLowerCase(TR).startsWith("[playlist] ")) {
            return evalPlaylist(bc, jp, base);
        }

        if (!expArtist.isEmpty() && expTrack.isEmpty()) {
            return evalFirstArtist(bc, jp, base);
        }

        return evalArtistAndTrack(bc, jp, base);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FIRST_ARTIST_IS — 1. sırada beklenen sanatçı gelmeli
    // ─────────────────────────────────────────────────────────────────────────

    private String[] evalFirstArtist(BulguCase bc, JsonPath jp, String base) {
        int    n        = bc.topN();
        String expected = n == 1
                ? "1. sırada '" + bc.expArtist() + "' sanatçısı gelmeli."
                : "İlk " + n + " içinde '" + bc.expArtist() + "' sanatçısı gelmeli.";

        int pos = MuudSearchUtils.findArtistIndex(jp, n, bc.expArtist());

        if (pos != -1) {
            String fa = MuudSearchUtils.getPerformerName(jp, base + "[" + pos + "].data");
            return new String[]{expected, "OK",
                    "Başarılı — " + (pos + 1) + ". sırada '" + fa + "' geldi.\n"
                    + top5Desc(jp, base)};
        }

        int    fullPos  = MuudSearchUtils.findArtistIndex(jp, TOP_N, bc.expArtist());
        String where    = fullPos == -1
                ? "top-" + TOP_N + "'da da bulunamadı"
                : (fullPos + 1) + ". sırada bulundu";
        return new String[]{expected, "NOK",
                "İlk " + n + "'da yok — " + bc.expArtist() + ": " + where + ".\n"
                        + top5Desc(jp, base)};
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOPN_HAS_ARTIST_AND_TRACK — Top-N içinde sanatçı+şarkı birlikte bulunmalı
    // ─────────────────────────────────────────────────────────────────────────

    private String[] evalArtistAndTrack(BulguCase bc, JsonPath jp, String base) {
        int    n      = bc.topN();
        String expStr = bc.expArtist().isEmpty()
                ? "'" + bc.expTrack() + "'"
                : "'" + bc.expArtist() + "' – '" + bc.expTrack() + "'";
        String expected = "Top-" + n + " içinde " + expStr + " eşleşmesi bulunmalı.";

        int idx = MuudSearchUtils.findArtistAndTrackIndex(jp, n, bc.expArtist(), bc.expTrack());

        if (idx != -1) {
            String fa = MuudSearchUtils.getPerformerName(jp, base + "[" + idx + "].data");
            String ft = MuudSearchUtils.safeStr(jp.getString(base + "[" + idx + "].data.songName"));
            if (ft.isEmpty())
                ft = MuudSearchUtils.safeStr(jp.getString(base + "[" + idx + "].data.albumName"));
            String label = fa.isEmpty() ? ft : fa + "' – '" + ft;
            return new String[]{expected, "OK",
                    "Başarılı — " + (idx + 1) + ". sırada: '" + label + "'.\n"
                    + top5Desc(jp, base)};
        }

        int    fullIdx  = MuudSearchUtils.findArtistAndTrackIndex(jp, TOP_N, bc.expArtist(), bc.expTrack());
        String where    = fullIdx == -1
                ? "top-" + TOP_N + "'da da bulunamadı"
                : (fullIdx + 1) + ". sırada bulundu";
        return new String[]{expected, "NOK",
                "Top-" + n + "'da yok — " + expStr + ": " + where + ".\n"
                        + top5Desc(jp, base)};
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOPN_RELATED_PLAYLIST — Top-N içinde keyword içeren playlist bulunmalı
    // ─────────────────────────────────────────────────────────────────────────

    private String[] evalPlaylist(BulguCase bc, JsonPath jp, String base) {
        int    n        = bc.topN();
        String keyword  = bc.expTrack().substring("[Playlist] ".length());
        String expected = "Top-" + n + " içinde '" + keyword + "' adını içeren playlist bulunmalı.";

        for (int i = 0; i < n; i++) {
            String pl = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.playlistName"));
            if (!pl.isEmpty() && MuudSearchUtils.containsTRInsensitive(pl, keyword)) {
                return new String[]{expected, "OK",
                        "Başarılı — " + (i + 1) + ". sırada playlist bulundu: '" + pl + "'.\n"
                        + top5Desc(jp, base)};
            }
        }

        int fullPos = -1;
        for (int i = 0; i < TOP_N; i++) {
            String pl = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.playlistName"));
            if (!pl.isEmpty() && MuudSearchUtils.containsTRInsensitive(pl, keyword)) {
                fullPos = i;
                break;
            }
        }
        String where = fullPos == -1
                ? "top-" + TOP_N + "'da da bulunamadı"
                : (fullPos + 1) + ". sırada bulundu";
        return new String[]{expected, "NOK",
                "Top-" + n + "'da yok — '" + keyword + "': " + where + ".\n"
                        + top5Desc(jp, base)};
    }

    // =========================================================================
    // YARDIMCI — Her case için "İlk 5 sonuç" listesi (numPlays,
    //            performerPopularity, popularity, score alt parametreleriyle)
    // =========================================================================

    private String itemDesc(JsonPath jp, String base, int i) {
        String song      = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.songName"));
        String album     = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.albumName"));
        String playlist  = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.playlistName"));
        String performer = MuudSearchUtils.getPerformerName(jp, base + "[" + i + "].data");
        String kind      = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.kind"));

        String label;
        if (!song.isEmpty())
            label = performer.isEmpty() ? "'" + song + "'" : "'" + performer + " – " + song + "'";
        else if (!album.isEmpty())
            label = "[Albüm] '" + album + "'" + (performer.isEmpty() ? "" : " – '" + performer + "'");
        else if (!playlist.isEmpty())
            label = "[Playlist] '" + playlist + "'";
        else if (!performer.isEmpty())
            label = "[Sanatçı] '" + performer + "'";
        else
            label = "(boş)";

        String kindPrefix = kind.isEmpty() ? "" : "[" + kind + "] ";
        Object scoreObj   = jp.get(base + "[" + i + "].score");
        String score      = scoreObj != null ? scoreObj.toString() : "-";

        String createTime = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.createTime"));

        String metrics;
        if ("performers".equals(kind)) {
            Object popularSongCountObj = jp.get(base + "[" + i + "].data.popularSongCount");
            String popularSongCount = popularSongCountObj != null ? popularSongCountObj.toString() : "-";
            metrics = "popularSongCount=" + popularSongCount + " | score=" + score;
        } else {

            Object numPlaysObj   = jp.get(base + "[" + i + "].data.numPlays");
            Object perfPopObj    = jp.get(base + "[" + i + "].data.performerPopularity");
            Object popularityObj = jp.get(base + "[" + i + "].data.popularity");
            String numPlays   = numPlaysObj   != null ? numPlaysObj.toString()   : "-";
            String perfPop    = perfPopObj    != null ? perfPopObj.toString()    : "-";
            String popularity = popularityObj != null ? popularityObj.toString() : "-";
            metrics = " | numPlays=" + numPlays
                    + " | performerPopularity=" + perfPop
                    + " | popularity=" + popularity
                    + " | score=" + score;
        }
        if (!createTime.isEmpty()) {
            metrics += " | createTime=" + createTime;
        }

        return kindPrefix + label + "\n     " + metrics;
    }

    private String top5Desc(JsonPath jp, String base) {
        StringBuilder sb = new StringBuilder("İlk 5 sonuç:");
        for (int i = 0; i < 5; i++) {
            sb.append("\n  ").append(i + 1).append(". ").append(itemDesc(jp, base, i));
        }
        return sb.toString();
    }
}
