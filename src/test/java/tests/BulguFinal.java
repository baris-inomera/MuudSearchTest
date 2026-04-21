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
 *  BULGU FINAL — Birleşik Kapsayıcı Regresyon Testi
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  BulguSnapshotV2'den alınan tüm case'leri MuudSearchApiKapsayiciUATTest
 *  mantığıyla değerlendirir. Her case için:
 *    • Kural otomatik türetilir (FIRST_ARTIST_IS / TOPN_HAS_ARTIST_AND_TRACK
 *                                / TOPN_RELATED_PLAYLIST)
 *    • Sonuç değerlendirilir: OK veya NOK
 *    • NOK ise — neden başarısız / 1. sırada ne görüldü — raporlanır
 *
 *  Test HİÇBİR ZAMAN fail etmez — saf gözlem & kapsayıcı rapor üretir.
 *
 *  Kullanım:
 *    mvn test -Dtest=BulguFinal
 *    Çıktı: proje kök dizininde TestReport_YYYYMMDD_HHmmss.xlsx
 * ─────────────────────────────────────────────────────────────────────────────
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BulguFinal extends TestConfig {

    private static final int    TOP_N = 10;
    private static final Locale TR    = Locale.forLanguageTag("tr");

    private static final List<TestResultRow> ROWS = new ArrayList<>();
    private static MuudSearchApi api;

    // =========================================================================
    // SETUP / TEARDOWN
    // =========================================================================

    @BeforeAll
    static void init() {
        api = new MuudSearchApi();
        System.out.println("✅ BulguFinal başlatıldı — top-" + TOP_N + " değerlendirilecek.");
    }

    @AfterAll
    static void writeReport() {
        System.out.printf("%n📋 Toplam %d case işlendi.%n", ROWS.size());
        ExcelTestReportWriter.write(ROWS);
    }

    // =========================================================================
    // CASE TANIMLAMASI — BulguSnapshotV2'den entegre edildi
    // =========================================================================

    record BulguCase(String caseId, String term, String expArtist, String expTrack, String section) {}

    static Stream<BulguCase> cases() {
        return Stream.of(

                // ─────────────────────────────────────────────────────────────
                // ŞARKI — Kullanıcı şarkı adıyla arama yapıyor
                // ─────────────────────────────────────────────────────────────
                new BulguCase("BULGU_001", "Hermes",                        "Batuflex",          "Hermès 2.0",                    "Şarkı"),
                new BulguCase("BULGU_002", "a canım",                       "Mabel Matiz",       "A Canım",                       "Şarkı"),
                new BulguCase("BULGU_003", "acanım",                        "Mabel Matiz",       "A Canım",                       "Şarkı"),
                new BulguCase("BULGU_004", "olmazlara vuruluyorum",         "Mert Demir",        "Olmazlara Vuruluyorum",         "Şarkı"),
                new BulguCase("BULGU_005", "çıkmaz bir sokakta",            "",                  "Çıkmaz Bir Sokakta",            "Şarkı"),
                new BulguCase("BULGU_010", "maraton",                       "Ati242",            "Maraton",                       "Şarkı"),
                new BulguCase("BULGU_011", "geri ver",                      "Wegh",              "Geri Ver",                      "Şarkı"),
                new BulguCase("BULGU_012", "saygımdan",                     "Bengü",             "Saygımdan",                     "Şarkı"),
                new BulguCase("BULGU_013", "meğerse",                       "Linet",             "Meğerse",                       "Şarkı"),
                new BulguCase("BULGU_014", "çok pardon",                    "Lvbel C5",          "COOOK PARDON",                  "Şarkı"),
                new BulguCase("BULGU_015", "dacia",                         "Lvbel C5",          "DACIA",                         "Şarkı"),
                new BulguCase("BULGU_020", "yalnızlığın çaresini bulmuşlar","",                  "Yalnızlığın Çaresini Bulmuşlar","Şarkı"),
                new BulguCase("BULGU_023", "yapar mısın",                   "Poizi",             "YAPAR MISIN?",                  "Şarkı"),
                new BulguCase("BULGU_024", "yerinde",                       "Sefo",              "Yerinde Dur",                   "Şarkı"),
                new BulguCase("BULGU_025", "yerinde dur",                   "Sefo",              "Yerinde Dur",                   "Şarkı"),
                new BulguCase("BULGU_028", "ey aşk",                        "Sezen Aksu",        "Ey Aşk",                        "Şarkı"),
                new BulguCase("BULGU_030", "şımarık",                       "Tarkan",            "Şımarık",                       "Şarkı"),
                new BulguCase("BULGU_031", "giderim kırağınan",             "Onur Şan",          "Giderim Kırağınan",             "Şarkı"),
                new BulguCase("BULGU_032", "dame un grr",                   "Fantomel",          "Dame Un Grrr",                  "Şarkı"),
                new BulguCase("BULGU_033", "ara beni lütfen",               "Kenan Doğulu",      "Ara Beni Lütfen",               "Şarkı"),
                new BulguCase("BULGU_034", "vidrado em",                    "Dj Guuga",          "Vidrado Em Você",               "Şarkı"),
                new BulguCase("BULGU_035", "aşk yok olmaktır",              "",                  "Aşk Yok Olmaktır",              "Şarkı"),
                new BulguCase("BULGU_036", "can efendim",                   "",                  "Can Efendim",                   "Şarkı"),
                new BulguCase("BULGU_037", "çıt çıt",                       "Barış Manço",       "Çıt Çıt Çedene",                "Şarkı"),
                new BulguCase("BULGU_038", "çıt çıt çedene",                "Barış Manço",       "Çıt Çıt Çedene",                "Şarkı"),
                new BulguCase("BULGU_039", "çıkar biri karşıma",            "Poizi",             "Çıkar Biri Karşıma",            "Şarkı"),
                new BulguCase("BULGU_041", "messy lola young",              "Lola Young",        "Messy",                         "Şarkı"),
                new BulguCase("BULGU_042", "sen yanlış yaptın",             "Şahin Kendirci",    "Sen Yanlış Yaptın",             "Şarkı"),
                new BulguCase("BULGU_043", "vay dayı",                      "Aynur Polat",       "Vay Dayı",                      "Şarkı"),
                new BulguCase("BULGU_044", "silinmez",                      "Mansur Ark",        "Silinmez",                      "Şarkı"),
                new BulguCase("BULGU_045", "halbuki",                       "Yalın",             "Halbuki",                       "Şarkı"),
                new BulguCase("BULGU_047", "duydun mu",                     "Yusuf Güney",       "Duydun Mu?",                    "Şarkı"),
                new BulguCase("BULGU_048", "sana güvenmiyorum",             "Dedublüman",        "Sana Güvenmiyorum",             "Şarkı"),
                new BulguCase("BULGU_049", "yasemen",                       "Afra",              "Yasemen",                       "Şarkı"),
                new BulguCase("BULGU_050", "düşer o",                       "İzel",              "Düşer O",                       "Şarkı"),
                new BulguCase("BULGU_051", "kömür",                         "Mabel Matiz",       "Kömür",                         "Şarkı"),
                new BulguCase("BULGU_052", "mabel kömür",                   "Mabel Matiz",       "Kömür",                         "Şarkı"),
                new BulguCase("BULGU_054", "just the way you are",          "",                  "Just The Way You Are",          "Şarkı"),
                new BulguCase("BULGU_056", "snap",                          "Manifest",          "Snap",                          "Şarkı"),
                new BulguCase("BULGU_058", "y poizi",                       "Poizi",             "Y",                             "Şarkı"),
                new BulguCase("BULGU_059", "ama başaramadım",               "Burak Bulut",       "Ama Başaramadım",               "Şarkı"),
                new BulguCase("BULGU_060", "kts manifest",                  "Manifest",          "KTS",                           "Şarkı"),
                new BulguCase("BULGU_061", "adına bir çizik çektim",        "",                  "Adına Bir Çizik Çektim",        "Şarkı"),
                new BulguCase("BULGU_062", "yaramızda kalsın",              "Merve Özbey",       "Yaramızda Kalsın",              "Şarkı"),
                new BulguCase("BULGU_063", "sev yeter",                     "",                  "Sev Yeter",                     "Şarkı"),
                new BulguCase("BULGU_065", "kaybolurum gülüşünde",          "İkilem",            "Kaybolurum Gülüşünde",          "Şarkı"),
                new BulguCase("BULGU_070", "Dua Lipa Shine",                "Dua Lipa",          "Shine",                         "Şarkı"),
                new BulguCase("BULGU_071", "ağlama ben ağlarım",            "Canozan",           "Ağlama Ben Ağlarım",            "Şarkı"),
                new BulguCase("BULGU_072", "ağlama ben",                    "Canozan",           "Ağlama Ben Ağlarım",            "Şarkı"),
                new BulguCase("BULGU_074", "erik",                          "",                  "Erik Dalı",                     "Şarkı"),
                new BulguCase("BULGU_077", "karakedi",                      "Melis Fis",         "Kara Kedi",                     "Şarkı"),
                new BulguCase("BULGU_078", "doğuştan",                      "Lvbel C5",          "Doğuştan Beri Haklıyım",        "Şarkı"),
                new BulguCase("BULGU_080", "şikayetim var",                 "",                  "Şikayetim Var",                 "Şarkı"),
                new BulguCase("BULGU_081", "bunca yıl",                     "Dedublüman",        "Bunca Yıl",                     "Şarkı"),
                new BulguCase("BULGU_082", "düldül",                        "Mabel Matiz",       "Düldül",                        "Şarkı"),
                new BulguCase("BULGU_086", "perde",                         "Poizi",             "Perde",                         "Şarkı"),
                new BulguCase("BULGU_092", "sonbahar",                      "Era7Capone",        "SONBAHAR",                      "Şarkı"),
                new BulguCase("BULGU_093", "acem kızı",                     "",                  "Acem Kızı",                     "Şarkı"),
                new BulguCase("BULGU_094", "hacel obası",                   "",                  "Hacel Obası",                   "Şarkı"),
                new BulguCase("BULGU_095", "yalan",                         "",                  "Yalan",                         "Şarkı"),
                new BulguCase("BULGU_096", "bana sor",                      "Ferdi Tayfur",      "Bana Sor",                      "Şarkı"),
                new BulguCase("BULGU_097", "rüya manifest",                 "Manifest",          "Rüya",                          "Şarkı"),
                new BulguCase("BULGU_098", "rüya",                          "Manifest",          "Rüya",                          "Şarkı"),
                new BulguCase("BULGU_100", "ara",                           "Zeynep Bastık",     "Ara",                           "Şarkı"),
                new BulguCase("BULGU_101", "14 bahar",                      "Mert Demir",        "14 Bahar",                      "Şarkı"),
                new BulguCase("BULGU_103", "ela mana",                      "",                  "Ela Mana",                      "Şarkı"),
                new BulguCase("BULGU_105", "erik dalı",                     "",                  "Erik Dalı",                     "Şarkı"),
                new BulguCase("BULGU_106", "elfida",                        "",                  "Elfida",                        "Şarkı"),
                new BulguCase("BULGU_107", "yazan kalem siyah",             "",                  "Yazan Kalem Siyah",             "Şarkı"),
                new BulguCase("BULGU_108", "Mihriban",                      "",                  "Mihriban",                      "Şarkı"),
                new BulguCase("BULGU_111", "merdo",                         "",                  "Merdo",                         "Şarkı"),
                new BulguCase("BULGU_121", "misket",                        "",                  "Misket",                        "Şarkı"),
                new BulguCase("BULGU_122", "kara sevda",                    "",                  "Kara Sevda",                    "Şarkı"),
                new BulguCase("BULGU_123", "parla",                         "",                  "Parla",                         "Şarkı"),
                new BulguCase("BULGU_124", "kırmızı balık",                 "",                  "Kırmızı Balık",                 "Şarkı"),
                new BulguCase("BULGU_114", "dandini",                       "Ninni Bebek",       "Dandini Dandini Dastana",       "Şarkı"),
                new BulguCase("BULGU_119_C","farzet",                       "İlyas Yalçıntaş",   "Farzet",                        "Şarkı"),
                new BulguCase("BULGU_089", "phonk",                         "DEHA INC.",         "Phonk",                         "Şarkı"),
                new BulguCase("BULGU_104", "yekten",                        "Demet Akalın",      "Yekten",                        "Şarkı"),
                new BulguCase("BULGU_113", "sigara",                        "",                  "Sigara",                        "Şarkı"),
                new BulguCase("BULGU_019", "yalnızlığın çaresini",          "gripin",            "Yalnızlığın Çaresini Bulmuşlar","Şarkı"),

                // ─────────────────────────────────────────────────────────────
                // SANATÇI — Kullanıcı sanatçı adıyla arama yapıyor
                // ─────────────────────────────────────────────────────────────
                new BulguCase("BULGU_008", "mfö",                           "MFÖ",               "",                              "Sanatçı"),
                new BulguCase("BULGU_016", "Manifest",                      "Manifest",          "",                              "Sanatçı"),
                new BulguCase("BULGU_017", "semicenk",                      "Semicenk",          "",                              "Sanatçı"),
                new BulguCase("BULGU_053", "kök$l",                         "kök$vl",            "",                              "Sanatçı"),
                new BulguCase("BULGU_055", "utku akkaya",                   "Utku Akkaya",       "",                              "Sanatçı"),
                new BulguCase("BULGU_088", "derya bedavacı",                "Derya Bedavacı",    "",                              "Sanatçı"),
                new BulguCase("BULGU_090", "ceza",                          "Ceza",              "",                              "Sanatçı"),
                new BulguCase("BULGU_091", "Ceza",                          "Ceza",              "",                              "Sanatçı"),
                new BulguCase("BULGU_099", "çakal",                         "cakal",             "",                              "Sanatçı"),
                new BulguCase("BULGU_102", "yaşar",                         "Yaşar",             "",                              "Sanatçı"),
                new BulguCase("BULGU_110", "lvc5",                          "Lvbel C5",          "",                              "Sanatçı"),
                new BulguCase("BULGU_112", "Gökhan Özen",                   "Gökhan Özen",       "",                              "Sanatçı"),
                new BulguCase("BULGU_116", "level c5",                      "Lvbel C5",          "",                              "Sanatçı"),
                new BulguCase("BULGU_117", "levelc5",                       "Lvbel C5",          "",                              "Sanatçı"),
                new BulguCase("BULGU_118", "çelik",                         "Çelik",             "",                              "Sanatçı"),
                new BulguCase("BULGU_119_B","Haluk Levent",                  "Haluk Levent",      "",                              "Sanatçı"),
                new BulguCase("BULGU_120", "Mustafa Yıldızdoğan",           "Mustafa Yıldızdoğan","",                             "Sanatçı"),
                new BulguCase("BULGU_022", "teo",                           "Teoman",            "",                              "Sanatçı"),
                new BulguCase("BULGU_073", "arabam",                        "Sefo",              "",                              "Sanatçı"),

                // ─────────────────────────────────────────────────────────────
                // LYRİCS — Kullanıcı şarkı sözüyle arama yapıyor
                // ─────────────────────────────────────────────────────────────
                new BulguCase("BULGU_066", "bak ben yara gibiyim",          "Emir Can İğrek",    "Nalan",                         "Lyrics"),
                new BulguCase("BULGU_067", "çölüme yağmur oldun",           "Müslüm Gürses",     "Affet",                         "Lyrics"),
                new BulguCase("BULGU_068", "sana hastayım anlasana",        "Derya Uluğ",        "Yansıma",                       "Lyrics"),
                new BulguCase("BULGU_075", "hadi ya",                       "Melis Kar",         "Yatıya",                        "Lyrics"),
                new BulguCase("BULGU_076", "babalar",                       "Blok3",             "PATLAT",                        "Lyrics"),
                new BulguCase("BULGU_079", "silemez o beni",                "Yıldız Tilbe",      "Dizine Dursun",                 "Lyrics"),
                new BulguCase("BULGU_083", "çetin ceviz şerbetli mayam",    "Melike Şahin",      "Canın Beni Çekti",              "Lyrics"),
                new BulguCase("BULGU_084", "bir motive",                    "Motive",            "",                              "Lyrics"),

                // ─────────────────────────────────────────────────────────────
                // PLAYLİST — Kullanıcı kategori/tür adıyla liste arıyor
                // ─────────────────────────────────────────────────────────────
                new BulguCase("BULGU_026", "90 lar",                        "",                  "[Playlist] 90",                 "Playlist"),
                new BulguCase("BULGU_027", "çocuk",                         "",                  "[Playlist] çocuk",              "Playlist"),
                new BulguCase("BULGU_064", "pop",                           "",                  "[Playlist] pop",                "Playlist"),
                new BulguCase("BULGU_069", "yabancı",                       "",                  "[Playlist] yabancı",            "Playlist"),
                new BulguCase("BULGU_087", "akustik",                       "",                  "[Playlist] akustik",            "Playlist"),

                // ─────────────────────────────────────────────────────────────
                // TOLERANS — Yazım hatası, fonetik benzerlik, kısaltma
                // ─────────────────────────────────────────────────────────────
                new BulguCase("BULGU_006", "blok",                          "Blok3",             "",                              "Tolerans"),
                new BulguCase("BULGU_007", "kusura bakma",                  "Blok3",             "Kusura Bakma",                  "Tolerans"),
                new BulguCase("BULGU_021", "tarkn",                         "Tarkan",            "",                              "Tolerans"),
                new BulguCase("BULGU_029", "simarik",                       "Tarkan",            "Şımarık",                       "Tolerans"),
                new BulguCase("BULGU_040", "hav hav",                       "Lvbel C5",          "Havhavhav",                     "Tolerans"),
                new BulguCase("BULGU_115", "pozi",                          "Poizi",             "",                              "Tolerans"),
                new BulguCase("BULGU_119", "hşdra",                         "Hidra",             "",                              "Tolerans"),

                // ─────────────────────────────────────────────────────────────
                // UAT — SANATÇI ARAMALARI
                // ─────────────────────────────────────────────────────────────
                new BulguCase("UAT_001",  "u2",              "u2",                    "",  "UAT Sanatçı"),
                new BulguCase("UAT_002",  "84",              "seksendört",            "",  "UAT Sanatçı"),
                new BulguCase("UAT_003",  "goksel",          "Göksel",                "",  "UAT Sanatçı"),
                new BulguCase("UAT_010",  "edis",            "Edis",                  "",  "UAT Sanatçı"),
                new BulguCase("UAT_013",  "uzi",             "Uzi",                   "",  "UAT Sanatçı"),
                new BulguCase("UAT_014",  "can ozan",        "Canozan",               "",  "UAT Sanatçı"),
                new BulguCase("UAT_016",  "aleyna",          "Aleyna Tilki",          "",  "UAT Sanatçı"),
                new BulguCase("UAT_020",  "ezel",            "Ezhel",                 "",  "UAT Sanatçı"),
                new BulguCase("UAT_025",  "ferdi",           "Ferdi Tayfur",          "",  "UAT Sanatçı"),
                new BulguCase("UAT_026",  "mabel",           "Mabel Matiz",           "",  "UAT Sanatçı"),
                new BulguCase("UAT_027",  "yıldız",          "Yıldız Tilbe",          "",  "UAT Sanatçı"),
                new BulguCase("UAT_028",  "azer",            "Azer Bülbül",           "",  "UAT Sanatçı"),
                new BulguCase("UAT_030",  "sıla",            "Sıla",                  "",  "UAT Sanatçı"),
                new BulguCase("UAT_032",  "blok",            "BLOK3",                 "",  "UAT Sanatçı"),
                new BulguCase("UAT_033",  "serdar",          "Serdar Ortaç",          "",  "UAT Sanatçı"),
                new BulguCase("UAT_035",  "reymen",          "Reynmen",               "",  "UAT Sanatçı"),
                new BulguCase("UAT_036",  "cengiz",          "Cengiz Kurtoğlu",       "",  "UAT Sanatçı"),
                new BulguCase("UAT_038",  "neşet",           "Neşet Ertaş",           "",  "UAT Sanatçı"),
                new BulguCase("UAT_039",  "melike",          "Melike Şahin",          "",  "UAT Sanatçı"),
                new BulguCase("UAT_041",  "çakal",           "cakal",                 "",  "UAT Sanatçı"),
                new BulguCase("UAT_042",  "orhan",           "Orhan Gencebay",        "",  "UAT Sanatçı"),
                new BulguCase("UAT_045",  "eminem",          "Eminem",                "",  "UAT Sanatçı"),
                new BulguCase("UAT_046",  "sago",            "Sagopa Kajmer",         "",  "UAT Sanatçı"),
                new BulguCase("UAT_047",  "emircan",         "Emir Can İğrek",        "",  "UAT Sanatçı"),
                new BulguCase("UAT_049",  "soner",           "Soner Sarıkabadayı",    "",  "UAT Sanatçı"),
                new BulguCase("UAT_050",  "ati",             "Ati242",                "",  "UAT Sanatçı"),
                new BulguCase("UAT_051",  "güneş",           "Güneş",                 "",  "UAT Sanatçı"),
                new BulguCase("UAT_052",  "dua lipa",        "Dua Lipa",              "",  "UAT Sanatçı"),
                new BulguCase("UAT_053",  "norm",            "Norm Ender",            "",  "UAT Sanatçı"),
                new BulguCase("UAT_055",  "sibel",           "Sibel Can",             "",  "UAT Sanatçı"),
                new BulguCase("UAT_056",  "irem",            "İrem Derici",           "",  "UAT Sanatçı"),
                new BulguCase("UAT_058",  "musa",            "Musa Eroğlu",           "",  "UAT Sanatçı"),
                new BulguCase("UAT_059",  "mero",            "Mero",                  "",  "UAT Sanatçı"),
                new BulguCase("UAT_061",  "murda",           "Murda",                 "",  "UAT Sanatçı"),
                new BulguCase("UAT_062",  "kurtuluş",        "Kurtuluş Kuş",          "",  "UAT Sanatçı"),
                new BulguCase("UAT_063",  "cash",            "Cash Flow",             "",  "UAT Sanatçı"),
                new BulguCase("UAT_064",  "inna",            "Inna",                  "",  "UAT Sanatçı"),
                new BulguCase("UAT_067",  "adele",           "Adele",                 "",  "UAT Sanatçı"),
                new BulguCase("UAT_070",  "mor ve ötesi",    "mor ve ötesi",          "",  "UAT Sanatçı"),
                new BulguCase("UAT_071",  "reyn",            "Reynmen",               "",  "UAT Sanatçı"),
                new BulguCase("UAT_072",  "mahsun",          "Mahsun Kırmızıgül",     "",  "UAT Sanatçı"),
                new BulguCase("UAT_074",  "emircan iğrek",   "Emir Can İğrek",        "",  "UAT Sanatçı"),
                new BulguCase("UAT_075",  "patron",          "Patron",                "",  "UAT Sanatçı"),
                new BulguCase("UAT_076",  "tefo",            "Tefo",                  "",  "UAT Sanatçı"),
                new BulguCase("UAT_077",  "doğuş",           "Doğuş",                 "",  "UAT Sanatçı"),
                new BulguCase("UAT_078",  "funda",           "Funda Arar",            "",  "UAT Sanatçı"),
                new BulguCase("UAT_080",  "sura",            "Sura İskenderli",       "",  "UAT Sanatçı"),
                new BulguCase("UAT_081",  "rafet",           "Rafet El Roman",        "",  "UAT Sanatçı"),
                new BulguCase("UAT_084",  "ben fero",        "Ben Fero",              "",  "UAT Sanatçı"),
                new BulguCase("UAT_085",  "haluk",           "Haluk Levent",          "",  "UAT Sanatçı"),
                new BulguCase("UAT_086",  "inji",            "INJI",                  "",  "UAT Sanatçı"),
                new BulguCase("UAT_087",  "rihanna",         "Rihanna",               "",  "UAT Sanatçı"),
                new BulguCase("UAT_088",  "lvbel",           "Lvbel C5",              "",  "UAT Sanatçı"),
                new BulguCase("UAT_089",  "mavi",            "Mavi",                  "",  "UAT Sanatçı"),
                new BulguCase("UAT_090",  "velet",           "Velet",                 "",  "UAT Sanatçı"),
                new BulguCase("UAT_091",  "adamlar",         "Adamlar",               "",  "UAT Sanatçı"),
                new BulguCase("UAT_092",  "zerrin",          "Zerrin Özer",           "",  "UAT Sanatçı"),
                new BulguCase("UAT_093",  "selda",           "Selda Bağcan",          "",  "UAT Sanatçı"),
                new BulguCase("UAT_094",  "bilal",           "Bilal Sonses",          "",  "UAT Sanatçı"),
                new BulguCase("UAT_096",  "gülden",          "Gülden Karaböcek",      "",  "UAT Sanatçı"),
                new BulguCase("UAT_097",  "ibrahim tat",     "İbrahim Tatlıses",      "",  "UAT Sanatçı"),
                new BulguCase("UAT_098",  "engin",           "Engin Nurşani",         "",  "UAT Sanatçı"),
                new BulguCase("UAT_099",  "şebnem",          "Şebnem Ferah",          "",  "UAT Sanatçı"),
                new BulguCase("UAT_100",  "ayaz",            "Ayaz Erdoğan",          "",  "UAT Sanatçı"),
                new BulguCase("UAT_101",  "ajda",            "Ajda Pekkan",           "",  "UAT Sanatçı"),
                new BulguCase("UAT_102",  "blackpink",       "BLACKPINK",             "",  "UAT Sanatçı"),
                new BulguCase("UAT_103",  "no1",             "No.1",                  "",  "UAT Sanatçı"),
                new BulguCase("UAT_104",  "sia",             "Sia",                   "",  "UAT Sanatçı"),
                new BulguCase("UAT_105",  "izel",            "İzel",                  "",  "UAT Sanatçı"),
                new BulguCase("UAT_107",  "aynur",           "Aynur Aydın",           "",  "UAT Sanatçı"),
                new BulguCase("UAT_109",  "hayko",           "Hayko Cepkin",          "",  "UAT Sanatçı"),
                new BulguCase("UAT_110",  "shakira",         "Shakira",               "",  "UAT Sanatçı"),
                new BulguCase("UAT_111",  "halo",            "Halodayı",              "",  "UAT Sanatçı"),
                new BulguCase("UAT_112",  "50",              "50 Cent",               "",  "UAT Sanatçı"),
                new BulguCase("UAT_113",  "koray",           "Koray Avcı",            "",  "UAT Sanatçı"),
                new BulguCase("UAT_114",  "ümit",            "Ümit Besen",            "",  "UAT Sanatçı"),
                new BulguCase("UAT_115",  "elif buse",       "Elif Buse Doğan",       "",  "UAT Sanatçı"),
                new BulguCase("UAT_118",  "hejan",           "Heijan",                "",  "UAT Sanatçı"),
                new BulguCase("UAT_120",  "no 1",            "No.1",                  "",  "UAT Sanatçı"),
                new BulguCase("UAT_123",  "özcan",           "Özcan Deniz",           "",  "UAT Sanatçı"),
                new BulguCase("UAT_125",  "deha",            "DEHA INC.",             "",  "UAT Sanatçı"),
                new BulguCase("UAT_127",  "semicek",         "Semicenk",              "",  "UAT Sanatçı"),
                new BulguCase("UAT_129",  "taylor",          "Taylor Swift",          "",  "UAT Sanatçı"),
                new BulguCase("UAT_130",  "beyonce",         "Beyoncé",               "",  "UAT Sanatçı"),
                new BulguCase("UAT_131",  "emre gel",        "Emre Fel",              "",  "UAT Sanatçı"),
                new BulguCase("UAT_133",  "sibelcan",        "Sibel Can",             "",  "UAT Sanatçı"),
                new BulguCase("UAT_135",  "kofn",            "KÖFN",                  "",  "UAT Sanatçı"),
                new BulguCase("UAT_139",  "madonna",         "Madonna",               "",  "UAT Sanatçı"),
                new BulguCase("UAT_142",  "sertap",          "Sertab Erener",         "",  "UAT Sanatçı"),
                new BulguCase("UAT_146",  "mr ve ötei",      "Mor ve Ötesi",          "",  "UAT Sanatçı"),
                new BulguCase("UAT_147",  "dolu kadhi tut",  "Dolu Kadehi Ters Tut",  "",  "UAT Sanatçı"),
                new BulguCase("UAT_149",  "dktt",            "Dolu Kadehi Ters Tut",  "",  "UAT Sanatçı"),

                // ─────────────────────────────────────────────────────────────
                // UAT LYRİCS — Kullanıcı şarkı sözüyle arama yapıyor
                // ─────────────────────────────────────────────────────────────
                new BulguCase("UAT_117",  "hadi ya",                        "Melis Kar",         "Yatıya",           "UAT Lyrics"),
                new BulguCase("UAT_119",  "babalar",                        "Blok3",             "PATLAT",           "UAT Lyrics"),
                new BulguCase("UAT_128",  "sarışınlar",                     "Derya Uluğ",        "Esmerin Adı Oya",  "UAT Lyrics"),
                new BulguCase("UAT_136",  "silemez o beni",                 "Yıldız Tilbe",      "Dizine Dursun",    "UAT Lyrics"),
                new BulguCase("UAT_137",  "babalar sözünü tutar",           "Blok3",             "PATLAT",           "UAT Lyrics"),
                new BulguCase("UAT_143",  "çok geç şmdi",                   "Edis",              "Yalan",            "UAT Lyrics"),
                new BulguCase("UAT_144",  "affet bu gece istedim ölmek",    "Model",             "Pembe Mezarlık",   "UAT Lyrics"),

                // ─────────────────────────────────────────────────────────────
                // UAT ŞARKI ARAMALARI
                // ─────────────────────────────────────────────────────────────
                new BulguCase("UAT_011",  "nasır",                          "Melike Şahin",      "Nasır",                    "UAT Şarkı"),
                new BulguCase("UAT_015",  "yandım ah",                      "Sakiler",           "Yalanı Bırak",             "UAT Şarkı"),
                new BulguCase("UAT_017",  "sus",                            "Ceza",              "Suspus",                   "UAT Şarkı"),
                new BulguCase("UAT_018",  "pus",                            "Ceza",              "Suspus",                   "UAT Şarkı"),
                new BulguCase("UAT_019",  "bodrum",                         "Yüzyüzeyken Konuşuruz","Bodrum",                "UAT Şarkı"),
                new BulguCase("UAT_022",  "bak ben yara gibiyim",           "Emir Can İğrek",    "Nalan",                    "UAT Şarkı"),
                new BulguCase("UAT_023",  "çölüme yağmur oldun",            "Müslüm Gürses",     "Affet",                    "UAT Şarkı"),
                new BulguCase("UAT_024",  "zaten aşklar hep yalan dolan",   "Yıldız Tilbe",      "Sana Değer",               "UAT Şarkı"),
                new BulguCase("UAT_034",  "sana hastayım anlasana",         "Derya Uluğ",        "Yansıma",                  "UAT Şarkı"),
                new BulguCase("UAT_054",  "illede sen",                     "Azer Bülbül",       "İlle De Sen",              "UAT Şarkı"),
                new BulguCase("UAT_068",  "arabam",                         "Sefo",              "Araba",                    "UAT Şarkı"),
                new BulguCase("UAT_145",  "lacivert eceler",                "Ferhat Göçer",      "Lacivert Geceler",         "UAT Şarkı"),
                new BulguCase("UAT_148",  "güldün ne güzel",                "Pinhani",           "Ne Güzel Güldün",          "UAT Şarkı"),
                new BulguCase("UAT_141",  "kalbimin sahibine",              "İrem Derici",       "Kalbimin Tek Sahibine",    "UAT Şarkı"),
                new BulguCase("UAT_126",  "gözlerime bak",                  "Mert Demir",        "Gözlerime Bak",            "UAT Şarkı"),

                // ─────────────────────────────────────────────────────────────
                // UAT PLAYLİST ARAMALARI
                // ─────────────────────────────────────────────────────────────
                new BulguCase("UAT_004",  "pop",             "",  "[Playlist] pop",          "UAT Playlist"),
                new BulguCase("UAT_005",  "90",              "",  "[Playlist] 90",           "UAT Playlist"),
                new BulguCase("UAT_006",  "90lar",           "",  "[Playlist] 90",           "UAT Playlist"),
                new BulguCase("UAT_007",  "90'lar",          "",  "[Playlist] 90",           "UAT Playlist"),
                new BulguCase("UAT_008",  "90s",             "",  "[Playlist] 90",           "UAT Playlist"),
                new BulguCase("UAT_009",  "90 lar",          "",  "[Playlist] 90",           "UAT Playlist"),
                new BulguCase("UAT_029",  "arabesk",         "",  "[Playlist] arabesk",      "UAT Playlist"),
                new BulguCase("UAT_031",  "ilahi",           "",  "[Playlist] ilahi",        "UAT Playlist"),
                new BulguCase("UAT_037",  "karadeniz",       "",  "[Playlist] karadeniz",    "UAT Playlist"),
                new BulguCase("UAT_040",  "halay",           "",  "[Playlist] halay",        "UAT Playlist"),
                new BulguCase("UAT_043",  "yabancı",         "",  "[Playlist] yabancı",      "UAT Playlist"),
                new BulguCase("UAT_044",  "roman",           "",  "[Playlist] roman",        "UAT Playlist"),
                new BulguCase("UAT_048",  "çocuk",           "",  "[Playlist] çocuk",        "UAT Playlist"),
                new BulguCase("UAT_065",  "oyun hava",       "",  "[Playlist] oyun hava",    "UAT Playlist"),
                new BulguCase("UAT_069",  "spor",            "",  "[Playlist] spor",         "UAT Playlist"),
                new BulguCase("UAT_073",  "klasik",          "",  "[Playlist] klasik",       "UAT Playlist"),
                new BulguCase("UAT_079",  "ankara",          "",  "[Playlist] ankara",       "UAT Playlist"),
                new BulguCase("UAT_082",  "akustik",         "",  "[Playlist] akustik",      "UAT Playlist"),
                new BulguCase("UAT_083",  "çocuk şarkıları", "",  "[Playlist] çocuk",        "UAT Playlist"),
                new BulguCase("UAT_106",  "türkçe",          "",  "[Playlist] türkçe",       "UAT Playlist"),
                new BulguCase("UAT_108",  "80",              "",  "[Playlist] 80",           "UAT Playlist"),
                new BulguCase("UAT_116",  "rock",            "",  "[Playlist] rock",         "UAT Playlist"),
                new BulguCase("UAT_121",  "dans",            "",  "[Playlist] dans",         "UAT Playlist"),
                new BulguCase("UAT_122",  "türk sanat",      "",  "[Playlist] türk sanat",   "UAT Playlist"),
                new BulguCase("UAT_134",  "meditasyon",      "",  "[Playlist] meditasyon",   "UAT Playlist"),
                new BulguCase("UAT_138",  "90 lar pop",      "",  "[Playlist] pop",          "UAT Playlist"),
                new BulguCase("UAT_140",  "ramazan",         "",  "[Playlist] ramazan",      "UAT Playlist")
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
                    "Gözlem case'i — beklenen içerik tanımlanmamış, " + list.size() + " sonuç döndü."};
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
        String expected    = "1. sırada '" + bc.expArtist() + "' sanatçısı gelmeli.";
        String firstArtist = MuudSearchUtils.safeStr(jp.getString(base + "[0].data.performerName"));

        if (MuudSearchUtils.containsTRInsensitive(firstArtist, bc.expArtist())) {
            return new String[]{expected, "OK",
                    "Başarılı — 1. sırada '" + firstArtist + "' geldi."};
        }

        int    pos   = MuudSearchUtils.findArtistIndex(jp, TOP_N, bc.expArtist());
        String where = pos == -1
                ? "top-" + TOP_N + "'da bulunamadı"
                : (pos + 1) + ". sırada bulundu";

        return new String[]{expected, "NOK",
                "1. sırada '" + firstItemDesc(jp, base) + "' geldi — '"
                        + bc.expArtist() + "': " + where + "."};
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOPN_HAS_ARTIST_AND_TRACK — Top-N içinde sanatçı+şarkı birlikte bulunmalı
    // ─────────────────────────────────────────────────────────────────────────

    private String[] evalArtistAndTrack(BulguCase bc, JsonPath jp, String base) {
        String expStr   = bc.expArtist().isEmpty()
                ? "'" + bc.expTrack() + "'"
                : "'" + bc.expArtist() + "' – '" + bc.expTrack() + "'";
        String expected = "Top-" + TOP_N + " içinde " + expStr + " eşleşmesi bulunmalı.";

        int idx = MuudSearchUtils.findArtistAndTrackIndex(jp, TOP_N, bc.expArtist(), bc.expTrack());

        if (idx != -1) {
            String fa = MuudSearchUtils.safeStr(jp.getString(base + "[" + idx + "].data.performerName"));
            String ft = MuudSearchUtils.safeStr(jp.getString(base + "[" + idx + "].data.songName"));
            if (ft.isEmpty())
                ft = MuudSearchUtils.safeStr(jp.getString(base + "[" + idx + "].data.albumName"));
            String label = fa.isEmpty() ? ft : fa + "' – '" + ft;
            return new String[]{expected, "OK",
                    "Başarılı — " + (idx + 1) + ". sırada: '" + label + "'."};
        }

        return new String[]{expected, "NOK",
                "Top-" + TOP_N + "'da bulunamadı. 1. sırada: " + firstItemDesc(jp, base) + "."};
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOPN_RELATED_PLAYLIST — Top-N içinde keyword içeren playlist bulunmalı
    // ─────────────────────────────────────────────────────────────────────────

    private String[] evalPlaylist(BulguCase bc, JsonPath jp, String base) {
        String keyword  = bc.expTrack().substring("[Playlist] ".length());
        String expected = "Top-" + TOP_N + " içinde '" + keyword + "' adını içeren playlist bulunmalı.";

        for (int i = 0; i < TOP_N; i++) {
            String pl = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.playlistName"));
            if (!pl.isEmpty() && MuudSearchUtils.containsTRInsensitive(pl, keyword)) {
                return new String[]{expected, "OK",
                        "Başarılı — " + (i + 1) + ". sırada playlist bulundu: '" + pl + "'."};
            }
        }

        return new String[]{expected, "NOK",
                "Top-" + TOP_N + "'da '" + keyword
                        + "' içeren playlist bulunamadı. 1. sırada: " + firstItemDesc(jp, base) + "."};
    }

    // =========================================================================
    // YARDIMCI — "Ne görüldü?" kısa açıklaması
    // =========================================================================

    /**
     * 1. sıradaki sonucun insan-okunur kısa açıklamasını üretir.
     * Sadece NOK mesajlarında "1. sırada X geldi" ifadesini doldurmak için kullanılır.
     */
    private String firstItemDesc(JsonPath jp, String base) {
        String song      = MuudSearchUtils.safeStr(jp.getString(base + "[0].data.songName"));
        String album     = MuudSearchUtils.safeStr(jp.getString(base + "[0].data.albumName"));
        String playlist  = MuudSearchUtils.safeStr(jp.getString(base + "[0].data.playlistName"));
        String performer = MuudSearchUtils.safeStr(jp.getString(base + "[0].data.performerName"));

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
}