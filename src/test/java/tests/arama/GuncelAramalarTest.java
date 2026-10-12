package tests.arama;

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
 * Eylül–Ekim 2026 gerçek kullanıcı verisi + popüler kült şarkılar tabanlı
 * arama kalitesi test seti.  ~450 case — sanatçı · şarkı · playlist · lyrics
 * · yazım toleransı · boşluk · noktalama · karışık klavye girişleri.
 *
 * Test HİÇBİR ZAMAN fail etmez — saf gözlem & Excel raporu üretir.
 *
 * Çalıştır:
 *   mvn test -Dtest=GuncelAramalarTest
 * Çıktı:
 *   reports/guncel-aramalar/TestReport_dd.MM.yyyy_HH.mm_ORTAM.xlsx
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GuncelAramalarTest extends TestConfig {

    private static final int TOP_N = 10;
    private static final Locale TR = Locale.forLanguageTag("tr");

    // Bölüm sabitleri
    private static final String S_SARKI_TAM    = "Şarkı · Tam Eşleşme";
    private static final String S_SARKI_KISMI  = "Şarkı · Kısmi Ad";
    private static final String S_SARKI_SANAT  = "Şarkı · Sanatçı + Şarkı";
    private static final String S_SARKI_HARF   = "Şarkı · Yazım · Harf Hatası";
    private static final String S_SARKI_DIA    = "Şarkı · Yazım · Diakritik";
    private static final String S_SARKI_BOSL   = "Şarkı · Yazım · Boşluk";
    private static final String S_SARKI_NOKT   = "Şarkı · Yazım · Noktalama";
    private static final String S_SARKI_LYRICS = "Şarkı · Lyrics";
    private static final String S_SANAT_TAM    = "Sanatçı · Tam Eşleşme";
    private static final String S_SANAT_KISMI  = "Sanatçı · Kısmi Ad";
    private static final String S_SANAT_HARF   = "Sanatçı · Yazım · Harf Hatası";
    private static final String S_SANAT_DIA    = "Sanatçı · Yazım · Diakritik";
    private static final String S_SANAT_BOSL   = "Sanatçı · Yazım · Boşluk";
    private static final String S_SANAT_ALIAS  = "Sanatçı · Kısaltma / Alias";
    private static final String S_PLAYLIST     = "Playlist";

    private static final List<TestResultRow> ROWS = new ArrayList<>();
    private static MuudSearchApi api;

    @BeforeAll
    static void init() {
        api = new MuudSearchApi();
        System.out.println("✅ GuncelAramalarTest başlatıldı — top-" + TOP_N + " değerlendirilecek.");
    }

    @AfterAll
    static void writeReport() {
        System.out.printf("%n📋 Toplam %d case işlendi.%n", ROWS.size());
        String ortam = MuudSearchApi.ortam();
        ExcelTestReportWriter.writeBulgu(ROWS, "reports/guncel-aramalar", ortam, "GuncelAramalarTest");
        analyzer.BulguAnalyzer.annotateLatest("reports/guncel-aramalar", ortam);
    }

    public record BulguCase(String caseId, String term, String expArtist, String expTrack, String section, int topN) {
        BulguCase(String caseId, String term, String expArtist, String expTrack, String section) {
            this(caseId, term, expArtist, expTrack, section, 5);
        }
    }

    public static Stream<BulguCase> cases() {
        return Stream.of(

            // BÖLÜM 1 — ŞARKI TAM EŞLEŞme ===================================

            // Manifest (21k+ arama)
            new BulguCase("YG_SARKI_TAM_1",  "manifest",               "Manifest",         "Manifest",                   S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_2",  "Manifest",               "Manifest",         "Manifest",                   S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_3",  "MANIFEST",               "Manifest",         "Manifest",                   S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_4",  "hayır",                  "Manifest",         "Hayır",                      S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_5",  "Hayır",                  "Manifest",         "Hayır",                      S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_6",  "HAYIR",                  "Manifest",         "Hayır",                      S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_7",  "arıyo",                  "Manifest",         "Arıyo",                      S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_8",  "hileli",                 "Manifest",         "Hileli",                     S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_9",  "toz pembe",              "Manifest",         "Toz Pembe",                  S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_10", "zamansızdık",            "Manifest",         "Zamansızdık",                S_SARKI_TAM, 3),

            // Blok3 (6k+ arama)
            new BulguCase("YG_SARKI_TAM_11", "kusura bakma",           "Blok3",            "Kusura Bakma",               S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_12", "patlat",                 "Blok3",            "PATLAT",                     S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_13", "takip", "Motive", "Takip", S_SARKI_TAM, 3),

            // Hadise
            new BulguCase("YG_SARKI_TAM_14", "benim olsana",           "Crush",            "Benim Olsana",               S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_15", "düm tek tek",            "Hadise",           "Düm Tek Tek",                S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_16", "sıfır tolerans",         "Hadise",           "Sıfır Tolerans",             S_SARKI_TAM, 3),

            // Simge
            new BulguCase("YG_SARKI_TAM_17", "gamsız hayat",           "Candan Erçetin",   "Gamsız Hayat",               S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_18", "üzülmedin mi?", "Simge", "Üzülmedin mi?", S_SARKI_TAM, 3),

            // Ahmet Kaya — kült
            new BulguCase("YG_SARKI_TAM_19", "hep sonradan", "Ahmet Kaya", "Hep Sonradan", S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_20", "beni vur", "Ahmet Kaya", "Beni Vur", S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_21", "kum gibi",               "Ahmet Kaya",       "Kum Gibi",                   S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_22", "yakamoz", "Ahmet Kaya", "Yakamoz", S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_23", "başım belada",           "Ahmet Kaya",       "Başım Belada",               S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_24", "doruklara sevdalandım", "Ahmet Kaya", "Doruklara Sevdalandım", S_SARKI_TAM, 3),

            // Sezen Aksu — kült
            new BulguCase("YG_SARKI_TAM_25", "hadi bakalım",           "Sezen Aksu",       "Hadi Bakalım",               S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_26", "firuze",                 "Sezen Aksu",       "Firuze",                     S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_27", "kaçak",                  "Sezen Aksu",       "Kaçak",                      S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_28", "sen ağlama",             "Sezen Aksu",       "Sen Ağlama",                 S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_29", "bu son olsun", "Cem Karaca", "Bu Son Olsun", S_SARKI_TAM, 3),

            // Müslüm Gürses — kült
            new BulguCase("YG_SARKI_TAM_30", "affet",                  "Müslüm Gürses",    "Affet",                      S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_31", "alışırım", "Müslüm Gürses", "Alışırım", S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_32", "öpmek istedim", "Müslüm Gürses", "Öpmek İstedim", S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_33", "ağlama değmez",          "Müslüm Gürses",    "Ağlama Değmez",              S_SARKI_TAM, 3),

            // Tarkan
            new BulguCase("YG_SARKI_TAM_34", "şımarık",                "Tarkan",           "Şımarık",                    S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_35", "kuzu kuzu",              "Tarkan",           "Kuzu Kuzu",                  S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_36", "dudu",                   "Tarkan",           "Dudu",                       S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_37", "bounce",                 "Tarkan",           "Bounce",                     S_SARKI_TAM, 3),

            // Sagopa Kajmer — kült
            new BulguCase("YG_SARKI_TAM_38", "bir pesimistin gözyaşları", "Sagopa Kajmer", "Bir Pesimistin Gözyaşları",  S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_39", "baytar", "Sagopa Kajmer", "Baytar", S_SARKI_TAM, 3),

            // İbrahim Tatlıses
            new BulguCase("YG_SARKI_TAM_41", "mavi mavi", "İbrahim Tatlıses", "Mavi Mavi", S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_42", "mutlu ol yeter", "İbrahim Tatlıses", "Mutlu Ol Yeter", S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_43", "tamam aşkım", "İbrahim Tatlıses", "Tamam Aşkım", S_SARKI_TAM, 3),

            // Ferdi Tayfur — kült
            new BulguCase("YG_SARKI_TAM_44", "bana sor",               "Ferdi Tayfur",     "Bana Sor",                   S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_45", "sevda yelleri", "Ferdi Tayfur", "Sevda Yelleri", S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_46", "garip", "Ferdi Tayfur", "Garip", S_SARKI_TAM, 3),

            // Orhan Gencebay — kült
            new BulguCase("YG_SARKI_TAM_47", "batsın bu dünya",        "Orhan Gencebay",   "Batsın Bu Dünya",            S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_48", "yalnızlar rıhtımı",      "Erkin Koray",      "Yalnızlar Rıhtımı",          S_SARKI_TAM, 3),

            // Kayahan — kült
            new BulguCase("YG_SARKI_TAM_50", "elmanın yarısı", "Kayahan", "Elmanın Yarısı", S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_51", "yemin ettim", "Kayahan", "Yemin Ettim", S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_52", "gözlerinin hapsindeyim", "Kayahan", "Gözlerinin Hapsindeyim", S_SARKI_TAM, 3),

            // İbrahim Erkal
            new BulguCase("YG_SARKI_TAM_53", "sırılsıklam", "İbrahim Erkal", "Sırılsıklam", S_SARKI_TAM, 3),

            // Azer Bülbül
            new BulguCase("YG_SARKI_TAM_54", "oğlan şibidik",          "Azer Bülbül",      "Oğlan Şibidik",              S_SARKI_TAM, 3),

            // Yıldız Tilbe
            new BulguCase("YG_SARKI_TAM_55", "sana değer",             "Yıldız Tilbe",     "Sana Değer",                 S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_56", "vazgeçtim", "Yıldız Tilbe", "Vazgeçtim", S_SARKI_TAM, 3),

            // Ezhel
            new BulguCase("YG_SARKI_TAM_57", "geceler",                "Ezhel",            "Geceler",                    S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_58", "müptezhel",              "Ezhel",            "Müptezhel",                  S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_59", "bana ne bu sevdalardan", "Ezhel",            "Bana Ne Bu Sevdalardan",     S_SARKI_TAM, 3),

            // Wegh
            new BulguCase("YG_SARKI_TAM_60", "geri ver",               "Wegh",             "Geri Ver",                   S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_61", "kennedy'i ben vurdum",  "Wegh",             "Kennedy'i Ben Vurdum",      S_SARKI_TAM, 3),

            // Uzi
            new BulguCase("YG_SARKI_TAM_62", "vursunlar",              "Uzi",              "Vursunlar",                  S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_63", "sebebi yar", "Blok3", "Sebebi Yar", S_SARKI_TAM, 3),

            // Bergen — kült arabesk
            new BulguCase("YG_SARKI_TAM_64", "acıların kadını",        "Bergen",           "Acıların Kadını",            S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_65", "seni kalbimden kovdum",  "Bergen",           "Seni Kalbimden Kovdum",      S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_66", "onu da yak tanrım",      "Bergen",           "Onu Da Yak Tanrım",          S_SARKI_TAM, 3),

            // Cem Karaca — kült
            new BulguCase("YG_SARKI_TAM_67", "resimdeki gözyaşları",   "Cem Karaca",       "Resimdeki Gözyaşları",       S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_68", "tamirci çırağı",         "Cem Karaca",       "Tamirci Çırağı",             S_SARKI_TAM, 3),

            // Manga
            new BulguCase("YG_SARKI_TAM_69", "beni benimle bırak",     "Manga",            "Beni Benimle Bırak",         S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_70", "şehr-i hüzün",           "Manga",            "Şehr-i Hüzün",               S_SARKI_TAM, 3),

            // Duman
            new BulguCase("YG_SARKI_TAM_71", "aman aman",              "Duman",            "Aman Aman",                  S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_72", "her şeyi yak",           "Duman",            "Her Şeyi Yak",               S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_73", "haberin yok ölüyorum",   "Duman",            "Haberin Yok Ölüyorum",       S_SARKI_TAM, 3),

            // Crush
            new BulguCase("YG_SARKI_TAM_74", "crush",                  "Crush",            "Crush",                      S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_75", "Crush",                  "Crush",            "Crush",                      S_SARKI_TAM, 3),

            // Semicenk
            new BulguCase("YG_SARKI_TAM_76", "düşer aklıma",           "Semicenk",         "Düşer Aklıma",               S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_77", "masal gibi",             "Semicenk",         "Masal Gibi",                 S_SARKI_TAM, 3),

            // Selda Bağcan — kült
            new BulguCase("YG_SARKI_TAM_78", "uzun ince bir yoldayım", "Selda Bağcan",     "Uzun İnce Bir Yoldayım",     S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_79", "oy nazlı gelin",         "Selda Bağcan",     "Oy Nazlı Gelin",             S_SARKI_TAM, 3),

            // Barış Manço — kült
            new BulguCase("YG_SARKI_TAM_80", "gülpembe",               "Barış Manço",      "Gülpembe",                   S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_81", "sarı çizmeli mehmet ağa","Barış Manço",      "Sarı Çizmeli Mehmet Ağa",    S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_82", "halil ibrahim sofrası",  "Barış Manço",      "Halil İbrahim Sofrası",      S_SARKI_TAM, 3),

            // Gülşen
            new BulguCase("YG_SARKI_TAM_83", "be adam",                "Gülşen",           "Be Adam",                    S_SARKI_TAM, 3),
            new BulguCase("YG_SARKI_TAM_84", "bangır bangır",          "Gülşen",           "Bangır Bangır",              S_SARKI_TAM, 3),

            // Demet Akalın
            new BulguCase("YG_SARKI_TAM_85", "tatil", "Demet Akalın", "Tatil", S_SARKI_TAM, 3),

            // BÖLÜM 2 — ŞARKI KISMİ AD ======================================

            new BulguCase("YG_KISMI_1",  "bir pesimistin",             "Sagopa Kajmer",    "Bir Pesimistin Gözyaşları",  S_SARKI_KISMI, 3),
            new BulguCase("YG_KISMI_2",  "batsin bu",                  "Orhan Gencebay",   "Batsın Bu Dünya",            S_SARKI_KISMI, 3),
            new BulguCase("YG_KISMI_3",  "yalnızlar",                  "Erkin Koray",      "Yalnızlar Rıhtımı",          S_SARKI_KISMI, 3),
            new BulguCase("YG_KISMI_4",  "toz pembe",                  "Manifest",         "Toz Pembe",                  S_SARKI_KISMI, 3),
            new BulguCase("YG_KISMI_6",  "uzun ince",                  "Selda Bağcan",     "Uzun İnce Bir Yoldayım",     S_SARKI_KISMI, 3),
            new BulguCase("YG_KISMI_7",  "beni benimle",               "Manga",            "Beni Benimle Bırak",         S_SARKI_KISMI, 3),
            new BulguCase("YG_KISMI_8",  "gülpembe",                   "Barış Manço",      "Gülpembe",                   S_SARKI_KISMI, 3),
            new BulguCase("YG_KISMI_10", "masal",                      "Semicenk",         "Masal Gibi",                 S_SARKI_KISMI, 3),
            new BulguCase("YG_KISMI_11", "müptezhel",                  "Ezhel",            "Müptezhel",                  S_SARKI_KISMI, 3),
            new BulguCase("YG_KISMI_12", "benim olsana",               "Crush",            "Benim Olsana",               S_SARKI_KISMI, 3),
            new BulguCase("YG_KISMI_14", "resimdeki göz",              "Cem Karaca",       "Resimdeki Gözyaşları",       S_SARKI_KISMI, 3),
            new BulguCase("YG_KISMI_15", "acıların",                   "Bergen",           "Acıların Kadını",            S_SARKI_KISMI, 3),
            new BulguCase("YG_KISMI_16", "gamsız",                     "Candan Erçetin",   "Gamsız Hayat",               S_SARKI_KISMI, 3),
            new BulguCase("YG_KISMI_17", "kennedy",                    "Wegh",             "Kennedy'i Ben Vurdum",      S_SARKI_KISMI, 3),
            new BulguCase("YG_KISMI_18", "vursunlar",                  "Uzi",              "Vursunlar",                  S_SARKI_KISMI, 3),
            new BulguCase("YG_KISMI_19", "düşer",                      "Semicenk",         "Düşer Aklıma",               S_SARKI_KISMI, 3),
            new BulguCase("YG_KISMI_20", "firuze",                     "Sezen Aksu",       "Firuze",                     S_SARKI_KISMI, 3),
            new BulguCase("YG_KISMI_21", "aman aman",                  "Duman",            "Aman Aman",                  S_SARKI_KISMI, 3),

            // BÖLÜM 3 — SANATÇI + ŞARKI BİRLEŞİK ARAMALAR ==================

            new BulguCase("YG_SANAT_1",  "manifest hayır",             "Manifest",         "Hayır",                      S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_2",  "manifest arıyo",             "Manifest",         "Arıyo",                      S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_3",  "manifest toz pembe",         "Manifest",         "Toz Pembe",                  S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_4", "ahmet kaya hep sonradan", "Ahmet Kaya", "Hep Sonradan", S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_5", "ahmet kaya beni vur", "Ahmet Kaya", "Beni Vur", S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_6",  "sezen firuze",               "Sezen Aksu",       "Firuze",                     S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_7",  "müslüm affet",               "Müslüm Gürses",    "Affet",                      S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_8",  "tarkan şımarık",             "Tarkan",           "Şımarık",                    S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_9",  "tarkan dudu",                "Tarkan",           "Dudu",                       S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_10", "sagopa bir pesimistin",      "Sagopa Kajmer",    "Bir Pesimistin Gözyaşları",  S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_11", "orhan batsın bu dünya",      "Orhan Gencebay",   "Batsın Bu Dünya",            S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_12", "ferdi bana sor",             "Ferdi Tayfur",     "Bana Sor",                   S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_13", "ezhel geceler",              "Ezhel",            "Geceler",                    S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_14", "duman aman aman",            "Duman",            "Aman Aman",                  S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_15", "manga beni benimle bırak",   "Manga",            "Beni Benimle Bırak",         S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_16", "kayahan elmanın yarısı", "Kayahan", "Elmanın Yarısı", S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_17", "bergen acıların kadını",     "Bergen",           "Acıların Kadını",            S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_18", "barış manço gülpembe",       "Barış Manço",      "Gülpembe",                   S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_19", "crush benim olsana",         "Crush",            "Benim Olsana",               S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_20", "wegh geri ver",              "Wegh",             "Geri Ver",                   S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_21", "uzi vursunlar",              "Uzi",              "Vursunlar",                  S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_22", "semicenk masal gibi",        "Semicenk",         "Masal Gibi",                 S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_23", "blok3 kusura bakma",         "Blok3",            "Kusura Bakma",               S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_24", "selda uzun ince yol",        "Selda Bağcan",     "Uzun İnce Bir Yoldayım",     S_SARKI_SANAT, 3),
            new BulguCase("YG_SANAT_25", "cem karaca tamirci",         "Cem Karaca",       "Tamirci Çırağı",             S_SARKI_SANAT, 3),

            // BÖLÜM 4 — DİAKRİTİK YAZIM TOLERANSI ===========================

            new BulguCase("YG_DIA_1",  "simarik",                      "Tarkan",           "Şımarık",                    S_SARKI_DIA, 3),
            new BulguCase("YG_DIA_2",  "simarık",                      "Tarkan",           "Şımarık",                    S_SARKI_DIA, 3),
            new BulguCase("YG_DIA_3",  "ariyo",                        "Manifest",         "Arıyo",                      S_SARKI_DIA, 3),
            new BulguCase("YG_DIA_4",  "hayir",                        "Manifest",         "Hayır",                      S_SARKI_DIA, 3),
            new BulguCase("YG_DIA_7",  "firüze",                       "Sezen Aksu",       "Firuze",                     S_SARKI_DIA, 3),
            new BulguCase("YG_DIA_8",  "batsin bu dunya",              "Orhan Gencebay",   "Batsın Bu Dünya",            S_SARKI_DIA, 3),
            new BulguCase("YG_DIA_9",  "yalnizlar rihtimi",            "Erkin Koray",      "Yalnızlar Rıhtımı",          S_SARKI_DIA, 3),
            new BulguCase("YG_DIA_10", "beni benimle birak",           "Manga",            "Beni Benimle Bırak",         S_SARKI_DIA, 3),
            new BulguCase("YG_DIA_11", "bir pesimistin gozyaslari",    "Sagopa Kajmer",    "Bir Pesimistin Gözyaşları",  S_SARKI_DIA, 3),
            new BulguCase("YG_DIA_13", "uzun ince bir yoldayim",       "Selda Bağcan",     "Uzun İnce Bir Yoldayım",     S_SARKI_DIA, 3),
            new BulguCase("YG_DIA_14", "gulpembe",                     "Barış Manço",      "Gülpembe",                   S_SARKI_DIA, 3),
            new BulguCase("YG_DIA_15", "sana deger",                   "Yıldız Tilbe",     "Sana Değer",                 S_SARKI_DIA, 3),

            // BÖLÜM 5 — HARF HATASI ==========================================

            new BulguCase("YG_HARF_1",  "hayyır",                      "Manifest",         "Hayır",                      S_SARKI_HARF, 3),
            new BulguCase("YG_HARF_2",  "hayırr",                      "Manifest",         "Hayır",                      S_SARKI_HARF, 3),
            new BulguCase("YG_HARF_3",  "affed",                       "Müslüm Gürses",    "Affet",                      S_SARKI_HARF, 3),
            new BulguCase("YG_HARF_4",  "afet",                        "Müslüm Gürses",    "Affet",                      S_SARKI_HARF, 3),
            new BulguCase("YG_HARF_5",  "geri veer",                   "Wegh",             "Geri Ver",                   S_SARKI_HARF, 3),
            new BulguCase("YG_HARF_6",  "vursinlar",                   "Uzi",              "Vursunlar",                  S_SARKI_HARF, 3),
            new BulguCase("YG_HARF_7",  "mustezhel",                   "Ezhel",            "Müptezhel",                  S_SARKI_HARF, 3),
            new BulguCase("YG_HARF_8",  "gamssız hayat",               "Candan Erçetin",   "Gamsız Hayat",               S_SARKI_HARF, 3),
            new BulguCase("YG_HARF_9",  "şehri hüzün",                 "Manga",            "Şehr-i Hüzün",               S_SARKI_HARF, 3),
            new BulguCase("YG_HARF_10", "Crısh",                       "Crush",            "Crush!",                     S_SARKI_HARF, 3),

            // BÖLÜM 6 — BOŞLUK ===============================================

            new BulguCase("YG_BOSL_1",  "tozpembe",                    "Manifest",         "Toz Pembe",                  S_SARKI_BOSL, 3),
            new BulguCase("YG_BOSL_3",  "amanaman",                    "Duman",            "Aman Aman",                  S_SARKI_BOSL, 3),
            new BulguCase("YG_BOSL_4",  "blok 3",                      "Blok3",            "",                           S_SANAT_BOSL, 3),

            // BÖLÜM 7 — NOKTALAMA =============================================

            new BulguCase("YG_NOKT_1",  "-Crush.",                     "Crush",            "Crush",                      S_SARKI_NOKT, 3),
            new BulguCase("YG_NOKT_2",  "Crush.",                      "Crush",            "Crush",                      S_SARKI_NOKT, 3),
            new BulguCase("YG_NOKT_3",  ".Crush",                      "Crush",            "Crush",                      S_SARKI_NOKT, 3),
            new BulguCase("YG_NOKT_4",  "hayır.",                      "Manifest",         "Hayır",                      S_SARKI_NOKT, 3),
            new BulguCase("YG_NOKT_5",  "arıyo,",                      "Manifest",         "Arıyo",                      S_SARKI_NOKT, 3),
            new BulguCase("YG_NOKT_6",  "affet.",                      "Müslüm Gürses",    "Affet",                      S_SARKI_NOKT, 3),
            new BulguCase("YG_NOKT_7",  "şımarık!",                    "Tarkan",           "Şımarık",                    S_SARKI_NOKT, 3),
            new BulguCase("YG_NOKT_8",  "dudu?",                       "Tarkan",           "Dudu",                       S_SARKI_NOKT, 3),
            new BulguCase("YG_NOKT_9",  "gülpembe,",                   "Barış Manço",      "Gülpembe",                   S_SARKI_NOKT, 3),
            new BulguCase("YG_NOKT_10", "şehr-i hüzün",                "Manga",            "Şehr-i Hüzün",               S_SARKI_NOKT, 3),

            // BÖLÜM 8 — LYRİCS ===============================================

            new BulguCase("YG_LYRICS_1", "çölüme yağmur oldun",        "Müslüm Gürses",    "Affet",                      S_SARKI_LYRICS, 3),
            new BulguCase("YG_LYRICS_2", "sen ağlama ben ağlarım",     "Sezen Aksu",       "Sen Ağlama",                 S_SARKI_LYRICS, 3),
            new BulguCase("YG_LYRICS_4", "aşk vermiştim ne yaptın",    "Bergen",           "Seni Kalbimden Kovdum",      S_SARKI_LYRICS, 3),
            new BulguCase("YG_LYRICS_7", "geçmiş olsun der gibi",      "Duman",            "Geçmiş Olsun",               S_SARKI_LYRICS, 5),

            // BÖLÜM 9 — SANATÇI TAM EŞLEŞme =================================

            new BulguCase("YG_S_TAM_1",  "hadise",                     "Hadise",           "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_2",  "Hadise",                     "Hadise",           "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_3",  "simge",                      "Simge",            "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_4",  "Simge",                      "Simge",            "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_5",  "ahmet kaya",                 "Ahmet Kaya",       "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_6",  "Ahmet Kaya",                 "Ahmet Kaya",       "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_7",  "AHMET KAYA",                 "Ahmet Kaya",       "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_8",  "müslüm gürses",              "Müslüm Gürses",    "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_9",  "Müslüm Gürses",              "Müslüm Gürses",    "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_10", "müslüm",                     "Müslüm Gürses",    "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_11", "tarkan",                     "Tarkan",           "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_12", "Tarkan",                     "Tarkan",           "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_13", "sagopa kajmer",              "Sagopa Kajmer",    "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_14", "Sagopa Kajmer",              "Sagopa Kajmer",    "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_15", "ibrahim tatlıses",           "İbrahim Tatlıses", "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_16", "İbrahim Tatlıses",           "İbrahim Tatlıses", "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_17", "ferdi tayfur",               "Ferdi Tayfur",     "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_18", "Ferdi Tayfur",               "Ferdi Tayfur",     "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_19", "orhan gencebay",             "Orhan Gencebay",   "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_20", "Orhan Gencebay",             "Orhan Gencebay",   "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_21", "kayahan",                    "Kayahan",          "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_22", "Kayahan",                    "Kayahan",          "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_23", "ibrahim erkal",              "İbrahim Erkal",    "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_24", "İbrahim Erkal",              "İbrahim Erkal",    "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_25", "azer bülbül",                "Azer Bülbül",      "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_26", "Azer Bülbül",                "Azer Bülbül",      "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_27", "yıldız tilbe",               "Yıldız Tilbe",     "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_28", "Yıldız Tilbe",               "Yıldız Tilbe",     "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_29", "ezhel",                      "Ezhel",            "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_30", "Ezhel",                      "Ezhel",            "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_31", "wegh",                       "Wegh",             "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_32", "Wegh",                       "Wegh",             "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_33", "uzi",                        "Uzi",              "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_34", "UZI",                        "Uzi",              "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_35", "bergen",                     "Bergen",           "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_36", "Bergen",                     "Bergen",           "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_37", "cem karaca",                 "Cem Karaca",       "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_38", "Cem Karaca",                 "Cem Karaca",       "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_39", "manga",                      "Manga",            "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_40", "Manga",                      "Manga",            "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_41", "duman",                      "Duman",            "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_42", "Duman",                      "Duman",            "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_43", "gülşen",                     "Gülşen",           "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_44", "Gülşen",                     "Gülşen",           "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_45", "demet akalın",               "Demet Akalın",     "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_46", "Demet Akalın",               "Demet Akalın",     "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_47", "selda bağcan",               "Selda Bağcan",     "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_48", "Selda Bağcan",               "Selda Bağcan",     "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_49", "semicenk",                   "Semicenk",         "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_50", "Semicenk",                   "Semicenk",         "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_51", "eypio",                      "Eypio",            "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_52", "Eypio",                      "Eypio",            "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_53", "uygar doğanay",              "Uygar Doğanay",    "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_54", "buray",                      "Buray",            "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_55", "dua lipa",                   "Dua Lipa",         "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_56", "DUA LIPA",                   "Dua Lipa",         "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_57", "kıvırcık ali",               "Kıvırcık Ali",     "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_58", "cem adrian",                 "Cem Adrian",       "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_59", "volkan konak",               "Volkan Konak",     "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_60", "neşet ertaş",                "Neşet Ertaş",      "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_61", "kamuran akkor",              "Kamuran Akkor",    "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_62", "taladro",                    "Taladro",          "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_63", "sefo",                       "Sefo",             "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_64", "gazapizm",                   "Gazapizm",         "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_65", "mustafa sandal",             "Mustafa Sandal",   "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_66", "murat boz",                  "Murat Boz",        "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_67", "ebru gündeş",                "Ebru Gündeş",      "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_68", "Ebru Gündeş",                "Ebru Gündeş",      "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_69", "poizi",                      "Poizi",            "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_S_TAM_70", "Poizi",                      "Poizi",            "",                           S_SANAT_TAM, 3),

            // BÖLÜM 10 — SANATÇI KISMİ ========================================

            new BulguCase("YG_S_KISMI_1",  "ahmet",                    "Ahmet Kaya",       "",                           S_SANAT_KISMI, 3),
            new BulguCase("YG_S_KISMI_2",  "müslüm",                   "Müslüm Gürses",    "",                           S_SANAT_KISMI, 3),
            new BulguCase("YG_S_KISMI_3",  "gürses",                   "Müslüm Gürses",    "",                           S_SANAT_KISMI, 3),
            new BulguCase("YG_S_KISMI_4",  "sagopa",                   "Sagopa Kajmer",    "",                           S_SANAT_KISMI, 3),
            new BulguCase("YG_S_KISMI_5",  "tatlıses",                 "İbrahim Tatlıses", "",                           S_SANAT_KISMI, 3),
            new BulguCase("YG_S_KISMI_6",  "gencebay",                 "Orhan Gencebay",   "",                           S_SANAT_KISMI, 3),
            new BulguCase("YG_S_KISMI_7",  "tayfur",                   "Ferdi Tayfur",     "",                           S_SANAT_KISMI, 3),
            new BulguCase("YG_S_KISMI_8",  "erkal",                    "İbrahim Erkal",    "",                           S_SANAT_KISMI, 3),
            new BulguCase("YG_S_KISMI_9",  "bülbül",                   "Azer Bülbül",      "",                           S_SANAT_KISMI, 3),
            new BulguCase("YG_S_KISMI_10", "tilbe",                    "Yıldız Tilbe",     "",                           S_SANAT_KISMI, 3),
            new BulguCase("YG_S_KISMI_11", "kajmer",                   "Sagopa Kajmer",    "",                           S_SANAT_KISMI, 3),
            new BulguCase("YG_S_KISMI_12", "karaca",                   "Cem Karaca",       "",                           S_SANAT_KISMI, 3),
            new BulguCase("YG_S_KISMI_13", "bağcan",                   "Selda Bağcan",     "",                           S_SANAT_KISMI, 3),
            new BulguCase("YG_S_KISMI_14", "doğanay",                  "Uygar Doğanay",    "",                           S_SANAT_KISMI, 3),
            new BulguCase("YG_S_KISMI_15", "akalın",                   "Demet Akalın",     "",                           S_SANAT_KISMI, 3),
            new BulguCase("YG_S_KISMI_16", "sandal",                   "Mustafa Sandal",   "",                           S_SANAT_KISMI, 3),
            new BulguCase("YG_S_KISMI_17", "konak",                    "Volkan Konak",     "",                           S_SANAT_KISMI, 3),
            new BulguCase("YG_S_KISMI_18", "ertaş",                    "Neşet Ertaş",      "",                           S_SANAT_KISMI, 3),
            new BulguCase("YG_S_KISMI_19", "gündeş",                   "Ebru Gündeş",      "",                           S_SANAT_KISMI, 3),
            new BulguCase("YG_S_KISMI_20", "akkor",                    "Kamuran Akkor",    "",                           S_SANAT_KISMI, 3),

            // BÖLÜM 11 — SANATÇI DİAKRİTİK ===================================

            new BulguCase("YG_S_DIA_1",  "muslum gurses",              "Müslüm Gürses",    "",                           S_SANAT_DIA, 3),
            new BulguCase("YG_S_DIA_2",  "yildiz tilbe",               "Yıldız Tilbe",     "",                           S_SANAT_DIA, 3),
            new BulguCase("YG_S_DIA_3",  "ibrahim tatli",              "İbrahim Tatlıses", "",                           S_SANAT_DIA, 3),
            new BulguCase("YG_S_DIA_4",  "orhan gencabay",             "Orhan Gencebay",   "",                           S_SANAT_DIA, 3),
            new BulguCase("YG_S_DIA_5",  "dua lıpa",                   "Dua Lipa",         "",                           S_SANAT_DIA, 3),
            new BulguCase("YG_S_DIA_6",  "neşet ertas",                "Neşet Ertaş",      "",                           S_SANAT_DIA, 3),
            new BulguCase("YG_S_DIA_7",  "selda bagcan",               "Selda Bağcan",     "",                           S_SANAT_DIA, 3),
            new BulguCase("YG_S_DIA_8",  "gulsen",                     "Gülşen",           "",                           S_SANAT_DIA, 3),
            new BulguCase("YG_S_DIA_9",  "demet akalin",               "Demet Akalın",     "",                           S_SANAT_DIA, 3),
            new BulguCase("YG_S_DIA_10", "kivircik ali",               "Kıvırcık Ali",     "",                           S_SANAT_DIA, 3),
            new BulguCase("YG_S_DIA_11", "ebru gundes",                "Ebru Gündeş",      "",                           S_SANAT_DIA, 3),

            // BÖLÜM 12 — SANATÇI HARF HATASI ==================================

            new BulguCase("YG_S_HARF_1", "hadıse",                     "Hadise",           "",                           S_SANAT_HARF, 3),
            new BulguCase("YG_S_HARF_2", "sagopa kajmr",               "Sagopa Kajmer",    "",                           S_SANAT_HARF, 3),
            new BulguCase("YG_S_HARF_3", "ferdi tayfor",               "Ferdi Tayfur",     "",                           S_SANAT_HARF, 3),
            new BulguCase("YG_S_HARF_4", "taladr",                     "Taladro",          "",                           S_SANAT_HARF, 3),
            new BulguCase("YG_S_HARF_5", "simgee",                     "Simge",            "",                           S_SANAT_HARF, 3),
            new BulguCase("YG_S_HARF_6", "eypi",                       "Eypio",            "",                           S_SANAT_HARF, 3),
            new BulguCase("YG_S_HARF_7", "dunman",                     "Duman",            "",                           S_SANAT_HARF, 3),
            new BulguCase("YG_S_HARF_8", "mangg",                      "Manga",            "",                           S_SANAT_HARF, 3),

            // BÖLÜM 13 — SANATÇI BOŞLUK ======================================

            new BulguCase("YG_S_BOSL_1", "ahmetkaya",                  "Ahmet Kaya",       "",                           S_SANAT_BOSL, 3),
            new BulguCase("YG_S_BOSL_2", "müslümgürses",               "Müslüm Gürses",    "",                           S_SANAT_BOSL, 3),
            new BulguCase("YG_S_BOSL_3", "crush boys",                 "Crushboys",        "",                           S_SANAT_BOSL, 3),

            // BÖLÜM 14 — ALIAS ================================================

            new BulguCase("YG_ALIAS_1", "sago",                        "Sagopa Kajmer",    "",                           S_SANAT_ALIAS, 3),
            new BulguCase("YG_ALIAS_2", "sagopa",                      "Sagopa Kajmer",    "",                           S_SANAT_ALIAS, 3),
            new BulguCase("YG_ALIAS_3", "ibo tatlıses",                "İbrahim Tatlıses", "",                           S_SANAT_ALIAS, 3),
            new BulguCase("YG_ALIAS_4", "mor otesi",                   "Mor ve Ötesi",     "",                           S_SANAT_ALIAS, 3),

            // BÖLÜM 15 — PLAYLİST =============================================

            new BulguCase("YG_PL_1",  "arabesk",                       "",                 "[Playlist] arabesk",         S_PLAYLIST, 5),
            new BulguCase("YG_PL_2",  "Arabesk",                       "",                 "[Playlist] arabesk",         S_PLAYLIST, 5),
            new BulguCase("YG_PL_3",  "arabesk arşiv",                 "",                 "[Playlist] arabesk",         S_PLAYLIST, 5),
            new BulguCase("YG_PL_4",  "türkü",                         "",                 "[Playlist] türkü",           S_PLAYLIST, 5),
            new BulguCase("YG_PL_5",  "türküler",                      "",                 "[Playlist] türkü",           S_PLAYLIST, 5),
            new BulguCase("YG_PL_6",  "ilahi",                         "",                 "[Playlist] ilahi",           S_PLAYLIST, 5),
            new BulguCase("YG_PL_7",  "ilahiler",                      "",                 "[Playlist] ilahi",           S_PLAYLIST, 5),
            new BulguCase("YG_PL_8",  "kürtçe",                        "",                 "[Playlist] kürtçe",          S_PLAYLIST, 5),
            new BulguCase("YG_PL_9",  "kürtçe şarkılar",               "",                 "[Playlist] kürtçe",          S_PLAYLIST, 5),
            new BulguCase("YG_PL_10", "karadeniz",                     "",                 "[Playlist] karadeniz",       S_PLAYLIST, 5),
            new BulguCase("YG_PL_11", "halay",                         "",                 "[Playlist] halay",           S_PLAYLIST, 5),
            new BulguCase("YG_PL_12", "oyun havası",                   "",                 "[Playlist] oyun havası",     S_PLAYLIST, 5),
            new BulguCase("YG_PL_13", "oyun havaları",                 "",                 "[Playlist] oyun havası",     S_PLAYLIST, 5),
            new BulguCase("YG_PL_14", "OYUN HAVALARI",                 "",                 "[Playlist] oyun havası",     S_PLAYLIST, 5),
            new BulguCase("YG_PL_15", "ankara oyun",                   "",                 "[Playlist] ankara",          S_PLAYLIST, 5),
            new BulguCase("YG_PL_16", "roman havası",                  "",                 "[Playlist] roman",           S_PLAYLIST, 5),
            new BulguCase("YG_PL_17", "phonk",                         "",                 "[Playlist] phonk",           S_PLAYLIST, 5),
            new BulguCase("YG_PL_18", "rap",                           "",                 "[Playlist] rap",             S_PLAYLIST, 5),
            new BulguCase("YG_PL_19", "türkçe rap",                    "",                 "[Playlist] rap",             S_PLAYLIST, 5),
            new BulguCase("YG_PL_20", "türkçe pop",                    "",                 "[Playlist] pop",             S_PLAYLIST, 5),
            new BulguCase("YG_PL_21", "ninni",                         "",                 "[Playlist] ninni",           S_PLAYLIST, 5),
            new BulguCase("YG_PL_22", "90 lar",                        "",                 "[Playlist] 90",              S_PLAYLIST, 5),
            new BulguCase("YG_PL_23", "90'lar",                        "",                 "[Playlist] 90",              S_PLAYLIST, 5),
            new BulguCase("YG_PL_24", "remix",                         "",                 "[Playlist] remix",           S_PLAYLIST, 5),
            new BulguCase("YG_PL_25", "türk sanat müziği",             "",                 "[Playlist] türk sanat",      S_PLAYLIST, 5),
            new BulguCase("YG_PL_26", "aura",                          "",                 "[Playlist] aura",            S_PLAYLIST, 5),
            new BulguCase("YG_PL_27", "fenerbahçe",                    "",                 "[Playlist] fenerbahçe",      S_PLAYLIST, 5),
            new BulguCase("YG_PL_28", "galatasaray",                   "",                 "[Playlist] galatasaray",     S_PLAYLIST, 5),
            new BulguCase("YG_PL_29", "meditasyon",                    "",                 "[Playlist] meditasyon",      S_PLAYLIST, 5),
            new BulguCase("YG_PL_30", "yabancı pop",                   "",                 "[Playlist] yabancı",         S_PLAYLIST, 5),

            // BÖLÜM 16 — EK ŞARKI TAM EŞLEŞme (CSV'den daha fazla şarkı) =====

            // Kayahan ek şarkılar
            new BulguCase("YG_EK_2", "emrin olur", "Kayahan", "Emrin Olur", S_SARKI_TAM, 3),

            // Sezen Aksu ek
            new BulguCase("YG_EK_4",  "gülümse",                       "Sezen Aksu",       "Gülümse",                    S_SARKI_TAM, 3),
            new BulguCase("YG_EK_5", "şıkıdım", "Tarkan", "Şıkıdım", S_SARKI_TAM, 3),

            // Tarkan ek
            new BulguCase("YG_EK_6",  "adımı kalbine yaz",             "Tarkan",           "Adımı Kalbine Yaz",          S_SARKI_TAM, 3),
            new BulguCase("YG_EK_7",  "hepsi senin mi",                "Tarkan",           "Hepsi Senin Mi",             S_SARKI_TAM, 3),
            new BulguCase("YG_EK_8", "aşk", "Tarkan", "Aşk", S_SARKI_TAM, 3),

            // Müslüm ek
            new BulguCase("YG_EK_9", "öpsem mi", "Müslüm Gürses", "Öpsem Mi", S_SARKI_TAM, 3),
            new BulguCase("YG_EK_10", "zalimsin yar", "Müslüm Gürses", "Zalimsin Yar", S_SARKI_TAM, 3),

            // Barış Manço ek
            new BulguCase("YG_EK_11", "dönence",                       "Barış Manço",      "Dönence",                    S_SARKI_TAM, 3),
            new BulguCase("YG_EK_12", "kara sevda",                    "Barış Manço",      "Kara Sevda",                 S_SARKI_TAM, 3),

            // Bergen ek
            new BulguCase("YG_EK_13", "sev yeter",                     "Bergen",           "Sev Yeter",                  S_SARKI_TAM, 3),

            // Orhan Gencebay ek
            new BulguCase("YG_EK_15", "bir teselli ver",                "Orhan Gencebay",   "Bir Teselli Ver",            S_SARKI_TAM, 3),
            new BulguCase("YG_EK_16", "sen olmasan olmaz",              "Orhan Gencebay",   "Sen Olmasan Olmaz",          S_SARKI_TAM, 3),

            // Ferdi Tayfur ek
            new BulguCase("YG_EK_17", "benim gibi sevenler", "Ferdi Tayfur", "Benim Gibi Sevenler", S_SARKI_TAM, 3),

            // Sagopa ek
            new BulguCase("YG_EK_19", "vasiyet", "Sagopa Kajmer", "Vasiyet", S_SARKI_TAM, 3),

            // Ezhel ek
            new BulguCase("YG_EK_21", "şehrimin tadı",                 "Ezhel",            "Şehrimin Tadı",              S_SARKI_TAM, 3),
            new BulguCase("YG_EK_22", "felaket", "Ezhel", "Felaket", S_SARKI_TAM, 3),

            // Duman ek
            new BulguCase("YG_EK_23", "senden daha güzel",             "Duman",            "Senden Daha Güzel",          S_SARKI_TAM, 3),
            new BulguCase("YG_EK_24", "eyvallah",                      "Duman",            "Eyvallah",                   S_SARKI_TAM, 3),

            // Manga ek
            new BulguCase("YG_EK_25", "dursun zaman", "Manga", "Dursun Zaman", S_SARKI_TAM, 3),
            new BulguCase("YG_EK_26", "cevapsız sorular", "Manga", "Cevapsız Sorular", S_SARKI_TAM, 3),

            // Gülşen ek
            new BulguCase("YG_EK_27", "saz mı caz mı",                 "Gülşen",           "Saz Mı Caz Mı",              S_SARKI_TAM, 3),
            new BulguCase("YG_EK_28", "erkeksen",                      "Gülşen",           "Erkeksen",                   S_SARKI_TAM, 3),

            // Simge ek
            new BulguCase("YG_EK_29", "yankı",                         "Simge",            "Yankı",                      S_SARKI_TAM, 3),
            new BulguCase("YG_EK_30", "miş miş",                       "Simge",            "Miş Miş",                    S_SARKI_TAM, 3),

            // BÖLÜM 17 — EK YAZIM TOLERANSI VARYASYONLARI ====================

            // Sanatçı adı büyük harf / küçük harf
            new BulguCase("YG_WR_1",  "MÜSLÜM GÜRSES",                 "Müslüm Gürses",    "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_WR_2",  "sezen aksu",                    "Sezen Aksu",       "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_WR_3",  "SEZEN AKSU",                    "Sezen Aksu",       "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_WR_4",  "barış manço",                   "Barış Manço",      "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_WR_5",  "BARIŞ MANÇO",                   "Barış Manço",      "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_WR_6",  "orhan gencebay",                "Orhan Gencebay",   "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_WR_7",  "ORHAN GENCEBAY",                "Orhan Gencebay",   "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_WR_8",  "bergen",                        "Bergen",           "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_WR_9",  "BERGEN",                        "Bergen",           "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_WR_10", "cem karaca",                    "Cem Karaca",       "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_WR_11", "CEM KARACA",                    "Cem Karaca",       "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_WR_12", "ferdi tayfur",                  "Ferdi Tayfur",     "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_WR_13", "FERDI TAYFUR",                  "Ferdi Tayfur",     "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_WR_14", "semicenk",                      "Semicenk",         "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_WR_15", "SEMICENK",                      "Semicenk",         "",                           S_SANAT_TAM, 3),

            // Şarkı adı büyük harf
            new BulguCase("YG_WR_16", "AFFET",                         "Müslüm Gürses",    "Affet",                      S_SARKI_TAM, 3),
            new BulguCase("YG_WR_17", "FIRUZE",                        "Sezen Aksu",       "Firuze",                     S_SARKI_TAM, 3),
            new BulguCase("YG_WR_18", "BATSIN BU DÜNYA",               "Orhan Gencebay",   "Batsın Bu Dünya",            S_SARKI_TAM, 3),
            new BulguCase("YG_WR_19", "GÜLPEMBE",                      "Barış Manço",      "Gülpembe",                   S_SARKI_TAM, 3),
            new BulguCase("YG_WR_20", "elmanın yarısı", "Kayahan", "Elmanın Yarısı", S_SARKI_TAM, 3),

            // Diakritik varyasyonlar — şarkı adı
            new BulguCase("YG_WR_21", "aglama degmez",                 "Müslüm Gürses",    "Ağlama Değmez",              S_SARKI_DIA, 3),
            new BulguCase("YG_WR_23", "kaçak",                         "Sezen Aksu",       "Kaçak",                      S_SARKI_TAM, 3),
            new BulguCase("YG_WR_24", "kacak",                         "Sezen Aksu",       "Kaçak",                      S_SARKI_DIA, 3),
            new BulguCase("YG_WR_25", "sıfır tolerans",                "Hadise",           "Sıfır Tolerans",             S_SARKI_TAM, 3),
            new BulguCase("YG_WR_26", "benim olsana",                  "Crush",            "Benim Olsana",               S_SARKI_TAM, 3),
            new BulguCase("YG_WR_27", "onu da yak tanrım",             "Bergen",           "Onu Da Yak Tanrım",          S_SARKI_TAM, 3),
            new BulguCase("YG_WR_28", "onu da yak tanrim",             "Bergen",           "Onu Da Yak Tanrım",          S_SARKI_DIA, 3),
            new BulguCase("YG_WR_29", "resimdeki gozyaslari",          "Cem Karaca",       "Resimdeki Gözyaşları",       S_SARKI_DIA, 3),
            new BulguCase("YG_WR_30", "tamirci ciragi",                "Cem Karaca",       "Tamirci Çırağı",             S_SARKI_DIA, 3),
            new BulguCase("YG_WR_32", "oglan sibidik",                 "Azer Bülbül",      "Oğlan Şibidik",              S_SARKI_DIA, 3),
            new BulguCase("YG_WR_34", "bana ne bu sevdalardan",        "Ezhel",            "Bana Ne Bu Sevdalardan",     S_SARKI_TAM, 3),
            new BulguCase("YG_WR_35", "bana ne bu sevdalardan",        "Ezhel",            "Bana Ne Bu Sevdalardan",     S_SARKI_KISMI, 3),

            // BÖLÜM 18 — EK SANATÇI + ŞARKI BİRLEŞİK ========================

            new BulguCase("YG_KOM_2", "tarkan şıkıdım", "Tarkan", "Şıkıdım", S_SARKI_SANAT, 3),
            new BulguCase("YG_KOM_3",  "tarkan adımı kalbine yaz",     "Tarkan",           "Adımı Kalbine Yaz",          S_SARKI_SANAT, 3),
            new BulguCase("YG_KOM_4", "müslüm gürses zalimsin yar", "Müslüm Gürses", "Zalimsin Yar", S_SARKI_SANAT, 3),
            new BulguCase("YG_KOM_5",  "barış manço dönence",          "Barış Manço",      "Dönence",                    S_SARKI_SANAT, 3),
            new BulguCase("YG_KOM_6",  "orhan bir teselli ver",        "Orhan Gencebay",   "Bir Teselli Ver",            S_SARKI_SANAT, 3),
            new BulguCase("YG_KOM_7", "sagopa kajmer vasiyet", "Sagopa Kajmer", "Vasiyet", S_SARKI_SANAT, 3),
            new BulguCase("YG_KOM_8",  "ezhel şehrimin tadı",          "Ezhel",            "Şehrimin Tadı",              S_SARKI_SANAT, 3),
            new BulguCase("YG_KOM_9",  "duman senden daha güzel",      "Duman",            "Senden Daha Güzel",          S_SARKI_SANAT, 3),
            new BulguCase("YG_KOM_10", "manga dursun zaman", "Manga", "Dursun Zaman", S_SARKI_SANAT, 3),
            new BulguCase("YG_KOM_11", "gülşen erkeksen",              "Gülşen",           "Erkeksen",                   S_SARKI_SANAT, 3),
            new BulguCase("YG_KOM_12", "bergen sev yeter",             "Bergen",           "Sev Yeter",                  S_SARKI_SANAT, 3),
            new BulguCase("YG_KOM_13", "ferdi tayfur benim gibi sevenler", "Ferdi Tayfur", "Benim Gibi Sevenler", S_SARKI_SANAT, 3),
            new BulguCase("YG_KOM_14", "simge yankı",                  "Simge",            "Yankı",                      S_SARKI_SANAT, 3),
            new BulguCase("YG_KOM_15", "wegh kennedy",                 "Wegh",             "Kennedy'i Ben Vurdum",      S_SARKI_SANAT, 3),

            // BÖLÜM 19 — EK SANATÇI (CSV'den atlanmış isimler) ==============

            new BulguCase("YG_EKS_1",  "kıraç",                        "Kıraç",            "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_2",  "Kıraç",                        "Kıraç",            "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_3",  "kirac",                        "Kıraç",            "",                           S_SANAT_DIA, 3),
            new BulguCase("YG_EKS_4",  "pinhani",                      "Pinhani",          "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_5",  "Pinhani",                      "Pinhani",          "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_6",  "melike şahin",                 "Melike Şahin",     "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_7",  "Melike Şahin",                 "Melike Şahin",     "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_8",  "melike sahin",                 "Melike Şahin",     "",                           S_SANAT_DIA, 3),
            new BulguCase("YG_EKS_9",  "mor ve ötesi",                 "Mor ve Ötesi",     "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_10", "Mor ve Ötesi",                 "Mor ve Ötesi",     "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_11", "mor ve otesi",                 "Mor ve Ötesi",     "",                           S_SANAT_DIA, 3),
            new BulguCase("YG_EKS_12", "murat boz",                    "Murat Boz",        "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_13", "Murat Boz",                    "Murat Boz",        "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_14", "sibel can",                    "Sibel Can",        "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_15", "Sibel Can",                    "Sibel Can",        "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_16", "nilüfer",                      "Nilüfer",          "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_17", "Nilüfer",                      "Nilüfer",          "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_18", "nilufer",                      "Nilüfer",          "",                           S_SANAT_DIA, 3),
            new BulguCase("YG_EKS_19", "ben fero",                     "Ben Fero",         "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_20", "Ben Fero",                     "Ben Fero",         "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_21", "zara",                         "Zara",             "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_22", "Zara",                         "Zara",             "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_23", "eypio",                        "Eypio",            "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_24", "Eypio",                        "Eypio",            "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_25", "taladro",                      "Taladro",          "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_26", "Taladro",                      "Taladro",          "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_27", "mabel matiz",                  "Mabel Matiz",      "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_28", "Mabel Matiz",                  "Mabel Matiz",      "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_29", "ebru yaşar",                   "Ebru Yaşar",       "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_30", "Ebru Yaşar",                   "Ebru Yaşar",       "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_31", "serdar ortaç",                 "Serdar Ortaç",     "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_32", "Serdar Ortaç",                 "Serdar Ortaç",     "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_33", "hande yener",                  "Hande Yener",      "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_34", "Hande Yener",                  "Hande Yener",      "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_35", "demet akalın",                 "Demet Akalın",     "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_36", "Demet Akalın",                 "Demet Akalın",     "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_37", "cem adrian",                   "Cem Adrian",       "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_38", "Cem Adrian",                   "Cem Adrian",       "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_39", "yalın",                        "Yalın",            "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_40", "Yalın",                        "Yalın",            "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_41", "edis",                         "Edis",             "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_42", "Edis",                         "Edis",             "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_43", "buray",                        "Buray",            "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_44", "Buray",                        "Buray",            "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_45", "emre fel",                     "Emre Fel",         "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_46", "Emre Fel",                     "Emre Fel",         "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_47", "sıla",                         "Sıla",             "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_48", "Sıla",                         "Sıla",             "",                           S_SANAT_TAM, 3),
            new BulguCase("YG_EKS_49", "sefo",                         "Sefo",             "",                           S_SANAT_TAM, 3)

        );
    }

    // TEST ====================================================================

    @ParameterizedTest(name = "[{0}] \"{1}\"")
    @MethodSource("cases")
    @Order(1)
    void run(BulguCase bc) {
        String[] result = new String[]{"(beklenen tanımlanmamış)", "NOK", "API hatası"};

        try {
            Response res = api.search(bc.term(), "active-indices", TOP_N);
            JsonPath jp = res.jsonPath();
            result = evaluate(bc, jp);
        } catch (Exception e) {
            System.err.printf("API hatası [%s '%s']: %s%n", bc.caseId(), bc.term(), e.getMessage());
            result[2] = "API hatası: " + e.getMessage();
        }

        System.out.printf("[%s] %-20s | %-42s | %s%n",
                result[1], bc.caseId(), "\"" + bc.term() + "\"", result[2]);

        ROWS.add(new TestResultRow(
                bc.caseId(),
                "\"" + bc.term() + "\" araması yapılır",
                "Arama terimi: '" + bc.term() + "' — Bölüm: " + bc.section()
                        + "\nKaynak: " + kaynak(bc),
                result[0],
                bc.section(),
                "active-indices",
                result[1],
                result[2]
        ));
    }

    // KAYNAK (case neden yazıldı) ============================================
    // Rapordaki "Kaynak:" satırını üretir. Yeni sanatçı eklerken bu haritayı güncelle.

    private static final String K_LOG    = "En çok aratılanlar (arama logu, Eylül–Ekim 2026)";
    private static final String K_GUNDEM = "Gündem / şu an popüler (güncel pop listeleri)";
    private static final String K_KULT   = "Kült / klasik repertuar (kalıcı yüksek arama talebi)";

    private static final java.util.Map<String, String> ARTIST_KAYNAK = java.util.Map.ofEntries(
            java.util.Map.entry("Manifest",       K_LOG + " — 21k+ arama"),
            java.util.Map.entry("Blok3",          K_LOG + " — 6k+ arama"),
            java.util.Map.entry("Motive",         K_LOG),
            java.util.Map.entry("Crush",          K_GUNDEM + " — 2026 çıkışlı grup, Benim Olsana (11 Eyl 2026)"),
            java.util.Map.entry("Hadise",         K_GUNDEM),
            java.util.Map.entry("Simge",          K_GUNDEM),
            java.util.Map.entry("Semicenk",       K_GUNDEM),
            java.util.Map.entry("Gülşen",         K_GUNDEM),
            java.util.Map.entry("Duman",          K_GUNDEM),
            java.util.Map.entry("Wegh",           K_GUNDEM),
            java.util.Map.entry("Uzi",            K_GUNDEM),
            java.util.Map.entry("Ezhel",          K_GUNDEM),
            java.util.Map.entry("Manga",          K_GUNDEM),
            java.util.Map.entry("Demet Akalın",   K_GUNDEM),
            java.util.Map.entry("Candan Erçetin", K_KULT),
            java.util.Map.entry("Tarkan",         K_KULT),
            java.util.Map.entry("Ahmet Kaya",     K_KULT),
            java.util.Map.entry("Sezen Aksu",     K_KULT),
            java.util.Map.entry("Müslüm Gürses",  K_KULT),
            java.util.Map.entry("Sagopa Kajmer",  K_KULT),
            java.util.Map.entry("Ferdi Tayfur",   K_KULT),
            java.util.Map.entry("Orhan Gencebay", K_KULT),
            java.util.Map.entry("Kayahan",        K_KULT),
            java.util.Map.entry("Bergen",         K_KULT + " (arabesk)"),
            java.util.Map.entry("Cem Karaca",     K_KULT),
            java.util.Map.entry("Selda Bağcan",   K_KULT),
            java.util.Map.entry("Barış Manço",    K_KULT),
            java.util.Map.entry("Erkin Koray",    K_KULT),
            java.util.Map.entry("İbrahim Tatlıses", K_KULT));

    private static String kaynak(BulguCase bc) {
        String s = bc.section();
        String base;
        if (S_PLAYLIST.equals(s) || bc.expArtist() == null || bc.expArtist().isBlank()) {
            base = "Muud playlist / kategori adı araması";
        } else {
            base = ARTIST_KAYNAK.getOrDefault(bc.expArtist(),
                    K_LOG + " — sanatçı listesinden (CSV'de atlanan isimler)");
        }
        boolean varyasyon = s.contains("Yazım") || s.contains("Alias");
        return varyasyon ? base + " → yazım toleransı varyasyonu" : base;
    }

    // KURAL DEĞERLENDİRME ====================================================

    private String[] evaluate(BulguCase bc, JsonPath jp) {
        List<Object> list = MuudSearchUtils.resultsList(jp);
        String base = MuudSearchUtils.getBasePath(jp);

        if (list.isEmpty()) {
            return new String[]{
                    "Arama sonucunda en az 1 kayıt dönmesi beklenir.", "NOK", "API boş sonuç döndürdü."};
        }

        String expTrack  = bc.expTrack();
        String expArtist = bc.expArtist();

        if (expTrack.isEmpty() && expArtist.isEmpty()) {
            return new String[]{"(gözlem)", "OK",
                    "Gözlem case — " + list.size() + " sonuç döndü.\n" + top5Desc(jp, base)};
        }

        if (expTrack.toLowerCase(TR).startsWith("[playlist]")) {
            return evalPlaylist(bc, jp, base);
        }

        if (!expArtist.isEmpty() && expTrack.isEmpty()) {
            return evalFirstArtist(bc, jp, base);
        }

        return evalArtistAndTrack(bc, jp, base);
    }

    private String[] evalFirstArtist(BulguCase bc, JsonPath jp, String base) {
        int n = bc.topN();
        String expected = n == 1
                ? "1. sırada '" + bc.expArtist() + "' sanatçısı gelmeli."
                : "İlk " + n + " içinde '" + bc.expArtist() + "' sanatçısı gelmeli.";

        int pos = MuudSearchUtils.findArtistIndex(jp, n, bc.expArtist());
        if (pos != -1) {
            String fa = MuudSearchUtils.getPerformerName(jp, base + "[" + pos + "].data");
            return new String[]{expected, "OK",
                    "Başarılı — " + (pos + 1) + ". sırada '" + fa + "' geldi.\n" + top5Desc(jp, base)};
        }

        int fullPos = MuudSearchUtils.findArtistIndex(jp, TOP_N, bc.expArtist());
        String where = fullPos == -1 ? "top-" + TOP_N + "'da da bulunamadı" : (fullPos + 1) + ". sırada bulundu";
        return new String[]{expected, "NOK",
                "İlk " + n + "'da yok — " + bc.expArtist() + ": " + where + ".\n" + top5Desc(jp, base)};
    }

    private String[] evalArtistAndTrack(BulguCase bc, JsonPath jp, String base) {
        int n = bc.topN();
        String expStr = bc.expArtist().isEmpty()
                ? "'" + bc.expTrack() + "'"
                : "'" + bc.expArtist() + "' – '" + bc.expTrack() + "'";
        String expected = "Top-" + n + " içinde " + expStr + " eşleşmesi bulunmalı.";

        int idx = MuudSearchUtils.findArtistAndTrackIndex(jp, n, bc.expArtist(), bc.expTrack());
        if (idx != -1) {
            String fa = MuudSearchUtils.getPerformerName(jp, base + "[" + idx + "].data");
            String ft = MuudSearchUtils.safeStr(jp.getString(base + "[" + idx + "].data.songName"));
            if (ft.isEmpty()) ft = MuudSearchUtils.safeStr(jp.getString(base + "[" + idx + "].data.albumName"));
            String label = fa.isEmpty() ? ft : fa + "' – '" + ft;
            return new String[]{expected, "OK",
                    "Başarılı — " + (idx + 1) + ". sırada: '" + label + "'.\n" + top5Desc(jp, base)};
        }

        int fullIdx = MuudSearchUtils.findArtistAndTrackIndex(jp, TOP_N, bc.expArtist(), bc.expTrack());
        String where = fullIdx == -1 ? "top-" + TOP_N + "'da da bulunamadı" : (fullIdx + 1) + ". sırada bulundu";
        return new String[]{expected, "NOK",
                "Top-" + n + "'da yok — " + expStr + ": " + where + ".\n" + top5Desc(jp, base)};
    }

    private String[] evalPlaylist(BulguCase bc, JsonPath jp, String base) {
        int n = bc.topN();
        String keyword = bc.expTrack().substring("[Playlist]".length()).trim();
        String expected = "Top-" + n + " içinde '" + keyword + "' adını içeren playlist bulunmalı.";

        for (int i = 0; i < n; i++) {
            String pl = MuudSearchUtils.safeStr(MuudSearchUtils.getPlaylistName(jp, base + "[" + i + "].data"));
            if (!pl.isEmpty() && MuudSearchUtils.containsTRInsensitive(pl, keyword)) {
                return new String[]{expected, "OK",
                        "Başarılı — " + (i + 1) + ". sırada playlist: '" + pl + "'.\n" + top5Desc(jp, base)};
            }
        }

        int fullPos = -1;
        for (int i = 0; i < TOP_N; i++) {
            String pl = MuudSearchUtils.safeStr(MuudSearchUtils.getPlaylistName(jp, base + "[" + i + "].data"));
            if (!pl.isEmpty() && MuudSearchUtils.containsTRInsensitive(pl, keyword)) {
                fullPos = i; break;
            }
        }
        String where = fullPos == -1 ? "top-" + TOP_N + "'da da bulunamadı" : (fullPos + 1) + ". sırada bulundu";
        return new String[]{expected, "NOK",
                "Top-" + n + "'da yok — '" + keyword + "': " + where + ".\n" + top5Desc(jp, base)};
    }

    private String itemDesc(JsonPath jp, String base, int i) {
        String song      = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.songName"));
        String album     = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.albumName"));
        String playlist  = MuudSearchUtils.safeStr(MuudSearchUtils.getPlaylistName(jp, base + "[" + i + "].data"));
        String performer = MuudSearchUtils.getPerformerName(jp, base + "[" + i + "].data");
        String kind      = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.kind"));

        String label;
        if (!song.isEmpty())           label = song + (performer.isEmpty() ? "" : " — " + performer);
        else if (!album.isEmpty())     label = album + (performer.isEmpty() ? "" : " — " + performer);
        else if (!playlist.isEmpty())  label = playlist;
        else if (!performer.isEmpty()) label = performer;
        else                           label = "(boş)";

        String kindPrefix = kind.isEmpty() ? "" : "[" + kind + "]";
        Object popObj = jp.get(base + "[" + i + "].data.prodPopularity");
        String popField = "prodPopularity";
        if (popObj == null) { popField = "popularity"; popObj = jp.get(base + "[" + i + "].data.popularity"); }
        Object scoreObj = jp.get(base + "[" + i + "].score");
        Object idObj    = jp.get(base + "[" + i + "].id");
        String pop   = popObj   != null ? popObj.toString()   : "-";
        String score = scoreObj != null ? scoreObj.toString() : "-";
        String id    = idObj    != null ? idObj.toString()    : "-";
        return kindPrefix + label + "\n | " + popField + "=" + pop + " | score=" + score + " | ID=" + id;
    }

    private String top5Desc(JsonPath jp, String base) {
        StringBuilder sb = new StringBuilder("İlk 5 sonuç:");
        for (int i = 0; i < 5; i++) sb.append("\n").append(i + 1).append(". ").append(itemDesc(jp, base, i));
        return sb.toString();
    }
}
