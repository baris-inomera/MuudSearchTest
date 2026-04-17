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
 *  BULGU SNAPSHOT V2 — Birleşik Regresyon Takipçisi
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  İçerik:
 *    BulguSnapshotTest → 124 BULGU case (bölüm 1–9)
 *    MuudSearchApiKapsayiciUATTest → 149 UAT case
 *    TOPLAM: 273 case
 *
 *  Tüm case'ler active-indices (general) üzerinde çalışır.
 *  Test HİÇBİR ZAMAN fail etmez — saf gözlem & regresyon raporu üretir.
 *
 *  Kullanım:
 *    mvn test -Dtest=BulguSnapshotV2
 *    Çıktı: proje kök dizininde BulguSnapshot_YYYYMMDD_HHmmss.xlsx
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BulguSnapshotV2 extends TestConfig {

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
        System.out.println("✅ BulguSnapshotV2 başlatıldı — " + TOP_N + " sonuç toplanacak.");
    }

    @AfterAll
    static void writeReport() {
        System.out.printf("%n📋 Toplam %d case işlendi.%n", ROWS.size());
        BulguSnapshotWriter.write(ROWS);
    }

    // =========================================================================
    // CASE TANIMLAMASI
    // =========================================================================

    record BulguCase(String caseId, String term, String expArtist, String expTrack, String section) {}

    static Stream<BulguCase> cases() {
        return Stream.of(

                // ─────────────────────────────────────────────────────────────
                // ŞARKI — Kullanıcı şarkı adıyla (tam/kısmi/sanatçı+şarkı) arama yapıyor
                // Pozisyon sütunu (YOK / #N) bulunup bulunmadığını ve yerini gösterir
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
                new BulguCase("BULGU_114", "dandini",                       "Ninni Bebek",                  "Dandini Dandini Dastana",                              "Şarkı"),
                new BulguCase("BULGU_119_C","farzet",                       "İlyas Yalçıntaş",                  "Farzet",                              "Şarkı"),
                new BulguCase("BULGU_089", "phonk",                         "DEHA INC.",                 "Phonk",                               "Şarkı"),
                new BulguCase("BULGU_104", "yekten",                        "Demet Akalın",                  "Yekten",                              "Şarkı"),
                new BulguCase("BULGU_113", "sigara",                        "",                  "Sigara",                              "Şarkı"),
                new BulguCase("BULGU_019", "yalnızlığın çaresini",          "gripin",                  "Yalnızlığın Çaresini Bulmuşlar",                              "Şarkı"),
                // ─────────────────────────────────────────────────────────────
                // SANATÇI — Kullanıcı sanatçı adıyla (tam/kısmi) arama yapıyor
                // ─────────────────────────────────────────────────────────────
                new BulguCase("BULGU_008", "mfö",                           "MFÖ",               "",                              "Sanatçı"),
                new BulguCase("BULGU_016", "Manifest",                      "Manifest",          "",                              "Sanatçı"),
                new BulguCase("BULGU_017", "semicenk",                      "Semicenk",          "",                              "Sanatçı"),
                new BulguCase("BULGU_053", "kök$l",                         "kök$vl",             "",                            "Sanatçı"),
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
                new BulguCase("BULGU_119_B","Haluk Levent",                 "Haluk Levent",      "",                              "Sanatçı"),
                new BulguCase("BULGU_120", "Mustafa Yıldızdoğan",           "Mustafa Yıldızdoğan","",                             "Sanatçı"),
                new BulguCase("BULGU_022", "teo",                           "Teoman",                  "",                              "Sanatçı"),
                new BulguCase("BULGU_073", "arabam",                        "Sefo",                  "",                              "Sanatçı"),

                // ─────────────────────────────────────────────────────────────
                // LYRİCS — Kullanıcı şarkı sözü/içeriğiyle arama yapıyor
                // (Arama terimi şarkı/sanatçı adıyla örtüşmüyor)
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
                // TOLERANS — Yazım hatası, fonetik benzerlik, kısaltma, Türkçe
                //            karakter eksikliği içeren aramalar
                // ─────────────────────────────────────────────────────────────
                new BulguCase("BULGU_006", "blok",                          "Blok3",             "",                              "Tolerans"),
                new BulguCase("BULGU_007", "kusura bakma",                  "Blok3",             "Kusura Bakma",                  "Tolerans"),
                new BulguCase("BULGU_021", "tarkn",                         "Tarkan",            "",                              "Tolerans"),
                new BulguCase("BULGU_029", "simarik",                       "Tarkan",            "Şımarık",                       "Tolerans"),
                new BulguCase("BULGU_040", "hav hav",                       "Lvbel C5",          "Havhavhav",                     "Tolerans"),
                new BulguCase("BULGU_115", "pozi",                          "Poizi",             "",                              "Tolerans"),
                new BulguCase("BULGU_119", "hşdra",                         "Hidra",             "",                              "Tolerans"),




                // ─────────────────────────────────────────────────────────────
                // UAT — SANATÇI ARAMALARI (FIRST_ARTIST_IS)
                // Beklenti: ilk sırada ilgili sanatçı gelmeli
                // ─────────────────────────────────────────────────────────────
                new BulguCase("UAT_001",  "u2",              "u2",                    "",   "UAT Sanatçı"),
                new BulguCase("UAT_002",  "84",              "seksendört",            "",   "UAT Sanatçı"),
                new BulguCase("UAT_003",  "goksel",          "Göksel",                "",   "UAT Sanatçı"),
                new BulguCase("UAT_010",  "edis",            "Edis",                  "",   "UAT Sanatçı"),
                new BulguCase("UAT_013",  "uzi",             "Uzi",                   "",   "UAT Sanatçı"),
                new BulguCase("UAT_014",  "can ozan",        "Canozan",               "",   "UAT Sanatçı"),
                new BulguCase("UAT_016",  "aleyna",          "Aleyna Tilki",          "",   "UAT Sanatçı"),
                new BulguCase("UAT_020",  "ezel",            "Ezhel",                 "",   "UAT Sanatçı"),
                new BulguCase("UAT_025",  "ferdi",           "Ferdi Tayfur",          "",   "UAT Sanatçı"),
                new BulguCase("UAT_026",  "mabel",           "Mabel Matiz",           "",   "UAT Sanatçı"),
                new BulguCase("UAT_027",  "yıldız",          "Yıldız Tilbe",          "",   "UAT Sanatçı"),
                new BulguCase("UAT_028",  "azer",            "Azer Bülbül",           "",   "UAT Sanatçı"),
                new BulguCase("UAT_030",  "sıla",            "Sıla",                  "",   "UAT Sanatçı"),
                new BulguCase("UAT_032",  "blok",            "BLOK3",                 "",   "UAT Sanatçı"),
                new BulguCase("UAT_033",  "serdar",          "Serdar Ortaç",          "",   "UAT Sanatçı"),
                new BulguCase("UAT_035",  "reymen",          "Reynmen",               "",   "UAT Sanatçı"),
                new BulguCase("UAT_036",  "cengiz",          "Cengiz Kurtoğlu",       "",   "UAT Sanatçı"),
                new BulguCase("UAT_038",  "neşet",           "Neşet Ertaş",           "",   "UAT Sanatçı"),
                new BulguCase("UAT_039",  "melike",          "Melike Şahin",          "",   "UAT Sanatçı"),
                new BulguCase("UAT_041",  "çakal",           "cakal",                 "",   "UAT Sanatçı"),
                new BulguCase("UAT_042",  "orhan",           "Orhan Gencebay",        "",   "UAT Sanatçı"),
                new BulguCase("UAT_045",  "eminem",          "Eminem",                "",   "UAT Sanatçı"),
                new BulguCase("UAT_046",  "sago",            "Sagopa Kajmer",         "",   "UAT Sanatçı"),
                new BulguCase("UAT_047",  "emircan",         "Emir Can İğrek",        "",   "UAT Sanatçı"),
                new BulguCase("UAT_049",  "soner",           "Soner Sarıkabadayı",    "",   "UAT Sanatçı"),
                new BulguCase("UAT_050",  "ati",             "Ati242",                "",   "UAT Sanatçı"),
                new BulguCase("UAT_051",  "güneş",           "Güneş",                 "",   "UAT Sanatçı"),
                new BulguCase("UAT_052",  "dua lipa",        "Dua Lipa",              "",   "UAT Sanatçı"),
                new BulguCase("UAT_053",  "norm",            "Norm Ender",            "",   "UAT Sanatçı"),
                new BulguCase("UAT_055",  "sibel",           "Sibel Can",             "",   "UAT Sanatçı"),
                new BulguCase("UAT_056",  "irem",            "İrem Derici",           "",   "UAT Sanatçı"),
                new BulguCase("UAT_058",  "musa",            "Musa Eroğlu",           "",   "UAT Sanatçı"),
                new BulguCase("UAT_059",  "mero",            "Mero",                  "",   "UAT Sanatçı"),
                new BulguCase("UAT_061",  "murda",           "Murda",                 "",   "UAT Sanatçı"),
                new BulguCase("UAT_062",  "kurtuluş",        "Kurtuluş Kuş",          "",   "UAT Sanatçı"),
                new BulguCase("UAT_063",  "cash",            "Cash Flow",             "",   "UAT Sanatçı"),
                new BulguCase("UAT_064",  "inna",            "Inna",                  "",   "UAT Sanatçı"),
                new BulguCase("UAT_067",  "adele",           "Adele",                 "",   "UAT Sanatçı"),
                new BulguCase("UAT_070",  "mor ve ötesi",    "mor ve ötesi",          "",   "UAT Sanatçı"),
                new BulguCase("UAT_071",  "reyn",            "Reynmen",               "",   "UAT Sanatçı"),
                new BulguCase("UAT_072",  "mahsun",          "Mahsun Kırmızıgül",     "",   "UAT Sanatçı"),
                new BulguCase("UAT_074",  "emircan iğrek",   "Emir Can İğrek",        "",   "UAT Sanatçı"),
                new BulguCase("UAT_075",  "patron",          "Patron",                "",   "UAT Sanatçı"),
                new BulguCase("UAT_076",  "tefo",            "Tefo",                  "",   "UAT Sanatçı"),
                new BulguCase("UAT_077",  "doğuş",           "Doğuş",                 "",   "UAT Sanatçı"),
                new BulguCase("UAT_078",  "funda",           "Funda Arar",            "",   "UAT Sanatçı"),
                new BulguCase("UAT_080",  "sura",            "Sura İskenderli",       "",   "UAT Sanatçı"),
                new BulguCase("UAT_081",  "rafet",           "Rafet El Roman",        "",   "UAT Sanatçı"),
                new BulguCase("UAT_084",  "ben fero",        "Ben Fero",              "",   "UAT Sanatçı"),
                new BulguCase("UAT_085",  "haluk",           "Haluk Levent",          "",   "UAT Sanatçı"),
                new BulguCase("UAT_086",  "inji",            "INJI",                  "",   "UAT Sanatçı"),
                new BulguCase("UAT_087",  "rihanna",         "Rihanna",               "",   "UAT Sanatçı"),
                new BulguCase("UAT_088",  "lvbel",           "Lvbel C5",              "",   "UAT Sanatçı"),
                new BulguCase("UAT_089",  "mavi",            "Mavi",                  "",   "UAT Sanatçı"),
                new BulguCase("UAT_090",  "velet",           "Velet",                 "",   "UAT Sanatçı"),
                new BulguCase("UAT_091",  "adamlar",         "Adamlar",               "",   "UAT Sanatçı"),
                new BulguCase("UAT_092",  "zerrin",          "Zerrin Özer",           "",   "UAT Sanatçı"),
                new BulguCase("UAT_093",  "selda",           "Selda Bağcan",          "",   "UAT Sanatçı"),
                new BulguCase("UAT_094",  "bilal",           "Bilal Sonses",          "",   "UAT Sanatçı"),
                new BulguCase("UAT_096",  "gülden",          "Gülden Karaböcek",      "",   "UAT Sanatçı"),
                new BulguCase("UAT_097",  "ibrahim tat",     "İbrahim Tatlıses",      "",   "UAT Sanatçı"),
                new BulguCase("UAT_098",  "engin",           "Engin Nurşani",         "",   "UAT Sanatçı"),
                new BulguCase("UAT_099",  "şebnem",          "Şebnem Ferah",          "",   "UAT Sanatçı"),
                new BulguCase("UAT_100",  "ayaz",            "Ayaz Erdoğan",          "",   "UAT Sanatçı"),
                new BulguCase("UAT_101",  "ajda",            "Ajda Pekkan",           "",   "UAT Sanatçı"),
                new BulguCase("UAT_102",  "blackpink",       "BLACKPINK",             "",   "UAT Sanatçı"),
                new BulguCase("UAT_103",  "no1",             "No.1",                  "",   "UAT Sanatçı"),
                new BulguCase("UAT_104",  "sia",             "Sia",                   "",   "UAT Sanatçı"),
                new BulguCase("UAT_105",  "izel",            "İzel",                  "",   "UAT Sanatçı"),
                new BulguCase("UAT_107",  "aynur",           "Aynur Aydın",           "",   "UAT Sanatçı"),
                new BulguCase("UAT_109",  "hayko",           "Hayko Cepkin",          "",   "UAT Sanatçı"),
                new BulguCase("UAT_110",  "shakira",         "Shakira",               "",   "UAT Sanatçı"),
                new BulguCase("UAT_111",  "halo",            "Halodayı",              "",   "UAT Sanatçı"),
                new BulguCase("UAT_112",  "50",              "50 Cent",               "",   "UAT Sanatçı"),
                new BulguCase("UAT_113",  "koray",           "Koray Avcı",            "",   "UAT Sanatçı"),
                new BulguCase("UAT_114",  "ümit",            "Ümit Besen",            "",   "UAT Sanatçı"),
                new BulguCase("UAT_115",  "elif buse",       "Elif Buse Doğan",       "",   "UAT Sanatçı"),
                new BulguCase("UAT_118",  "hejan",           "Heijan",                "",   "UAT Sanatçı"),
                new BulguCase("UAT_120",  "no 1",            "No.1",                  "",   "UAT Sanatçı"),
                new BulguCase("UAT_123",  "özcan",           "Özcan Deniz",           "",   "UAT Sanatçı"),
                new BulguCase("UAT_125",  "deha",            "DEHA INC.",              "",   "UAT Sanatçı"),
                new BulguCase("UAT_127",  "semicek",         "Semicenk",              "",   "UAT Sanatçı"),
                new BulguCase("UAT_129",  "taylor",          "Taylor Swift",          "",   "UAT Sanatçı"),
                new BulguCase("UAT_130",  "beyonce",         "Beyoncé",               "",   "UAT Sanatçı"),
                new BulguCase("UAT_131",  "emre gel",        "Emre Fel",              "",   "UAT Sanatçı"),
                new BulguCase("UAT_133",  "sibelcan",        "Sibel Can",             "",   "UAT Sanatçı"),
                new BulguCase("UAT_135",  "kofn",            "KÖFN",                  "",   "UAT Sanatçı"),
                new BulguCase("UAT_139",  "madonna",         "Madonna",               "",   "UAT Sanatçı"),
                new BulguCase("UAT_142",  "sertap",          "Sertab Erener",         "",   "UAT Sanatçı"),
                new BulguCase("UAT_146",  "mr ve ötei",      "Mor ve Ötesi",          "",   "UAT Sanatçı"),
                new BulguCase("UAT_147",  "dolu kadhi tut",  "Dolu Kadehi Ters Tut",  "",   "UAT Sanatçı"),
                new BulguCase("UAT_149",  "dktt",            "Dolu Kadehi Ters Tut",  "",   "UAT Sanatçı"),


                // ─────────────────────────────────────────────────────────────
                // UAT LYRİCS — Kullanıcı şarkı sözü/içeriğiyle arama yapıyor
                // (Arama terimi şarkı/sanatçı adıyla örtüşmüyor)
                // ─────────────────────────────────────────────────────────────
                new BulguCase("UAT_117",  "hadi ya",                         "Melis Kar",              "Yatıya",                   "UAT Lyrics"),
                new BulguCase("UAT_119",  "babalar",                         "Blok3",                  "PATLAT",                   "UAT Lyrics"),
                new BulguCase("UAT_128",  "sarışınlar",                      "Derya Uluğ",             "Esmerin Adı Oya",          "UAT Lyrics"),
                new BulguCase("UAT_136",  "silemez o beni",                  "Yıldız Tilbe",           "Dizine Dursun",            "UAT Lyrics"),
                new BulguCase("UAT_137",  "babalar sözünü tutar",            "Blok3",                  "PATLAT",                   "UAT Lyrics"),
                new BulguCase("UAT_143",  "çok geç şmdi",                    "Edis",                   "Yalan",                    "UAT Lyrics"),
                new BulguCase("UAT_144",  "affet bu gece istedim ölmek",     "Model",                  "Pembe Mezarlık",           "UAT Lyrics"),


                // ─────────────────────────────────────────────────────────────
                // UAT — ŞARKI ARAMALARI (TOPN_HAS_ARTIST_AND_TRACK)
                // Beklenti: sanatçı + şarkı top-10 içinde bulunmalı
                // ─────────────────────────────────────────────────────────────
                new BulguCase("UAT_011",  "nasır",                           "Melike Şahin",           "Nasır",                    "UAT Şarkı"),
                new BulguCase("UAT_015",  "yandım ah",                       "Sakiler",                "Yalanı Bırak",             "UAT Şarkı"),
                new BulguCase("UAT_017",  "sus",                             "Ceza",                   "Suspus",                   "UAT Şarkı"),
                new BulguCase("UAT_018",  "pus",                             "Ceza",                   "Suspus",                   "UAT Şarkı"),
                new BulguCase("UAT_019",  "bodrum",                          "Yüzyüzeyken Konuşuruz",  "Bodrum",                   "UAT Şarkı"),
                new BulguCase("UAT_022",  "bak ben yara gibiyim",            "Emir Can İğrek",         "Nalan",                    "UAT Şarkı"),
                new BulguCase("UAT_023",  "çölüme yağmur oldun",             "Müslüm Gürses",          "Affet",                    "UAT Şarkı"),
                new BulguCase("UAT_024",  "zaten aşklar hep yalan dolan",    "Yıldız Tilbe",           "Sana Değer",               "UAT Şarkı"),
                new BulguCase("UAT_034",  "sana hastayım anlasana",          "Derya Uluğ",             "Yansıma",                  "UAT Şarkı"),
                new BulguCase("UAT_054",  "illede sen",                      "Azer Bülbül",            "İlle De Sen",              "UAT Şarkı"),
                new BulguCase("UAT_068",  "arabam",                          "Sefo",                   "Araba",                    "UAT Şarkı"),
                new BulguCase("UAT_145",  "lacivert eceler",                 "Ferhat Göçer",           "Lacivert Geceler",         "UAT Şarkı"),
                new BulguCase("UAT_148",  "güldün ne güzel",                 "Pinhani",                "Ne Güzel Güldün",          "UAT Şarkı"),
                new BulguCase("UAT_141",  "kalbimin sahibine",               "İrem Derici",            "Kalbimin Tek Sahibine",    "UAT Şarkı"),
                new BulguCase("UAT_126",  "gözlerime bak",                   "Mert Demir",             "Gözlerime Bak",            "UAT Şarkı"),

                // ─────────────────────────────────────────────────────────────
                // UAT — PLAYLİST ARAMALARI (TOPN_RELATED_PLAYLIST)
                // Beklenti: ilgili playlist top-10 içinde olmalı (gözlem)
                // ─────────────────────────────────────────────────────────────
                new BulguCase("UAT_004",  "pop",              "",  "[Playlist] pop",          "UAT Playlist"),
                new BulguCase("UAT_005",  "90",               "",  "[Playlist] 90",           "UAT Playlist"),
                new BulguCase("UAT_006",  "90lar",            "",  "[Playlist] 90",           "UAT Playlist"),
                new BulguCase("UAT_007",  "90'lar",           "",  "[Playlist] 90",           "UAT Playlist"),
                new BulguCase("UAT_008",  "90s",              "",  "[Playlist] 90",           "UAT Playlist"),
                new BulguCase("UAT_009",  "90 lar",           "",  "[Playlist] 90",           "UAT Playlist"),
                new BulguCase("UAT_029",  "arabesk",          "",  "[Playlist] arabesk",      "UAT Playlist"),
                new BulguCase("UAT_031",  "ilahi",            "",  "[Playlist] ilahi",        "UAT Playlist"),
                new BulguCase("UAT_037",  "karadeniz",        "",  "[Playlist] karadeniz",    "UAT Playlist"),
                new BulguCase("UAT_040",  "halay",            "",  "[Playlist] halay",        "UAT Playlist"),
                new BulguCase("UAT_043",  "yabancı",          "",  "[Playlist] yabancı",      "UAT Playlist"),
                new BulguCase("UAT_044",  "roman",            "",  "[Playlist] roman",        "UAT Playlist"),
                new BulguCase("UAT_048",  "çocuk",            "",  "[Playlist] çocuk",        "UAT Playlist"),
                new BulguCase("UAT_065",  "oyun hava",        "",  "[Playlist] oyun hava",    "UAT Playlist"),
                new BulguCase("UAT_069",  "spor",             "",  "[Playlist] spor",         "UAT Playlist"),
                new BulguCase("UAT_073",  "klasik",           "",  "[Playlist] klasik",       "UAT Playlist"),
                new BulguCase("UAT_079",  "ankara",           "",  "[Playlist] ankara",       "UAT Playlist"),
                new BulguCase("UAT_082",  "akustik",          "",  "[Playlist] akustik",      "UAT Playlist"),
                new BulguCase("UAT_083",  "çocuk şarkıları",  "",  "[Playlist] çocuk",        "UAT Playlist"),
                new BulguCase("UAT_106",  "türkçe",           "",  "[Playlist] türkçe",       "UAT Playlist"),
                new BulguCase("UAT_108",  "80",               "",  "[Playlist] 80",           "UAT Playlist"),
                new BulguCase("UAT_116",  "rock",             "",  "[Playlist] rock",         "UAT Playlist"),
                new BulguCase("UAT_121",  "dans",             "",  "[Playlist] dans",         "UAT Playlist"),
                new BulguCase("UAT_122",  "türk sanat",       "",  "[Playlist] türk sanat",   "UAT Playlist"),
                new BulguCase("UAT_134",  "meditasyon",       "",  "[Playlist] meditasyon",   "UAT Playlist"),
                new BulguCase("UAT_138",  "90 lar pop",       "",  "[Playlist] pop",          "UAT Playlist"),
                new BulguCase("UAT_140",  "ramazan",          "",  "[Playlist] ramazan",      "UAT Playlist")
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

        System.out.printf("%-15s | %-38s | Konum: %-6s | %s%n",
                bc.caseId(), "\"" + bc.term() + "\"", posStr, bc.section());

        ROWS.add(new BulguSnapshotWriter.SnapshotRow(
                bc.caseId(), bc.term(),
                bc.expArtist(), bc.expTrack(),
                top10, foundAt, bc.section(),
                bulgAciklamasi(bc)
        ));
    }

    // =========================================================================
    // YARDIMCI METOTLAR
    // =========================================================================

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
                break;
            }
            names.add(display);
        }
        return names;
    }

    private int findPosition(JsonPath jp, String expArtist, String expTrack) {
        if (expTrack.isEmpty() && expArtist.isEmpty()) return 0;

        String base  = jp.get("topHits") != null ? "topHits" : "content";
        String expTl = expTrack.trim().toLowerCase(TR);
        String expAl = expArtist.trim().toLowerCase(TR);

        // Playlist araması: expTrack "[Playlist] <anahtar>" formatındaysa
        // playlistName alanında anahtar kelimeyi içeren ilk sonucu bul
        if (expTl.startsWith("[playlist] ")) {
            String keyword = expTl.substring("[playlist] ".length());
            for (int i = 0; i < TOP_N; i++) {
                String pl = safeStr(jp.getString(base + "[" + i + "].data.playlistName"));
                if (!pl.isEmpty() && pl.trim().toLowerCase(TR).contains(keyword)) {
                    return i + 1;
                }
            }
            return 0;
        }

        for (int i = 0; i < TOP_N; i++) {
            String song      = safeStr(jp.getString(base + "[" + i + "].data.songName"));
            String album     = safeStr(jp.getString(base + "[" + i + "].data.albumName"));
            String performer = safeStr(jp.getString(base + "[" + i + "].data.performerName"));

            // Spesifik şarkı aranırken ([expTl] dolu) albüm sonuçlarını match için sayma.
            // Albüm adı şarkı adıyla aynı olsa bile (ör. "Kömür" albümü ≠ "Kömür" şarkısı).
            boolean isAlbumResult = song.isEmpty() && !album.isEmpty();
            if (isAlbumResult && !expTl.isEmpty()) continue;

            String trackName = song.isEmpty() ? album : song;
            // Sondaki [Dedub Sessions], (Acoustic) gibi ek etiketleri silerek normalleştir
            String tl = normalizeTrack(trackName.trim().toLowerCase(TR));
            String pl = performer.trim().toLowerCase(TR);

            boolean trackOk  = expTl.isEmpty() || tl.equals(expTl);
            boolean artistOk = expAl.isEmpty() || pl.equals(expAl);

            // Bazı API yanıtlarında sanatçı ayrı alanda değil şarkı adına gömülüdür:
            // "Sen Yanlış Yaptın — Şahin Kendirci" → performer boş, song tek alan
            // "Mabel Matiz -Kömür"                 → performer boş, "Sanatçı -Şarkı" formatı
            // Bu durumda şarkı adını ayırıcıdan bölerek eşleştirmeyi her iki sırayla dene.
            if ((!trackOk || !artistOk) && pl.isEmpty() && !expAl.isEmpty()) {
                for (String sep : new String[]{" \u2014 ", " \u2013 ", " - ", " -", "- "}) {
                    int idx = tl.lastIndexOf(sep);
                    if (idx > 0) {
                        String left  = normalizeTrack(tl.substring(0, idx).trim());
                        String right = normalizeTrack(tl.substring(idx + sep.length()).trim());
                        // Sıra 1: "Şarkı <sep> Sanatçı"
                        if ((expTl.isEmpty() || left.equals(expTl)) && right.equals(expAl)) {
                            trackOk  = true;
                            artistOk = true;
                            break;
                        }
                        // Sıra 2: "Sanatçı <sep> Şarkı"  (ör: "Mabel Matiz -Kömür")
                        if ((expTl.isEmpty() || right.equals(expTl)) && left.equals(expAl)) {
                            trackOk  = true;
                            artistOk = true;
                            break;
                        }
                    }
                }
            }

            // [Sanatçı] kartında songName ve albumName boştur; yalnızca performerName gelir.
            // expTrack belirtilmemişse sanatçı kartı da geçerli bir eşleşmedir.
            boolean hasContent = !trackName.isEmpty()
                    || (!performer.isEmpty() && expTl.isEmpty());
            // Bazı API yanıtlarında performerName boş gelir ama şarkı adı tam eşleşir.
            // (Örn: "Kömür" → songName="Kömür", performerName="").
            // Bu veri kalitesi sorunudur; şarkı adı doğruysa sanatçısız da eşleşme kabul edilir.
            boolean performerMissing = performer.isEmpty() && !trackName.isEmpty() && !expTl.isEmpty();

            if (trackOk && (artistOk || performerMissing) && hasContent) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * Şarkı adının sonundaki versiyon/baskı eklerini temizler.
     * Örn: "sana güvenmiyorum [dedub sessions]" → "sana güvenmiyorum"
     *      "song title (acoustic)" → "song title"
     * Birden fazla ek varsa hepsini siler.
     */
    private static String normalizeTrack(String name) {
        String result = name.trim();
        String prev;
        do {
            prev = result;
            result = result.replaceAll("\\s*[\\[(][^\\]\\)]*[\\]\\)]\\s*$", "").trim();
        } while (!result.equals(prev));
        return result;
    }

    private static String safeStr(String s) { return s == null ? "" : s; }

    // =========================================================================
    // BULGU / UAT AÇIKLAMALARI
    // =========================================================================

    private static String bulgAciklamasi(BulguCase bc) {
        if (bc.caseId().startsWith("UAT_")) {
            return uatAciklamasi(bc);
        }
        return bulguAciklamasi(bc.caseId());
    }

    private static String uatAciklamasi(BulguCase bc) {
        if (!bc.expArtist().isEmpty() && !bc.expTrack().isEmpty()) {
            return "'" + bc.term() + "' araması için '" + bc.expArtist()
                    + "' – '" + bc.expTrack() + "' şarkısının top-" + TOP_N + " içinde bulunması bekleniyor.";
        } else if (!bc.expArtist().isEmpty()) {
            return "'" + bc.term() + "' araması için '" + bc.expArtist()
                    + "' sanatçısının ilk sırada gelmesi bekleniyor.";
        } else if (bc.expTrack().startsWith("[Playlist] ")) {
            String keyword = bc.expTrack().substring("[Playlist] ".length());
            return "'" + bc.term() + "' araması için '" + keyword
                    + "' kelimesini içeren bir playlist top-" + TOP_N + " içinde görünmeli.";
        } else {
            return "'" + bc.term() + "' araması için ilgili içerik (playlist/şarkı) bekleniyor — gözlem case'i.";
        }
    }

    private static String bulguAciklamasi(String caseId) {
        switch (caseId) {
            // ── BÖLÜM 1: İçerik Yok ──────────────────────────────────────────
            case "BULGU_001": return "Hermes yazdığımızda Batuflexin Hermès 2.0 şarkısını bulmuyor.";
            case "BULGU_002": return "a canım araması yapıldığında mabel matiz a canım şarkısını bulmuyor.";
            case "BULGU_003": return "acanım araması yapılınca mabel matiz a canım şarkısını bulmuyor.";
            case "BULGU_010": return "maraton yazınca Ati242 Maraton şarkısı çıkmıyor.";
            case "BULGU_013": return "meğerse yazıyorum, Liner meğerse şarkısını bulmuyor.";
            case "BULGU_014": return "çok pardon yazıyorum, Lvbel C5 COOOK PARDON şarkısını bulmadı.";
            case "BULGU_032": return "dame un grr araması yapılır aynı isimdeki şarkıyı bulamıyor.";
            case "BULGU_034": return "vidrado em yazıyorum ya da vidrado em voce yazıyorum Vidrado Em Você şarkısını bulamıyor.";
            case "BULGU_036": return "can efendim yazıyorum aynı isimli şarkıyı bulamıyor.";
            case "BULGU_037": return "çıt çıt yazıyorum aynı isimdeki şarkılar ilk 10da gelmiyor.";
            case "BULGU_038": return "çıt çıt çedene araması yapılır, çıt çıt şarkısı top-10da gelmiyor.";
            case "BULGU_040": return "hav hav yazıyorum Lvbel C5 havhavhav şarkısını bulmuyor.";
            case "BULGU_046": return "can ozan yazıyorum canozan sanatçısını bulamıyor.";
            case "BULGU_053": return "kök$l aramasında kök sanatçısını bulmuyor.";
            case "BULGU_054": return "just the way you are yazıyorum aynı isimli şarkılar ilk 10da çıkmıyor.";
            case "BULGU_057": return "y yazıyorum poizi Y şarkısını bulmuyor.";
            case "BULGU_058": return "y poizi yazıyorum Poizi Y şarkısını bulmuyor.";
            case "BULGU_062": return "yaramızda kalsın yazıyorum Merve Özbeyin klibi ilk sırada geliyor aynı sanatçının şarkısı aramada hiç çıkmıyor.";
            case "BULGU_110": return "lvc5 yazıldığında Lvbel C5 sanatçısının gelmesi beklenir ancak gelmiyor.";
            case "BULGU_115": return "pozi aramasında poizi sanatçısını bulması beklenir şuan bulamıyor.";
            case "BULGU_116": return "level c5 aramasında Lvbel C5 sanatçısını bulması beklenir.";
            case "BULGU_117": return "levelc5 aramasında Lvbel C5 sanatçısını bulması beklenir.";
            // ── BÖLÜM 2: Sıralama ────────────────────────────────────────────
            case "BULGU_004": return "olmazlara vuruluyorum araması yapıldığında albüm sonucu şarkıdan önce geliyor.";
            case "BULGU_005": return "çıkmaz bir sokakta araması yapınca aynı şarkının albümü daha önde görünüyor.";
            case "BULGU_011": return "geri ver araması yapıyorum, Wegh geri ver şarkısı ilk sırada gelmiyor.";
            case "BULGU_012": return "saygımdan araması yapıyorum Bengü saygımdan şarkısı ilk sırada değil albüm ilk sırada.";
            case "BULGU_015": return "dacia yazıyorum Lvbel C5 Dacia şarkısını ilk sırada getirmiyor.";
            case "BULGU_020": return "yalnızlığın çaresini bulmuşlar yazıyorum şarkı yerine albüm en üstte geliyor.";
            case "BULGU_023": return "yapar mısın yazıyorum Poizi yapar mısın şarkısı ilk sırada gelmiyor.";
            case "BULGU_024": return "yerinde yazıyorum Sefo Yerinde Dur şarkısı ilk sırada gelmiyor, başka sanatçı öne geçiyor.";
            case "BULGU_025": return "yerinde dur yazıyorum Bora Duran sanatçısı ilk sırada geliyor, Sefo Yerinde Dur şarkısı ilk sırada değil.";
            case "BULGU_028": return "ey aşk yazıyorum sezen aksu ey aşk şarkısı birebir eşleşmesine rağmen ilk sırada gelmiyor eypio sanatçısı ilk sırada geliyor.";
            case "BULGU_031": return "giderim kırağınan araması yapılıyor birebir eşleşmesine rağmen Onur Şan - giderim kırağınan şarkısı 6-7. sırada geliyor.";
            case "BULGU_033": return "ara beni lütfen araması yapılır aynı isimdeki şarkı 2. sırada geliyor Funda Arar ilk sırada geliyor.";
            case "BULGU_035": return "aşk yok olmaktır araması yapılır birebir eşleşen şarkı 2. sırada çıkmaktadır ilk sırada başka içerik geliyor.";
            case "BULGU_039": return "çıkar biri karşıma yazıyorum aynı isimli Poizi şarkısı 4. sırada çıkıyor.";
            case "BULGU_041": return "messy lola young yazıyorum, lola young sanatçısına ait messy şarkısı 9. sırada geliyor.";
            case "BULGU_042": return "sen yanlış yaptın yazıyorum aynı isimli şahin kendirci şarkısı ilk 5te gelmiyor.";
            case "BULGU_043": return "vay dayı yazıyorum aynı isimli aynur polat şarkısı 4. sırada geliyor.";
            case "BULGU_044": return "silinmez yazıyorum aynı isimli mansur ark şarkısı 2. sırada çıkıyor.";
            case "BULGU_045": return "halbuki yazıyorum aynı isimli yalın şarkısı 8. sırada geliyor.";
            case "BULGU_047": return "duydun mu yazıyorum aynı isimli yusuf güney şarkısı ilk sırada çıkmıyor.";
            case "BULGU_048": return "sana güvenmiyorum yazıyorum aynı isimli dedüblüman şarkısı 3. sırada çıkıyor.";
            case "BULGU_049": return "yasemen yazıyorum aynı isimli afra şarkısı ilk 15te görünmüyor hep sanatçı buluyor.";
            case "BULGU_050": return "düşer o yazıyorum aynı isimli izel şarkısı çok aşağı sıralarda yer alıyor.";
            case "BULGU_051": return "kömür yazıyorum mabel matiz kömür şarkısı ilk 10da gelmiyor.";
            case "BULGU_052": return "mabel kömür yazıyorum mabel matiz kömür şarkısı 4. sırada geliyor.";
            case "BULGU_056": return "snap yazıyorum aynı isimli manifest şarkısı 7. sırada geliyor.";
            case "BULGU_059": return "ama başaramadım yazıyorum aynı isimli burak bulut şarkısı 2. sırada geliyor.";
            case "BULGU_060": return "kts manifest yazıyorum manifestin kts şarkısı 2. sırada geliyor.";
            case "BULGU_061": return "adına bir çizik çektim yazıyorum aynı isme sahip şarkı 4. sırada geliyor.";
            case "BULGU_063": return "sev yeter yazıyorum birebir eşleşen şarkılar 4. ve 5. sırada geliyor.";
            case "BULGU_065": return "kaybolurum gülüşünde araması yapılıyor İkilem sanatçısının şarkısından önce klip ve albümü geliyor.";
            case "BULGU_066": return "bak ben yara gibiyim araması yapıyorum, Nalan şarkısının 1. sırada çıkması beklenirken sanatçı 2. sırada şarkı 3. sırada çıkıyor.";
            case "BULGU_071": return "erik yazıyorum erik dalı şarkısı ilk sırada çıkması gerekirken şarkının klibi ilk sırada çıkıyor.";
            case "BULGU_072": return "ağlama ben ağlarım araması yapıyorum, Canozan şarkısı 6. sırada çıkıyor ve aynı isimli albümden daha aşağıda.";
            case "BULGU_074": return "karakedi yazıyorum Melis Fis sanatçısını ilk sırada çıkarıyor ancak Kara Kedi şarkısını 6. sırada getiriyor.";
            case "BULGU_077": return "şikayetim var araması yapılır, aynı isimde şarkı olmasına rağmen ilk sırada Various Artist isminde bir sanatçı gelmektedir.";
            case "BULGU_080": return "düldül araması yapılır, birebir eşleşen Mabel Matiz şarkısı ilk sıralarda yer almıyor.";
            case "BULGU_081": return "bunca yıl araması yapılır Dedüblümanın aynı isimli şarkısı 5. sırada çıkmaktadır.";
            case "BULGU_082": return "düldül araması öneriden tıklanır; Düldül şarkısı 4. sırada çıkar ilk sırada çıkmalıydı.";
            case "BULGU_086": return "perde araması yapılır, Poizi perde şarkısı popüler olmasına ve birebir eşleşmesine rağmen 5. sırada gelmektedir.";
            case "BULGU_092": return "sonbahar yazıyorum Era7Capone sanatçısının SONBAHAR şarkısı çok alt sıralarda geliyor ilk 3te görünmesi beklenir.";
            case "BULGU_093": return "acem kızı yazıyorum birebir eşleşen şarkılar olmasına rağmen ilk sırada Fikret Kızılok sanatçısı gösteriliyor.";
            case "BULGU_094": return "Hacel obası yazıyorum ilk sırada klipler geliyor.";
            case "BULGU_095": return "yalan yazıyorum ilk sırada Majid Yalan isminde bir sanatçı geliyor, şarkıların gelmesi önemli.";
            case "BULGU_096": return "bana sor yazıyorum aynı isme sahip şarkılar olmasına rağmen ilk sırada ferdi tayfur sanatçısı çıkıyor.";
            case "BULGU_097": return "rüya manifest yazıyorum, aynı isimli manifest şarkısı 6. sırada geliyor. İlk sırada manifest sanatçısı ve başka sanatçıların aynı isimli şarkısı geliyor.";
            case "BULGU_098": return "rüya yazıyorum manifestin aynı isimli şarkısı ilk 10 sonuçta gelmiyor.";
            case "BULGU_100": return "ara kelimesi yazılır ilk sırada birebir eşleşen zeynep bastık ara şarkısı çıkması beklenirken Funda Arar sanatçısı çıkmaktadır.";
            case "BULGU_101": return "14 bahar araması yapılır aynı isme sahip Mert Demir şarkısı 9. sırada çıkmaktadır.";
            case "BULGU_103": return "ela mana yazıyorum, birebir eşleşen şarkı 8. sırada geliyor onun önündeki tüm sonuçlar sanatçı ve birebir eşleşmeyen sonuçlar.";
            case "BULGU_105": return "erik dalı yazıyorum ilk sonuçlar klip geliyor.";
            case "BULGU_106": return "elfida yazıyorum birebir eşleşen şarkı 2. sırada geliyor ilk sırada Gelida isminde bir sanatçı geliyor.";
            case "BULGU_107": return "yazan kalem siyah araması yapıyorum ilk 20 sonuçta birebir eşleşmesine rağmen bir şarkı sonucu gelmiyor.";
            case "BULGU_111": return "merdo yazıldığında birebir eşleşen şarkıların daha üst sırada gelmesi beklenir, birebir eşleşmeyen sanatçı üstte gelmektedir.";
            case "BULGU_121": return "misket yazıyorum birebir eşleşen şarkı en üstte yer almıyor.";
            case "BULGU_122": return "kara sevda yazıyorum birebir eşleşen şarkı 4. sırada çıkıyor.";
            case "BULGU_123": return "parla yazıyorum birebir eşleşen şarkı üst sıralarda çıkmıyor sadece sanatçılar üstte çıkıyor.";
            case "BULGU_124": return "kırmızı balık yazıyorum ilk sırada Mahsun Kırmızıgül çıkıyor birebir eşleşen başka bir şarkı olmasına rağmen.";
            // ── BÖLÜM 3: Lyric Arama ─────────────────────────────────────────
            case "BULGU_067": return "çölüme yağmur oldun araması yapıyorum müslüm gürses affet şarkısı gelmiyor.";
            case "BULGU_068": return "sana hastayım anlasana yazıyorum Derya Uluğ Yansıma şarkısı ilk sırada gelmiyor.";
            case "BULGU_070": return "Dua Lipa Shine öneriye tıklanır böyle bir içerik getirmiyor şarkı bulmuyor.";
            case "BULGU_075": return "hadi ya araması yapılır melis kar yatıya şarkısının çıkmasını bekliyoruz.";
            case "BULGU_076": return "babalar araması yapılır Blok3-PATLAT şarkısının ilk sırada gelmesi beklenir.";
            case "BULGU_079": return "silemez o beni yazıyorum, Yıldız Tilbe sanatçısına ait Dizine Dursun şarkısının ilk sırada olması beklenir.";
            case "BULGU_083": return "çetin ceviz şerbetli mayam araması yapılır şarkı sözlerinden Melike Şahin - Canın Beni Çekti şarkısı bulamuyor.";
            case "BULGU_084": return "bir ya da bir motive araması yapıldığında Motive sanatçısının bir şarkısını bulamıyor.";
            // ── BÖLÜM 4: Sanatçı Sıralama ────────────────────────────────────
            case "BULGU_008": return "mfö yazınca mfö sanatçısı ilk sırada çıkmıyor.";
            case "BULGU_009": return "mfo yazınca mfö sanatçısı ilk sırada çıkmıyor.";
            case "BULGU_016": return "Manifest yazıyorum manifest sanatçısı en üstte çıkarmıyor.";
            case "BULGU_017": return "semicenk araması yapılır — her iki yazım (semicenk/Semicenk) birebir aynı sonuçları getiriyor, case sensitivity sorunu yoktur. Ancak Semicenk şarkıları sanatçı kartlarının ardında 7. sıraya düşüyor.";
            case "BULGU_055": return "utku akkaya yazıyorum aynı isimli birebir eşleşen sanatçı olmasına rağmen ilkay akkaya ilk sırada çıkıyor.";
            case "BULGU_088": return "derya bedavacı araması yapıyorum aynı isme sahip sanatçı 3. sırada çıkıyor Derya Uluğ ilk sırada çıkıyor.";
            case "BULGU_090": return "ceza yazıyorum Ceza sanatçısı ilk sırada çıkıyor, Ceza büyük harfle yazıyorum Ceza sanatçısı 2. sırada çıkıyor.";
            case "BULGU_091": return "Ceza büyük harfle yazıldığında sanatçı 2. sırada çıkıyor (case sensitivity sorunu).";
            case "BULGU_099": return "çakal araması yapıldığında önerilerde çakal keywordü çıkmalıdır, bu isimdeki sanatçı için bu aramalar yapılır.";
            case "BULGU_102": return "yaşar araması yapıldığında sanatçı Yaşar yerine ilk sırada Ebru Yaşar çıkmaktadır.";
            case "BULGU_112": return "Gökhan Özen yazılır birebir eşleşen sanatçı 2. sırada gelmektedir ilk sırada Gökhan Türkmen gelmesi hatalıdır.";
            case "BULGU_118": return "çelik yazıyorum sanatçı çelik 2. sırada çıkıyor birebir eşleşmesine rağmen. İlk sırada Ayla Çelik geliyor.";
            case "BULGU_119_B": return "Haluk Levent yazıyorum ilk sırada Levent Yüksel çıkıyor. Birebir eşleşen sanatçı ilk sırada gelmeli.";
            case "BULGU_120": return "Mustafa Yıldızdoğan araması yapıyorum 2. ve 3. sırada ismi mustafa olan başka sanatçılar geliyor alakasız görünüyor.";
            // ── BÖLÜM 5: Tolerans ────────────────────────────────────────────
            case "BULGU_006": return "blok araması yapınca sanatçı blok3 ilk sırada gelmiyor bulması beklenirdi.";
            case "BULGU_007": return "kusura bakma yazınca blok 3 kusura bakma şarkısını ilk sıralarda çıkmıyor. Çok dinlenen bir şarkı ilk sırada çıkması beklenir.";
            case "BULGU_021": return "tarkn yazıyorum tarkan olduğunu anlıyor arama yapıyor ancak tarkan sanatçısını en üstte göstermiyor.";
            case "BULGU_029": return "simarik araması yapılır — Tarkan Şımarık 1. sırada doğru geliyor. Ancak 2-10. sıralarda Simpatik Music sanatçı kartları doluyor: fonetik benzerlik (simarik ~ simpatik) yanlış eşleşmeye yol açıyor.";
            case "BULGU_030": return "şımarık araması yapılır — Tarkan Şımarık 1. sırada doğru geliyor. Devamındaki sonuçlar da şımarık içeren cover ve albüm sonuçlar — kabul edilebilir.";
            // ── BÖLÜM 6: Playlist ─────────────────────────────────────────────
            case "BULGU_026": return "90 ya da 90 lar araması yapıyorum. Bu isimdeki listeler ilk sıralarda gelmiyor. Hatta hiç liste sonucu gelmiyor.";
            case "BULGU_027": return "çocuk yazıyorum arama sonuçlarında çocuklarla ilgili çalma listeleri hiç gelmiyor.";
            case "BULGU_064": return "pop yazıyorum, pop ile ilgili çalma listeleri gelmiyor.";
            case "BULGU_069": return "yabancı yazıyorum yabancı playlistler gelmiyor.";
            case "BULGU_087": return "akustik aramasında ilk 6 sonuç sanatçı görünmektedir. Akustik playlist ve şarkıların önce gelmesi beklenir.";
            // ── BÖLÜM 7: Auto-Correct ─────────────────────────────────────────
            case "BULGU_073": return "arabam diye arama yapıyorum yanlış yazdığımı düzeltiyor ve graham kelimesiyle arama yapıyor.";
            case "BULGU_078": return "doğuştan yazıyorum doğuka aramasıyla düzeltiyor. Lvbel C5 Doğuştan Beri Haklıyım şarkısının çıkması beklenir.";
            case "BULGU_104": return "yekten araması yapılır, yetkin (Türkçe yakın kelime) olarak düzeltilip Mehmet Yetkin gibi sanatçılar öne çıkıyor. Yekten şarkısı (Demet Akalın) 2. sırada mevcut ancak sanatçı kartları önüne geçiyor.";
            case "BULGU_113": return "sigara araması yapılır ancak yanlış yazıldığını zannederek sitara ile düzeltilip o şekilde arama yapılmaktadır.";
            case "BULGU_114": return "dandini araması yapıyorum dancing olarak düzeltiyor.";
            case "BULGU_119":  return "hsdra aramasını hidra olarak düzeltmesini beklerdik ancak had a olarak düzeltiyor.";
            case "BULGU_119_C": return "farzet araması yapılır farmer diye düzeltildiği için istenen sonuç çıkmamaktadır.";
            // ── BÖLÜM 8: Öneri Gözlem ────────────────────────────────────────
            case "BULGU_019": return "yalnızlığın çaresini yazıyorum önerilerde aynı keyword 3 defa çoklanıyor.";
            case "BULGU_022": return "teo yazıyorum önerilerde 2 defa Teoman keywordü geliyor.";
            case "BULGU_089": return "phonk araması yapılır ilk 10-15 sonuçta hiç şarkı çıkmıyor hep sanatçı çıkıyor.";
            // ── BÖLÜM 9: Case Sensitivity ─────────────────────────────────────
            case "BULGU_108": return "Mihriban araması yapılır. Büyük/küçük harf (Mihriban/mihriban) farkı sonuçları etkilemiyor — aynı top-10 dönüyor. Case sensitivity sorunu tespit edilmedi.";
            default: return "";
        }
    }
}
