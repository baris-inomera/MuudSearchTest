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
 *  BULGU SNAPSHOT V2 DETAIL — Arama Tipi Bazlı Regresyon Takipçisi
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  BulguSnapshotV2 ile birebir aynı case'leri içerir; tek fark bölüm
 *  etiketlerinin daha granüler bir arama-tipi taksonomisine göre
 *  düzenlenmesidir. Bu sayede Excel raporunda şu tür analizler yapılabilir:
 *
 *    "Şarkı · Lyrics başarı oranı %20 → lirik indeksleme önceliklendirilmeli"
 *    "Sanatçı · Yazım Toleransı %45  → fuzzy-match iyileştirmesi gerekli"
 *    "Şarkı · Tam Eşleşme %85        → temel arama sağlıklı"
 *
 *  Bölüm Taksonomisi (10 kategori):
 *  ─────────────────────────────────────────────────────────────────────────────
 *  Şarkı · Tam Eşleşme       kullanıcı şarkı adını birebir yazdı
 *  Şarkı · Kısmi Ad          şarkı adının başı veya bir parçası
 *  Şarkı · Sanatçı + Şarkı   sanatçı adı + şarkı adı birlikte
 *  Şarkı · Yazım Toleransı   yazım yanlışı / eksik karakter / Türkçe char / bitişik yazım
 *  Şarkı · Lyrics            şarkı sözüyle arama
 *  Sanatçı · Tam Eşleşme     sanatçı adını birebir yazdı
 *  Sanatçı · Kısmi Ad        ad / soyad / ilk kelime
 *  Sanatçı · Yazım Toleransı yazım yanlışı / fonetik / Türkçe char / bitişik yazım
 *  Sanatçı · Kısaltma/Alias  kısaltma, rumuz, sayısal alias
 *  Playlist                  kategori / tür adıyla liste arama
 * ─────────────────────────────────────────────────────────────────────────────
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BulguSnapshotV2Detail extends TestConfig {

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

    private static final List<BulguSnapshotWriter.SnapshotRow> ROWS = new ArrayList<>();
    private static MuudSearchApi api;

    // =========================================================================
    // SETUP / TEARDOWN
    // =========================================================================

    @BeforeAll
    static void init() {
        api = new MuudSearchApi();
        System.out.println("✅ BulguSnapshotV2Detail başlatıldı — " + TOP_N + " sonuç toplanacak.");
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

            // ═════════════════════════════════════════════════════════════════
            // ŞARKİ · TAM EŞLEŞMe
            // Kullanıcı şarkı adını birebir yazmış (case-insensitive)
            // ═════════════════════════════════════════════════════════════════
            new BulguCase("BULGU_002", "a canım",                        "Mabel Matiz",          "A Canım",                        S_SARKI_TAM),
            new BulguCase("BULGU_004", "olmazlara vuruluyorum",          "Mert Demir",           "Olmazlara Vuruluyorum",          S_SARKI_TAM),
            new BulguCase("BULGU_005", "çıkmaz bir sokakta",             "",                     "Çıkmaz Bir Sokakta",             S_SARKI_TAM),
            new BulguCase("BULGU_007", "kusura bakma",                   "Blok3",                "Kusura Bakma",                   S_SARKI_TAM),
            new BulguCase("BULGU_010", "maraton",                        "Ati242",               "Maraton",                        S_SARKI_TAM),
            new BulguCase("BULGU_011", "geri ver",                       "Wegh",                 "Geri Ver",                       S_SARKI_TAM),
            new BulguCase("BULGU_012", "saygımdan",                      "Bengü",                "Saygımdan",                      S_SARKI_TAM),
            new BulguCase("BULGU_013", "meğerse",                        "Linet",                "Meğerse",                        S_SARKI_TAM),
            new BulguCase("BULGU_015", "dacia",                          "Lvbel C5",             "DACIA",                          S_SARKI_TAM),
            new BulguCase("BULGU_020", "yalnızlığın çaresini bulmuşlar", "",                     "Yalnızlığın Çaresini Bulmuşlar", S_SARKI_TAM),
            new BulguCase("BULGU_023", "yapar mısın",                    "Poizi",                "YAPAR MISIN?",                   S_SARKI_TAM),
            new BulguCase("BULGU_025", "yerinde dur",                    "Sefo",                 "Yerinde Dur",                    S_SARKI_TAM),
            new BulguCase("BULGU_028", "ey aşk",                         "Sezen Aksu",           "Ey Aşk",                         S_SARKI_TAM),
            new BulguCase("BULGU_030", "şımarık",                        "Tarkan",               "Şımarık",                        S_SARKI_TAM),
            new BulguCase("BULGU_031", "giderim kırağınan",              "Onur Şan",             "Giderim Kırağınan",              S_SARKI_TAM),
            new BulguCase("BULGU_033", "ara beni lütfen",                "Kenan Doğulu",         "Ara Beni Lütfen",                S_SARKI_TAM),
            new BulguCase("BULGU_035", "aşk yok olmaktır",               "",                     "Aşk Yok Olmaktır",               S_SARKI_TAM),
            new BulguCase("BULGU_036", "can efendim",                    "",                     "Can Efendim",                    S_SARKI_TAM),
            new BulguCase("BULGU_038", "çıt çıt çedene",                 "Barış Manço",          "Çıt Çıt Çedene",                 S_SARKI_TAM),
            new BulguCase("BULGU_039", "çıkar biri karşıma",             "Poizi",                "Çıkar Biri Karşıma",             S_SARKI_TAM),
            new BulguCase("BULGU_042", "sen yanlış yaptın",              "Şahin Kendirci",       "Sen Yanlış Yaptın",              S_SARKI_TAM),
            new BulguCase("BULGU_043", "vay dayı",                       "Aynur Polat",          "Vay Dayı",                       S_SARKI_TAM),
            new BulguCase("BULGU_044", "silinmez",                       "Mansur Ark",           "Silinmez",                       S_SARKI_TAM),
            new BulguCase("BULGU_045", "halbuki",                        "Yalın",                "Halbuki",                        S_SARKI_TAM),
            new BulguCase("BULGU_047", "duydun mu",                      "Yusuf Güney",          "Duydun Mu?",                     S_SARKI_TAM),
            new BulguCase("BULGU_048", "sana güvenmiyorum",              "Dedublüman",           "Sana Güvenmiyorum",              S_SARKI_TAM),
            new BulguCase("BULGU_049", "yasemen",                        "Afra",                 "Yasemen",                        S_SARKI_TAM),
            new BulguCase("BULGU_050", "düşer o",                        "İzel",                 "Düşer O",                        S_SARKI_TAM),
            new BulguCase("BULGU_051", "kömür",                          "Mabel Matiz",          "Kömür",                          S_SARKI_TAM),
            new BulguCase("BULGU_054", "just the way you are",           "",                     "Just The Way You Are",           S_SARKI_TAM),
            new BulguCase("BULGU_056", "snap",                           "Manifest",             "Snap",                           S_SARKI_TAM),
            new BulguCase("BULGU_059", "ama başaramadım",                "Burak Bulut",          "Ama Başaramadım",                S_SARKI_TAM),
            new BulguCase("BULGU_061", "adına bir çizik çektim",         "",                     "Adına Bir Çizik Çektim",         S_SARKI_TAM),
            new BulguCase("BULGU_062", "yaramızda kalsın",               "Merve Özbey",          "Yaramızda Kalsın",               S_SARKI_TAM),
            new BulguCase("BULGU_063", "sev yeter",                      "",                     "Sev Yeter",                      S_SARKI_TAM),
            new BulguCase("BULGU_065", "kaybolurum gülüşünde",           "İkilem",               "Kaybolurum Gülüşünde",           S_SARKI_TAM),
            new BulguCase("BULGU_071", "ağlama ben ağlarım",             "Canozan",              "Ağlama Ben Ağlarım",             S_SARKI_TAM),
            new BulguCase("BULGU_080", "şikayetim var",                  "",                     "Şikayetim Var",                  S_SARKI_TAM),
            new BulguCase("BULGU_081", "bunca yıl",                      "Dedublüman",           "Bunca Yıl",                      S_SARKI_TAM),
            new BulguCase("BULGU_082", "düldül",                         "Mabel Matiz",          "Düldül",                         S_SARKI_TAM),
            new BulguCase("BULGU_086", "perde",                          "Poizi",                "Perde",                          S_SARKI_TAM),
            new BulguCase("BULGU_089", "phonk",                          "DEHA INC.",            "Phonk",                          S_SARKI_TAM),
            new BulguCase("BULGU_092", "sonbahar",                       "Era7Capone",           "SONBAHAR",                       S_SARKI_TAM),
            new BulguCase("BULGU_093", "acem kızı",                      "",                     "Acem Kızı",                      S_SARKI_TAM),
            new BulguCase("BULGU_094", "hacel obası",                    "",                     "Hacel Obası",                    S_SARKI_TAM),
            new BulguCase("BULGU_095", "yalan",                          "",                     "Yalan",                          S_SARKI_TAM),
            new BulguCase("BULGU_096", "bana sor",                       "Ferdi Tayfur",         "Bana Sor",                       S_SARKI_TAM),
            new BulguCase("BULGU_098", "rüya",                           "Manifest",             "Rüya",                           S_SARKI_TAM),
            new BulguCase("BULGU_100", "ara",                            "Zeynep Bastık",        "Ara",                            S_SARKI_TAM),
            new BulguCase("BULGU_101", "14 bahar",                       "Mert Demir",           "14 Bahar",                       S_SARKI_TAM),
            new BulguCase("BULGU_103", "ela mana",                       "",                     "Ela Mana",                       S_SARKI_TAM),
            new BulguCase("BULGU_104", "yekten",                         "Demet Akalın",         "Yekten",                         S_SARKI_TAM),
            new BulguCase("BULGU_105", "erik dalı",                      "",                     "Erik Dalı",                      S_SARKI_TAM),
            new BulguCase("BULGU_106", "elfida",                         "",                     "Elfida",                         S_SARKI_TAM),
            new BulguCase("BULGU_107", "yazan kalem siyah",              "",                     "Yazan Kalem Siyah",              S_SARKI_TAM),
            new BulguCase("BULGU_108", "Mihriban",                       "",                     "Mihriban",                       S_SARKI_TAM),
            new BulguCase("BULGU_111", "merdo",                          "",                     "Merdo",                          S_SARKI_TAM),
            new BulguCase("BULGU_113", "sigara",                         "",                     "Sigara",                         S_SARKI_TAM),
            new BulguCase("BULGU_119_C","farzet",                        "İlyas Yalçıntaş",      "Farzet",                         S_SARKI_TAM),
            new BulguCase("BULGU_121", "misket",                         "",                     "Misket",                         S_SARKI_TAM),
            new BulguCase("BULGU_122", "kara sevda",                     "",                     "Kara Sevda",                     S_SARKI_TAM),
            new BulguCase("BULGU_123", "parla",                          "",                     "Parla",                          S_SARKI_TAM),
            new BulguCase("BULGU_124", "kırmızı balık",                  "",                     "Kırmızı Balık",                  S_SARKI_TAM),
            // UAT
            new BulguCase("UAT_011",  "nasır",                           "Melike Şahin",         "Nasır",                          S_SARKI_TAM),
            new BulguCase("UAT_019",  "bodrum",                          "Yüzyüzeyken Konuşuruz","Bodrum",                          S_SARKI_TAM),
            new BulguCase("UAT_126",  "gözlerime bak",                   "Mert Demir",           "Gözlerime Bak",                  S_SARKI_TAM),

            // ═════════════════════════════════════════════════════════════════
            // ŞARKİ · KISMİ AD
            // Kullanıcı şarkı adının yalnızca başını veya bir bölümünü yazmış
            // ═════════════════════════════════════════════════════════════════
            new BulguCase("BULGU_001", "Hermes",                         "Batuflex",             "Hermès 2.0",                     S_SARKI_KISMI),
            new BulguCase("BULGU_019", "yalnızlığın çaresini",           "gripin",               "Yalnızlığın Çaresini Bulmuşlar", S_SARKI_KISMI),
            new BulguCase("BULGU_024", "yerinde",                        "Sefo",                 "Yerinde Dur",                    S_SARKI_KISMI),
            new BulguCase("BULGU_034", "vidrado em",                     "Dj Guuga",             "Vidrado Em Você",                S_SARKI_KISMI),
            new BulguCase("BULGU_037", "çıt çıt",                        "Barış Manço",          "Çıt Çıt Çedene",                 S_SARKI_KISMI),
            new BulguCase("BULGU_072", "ağlama ben",                     "Canozan",              "Ağlama Ben Ağlarım",             S_SARKI_KISMI),
            new BulguCase("BULGU_074", "erik",                           "",                     "Erik Dalı",                      S_SARKI_KISMI),
            new BulguCase("BULGU_078", "doğuştan",                       "Lvbel C5",             "Doğuştan Beri Haklıyım",         S_SARKI_KISMI),
            new BulguCase("BULGU_114", "dandini",                        "Ninni Bebek",          "Dandini Dandini Dastana",        S_SARKI_KISMI),
            // UAT
            new BulguCase("UAT_017",  "sus",                             "Ceza",                 "Suspus",                         S_SARKI_KISMI),
            new BulguCase("UAT_018",  "pus",                             "Ceza",                 "Suspus",                         S_SARKI_KISMI),
            new BulguCase("UAT_141",  "kalbimin sahibine",               "İrem Derici",          "Kalbimin Tek Sahibine",          S_SARKI_KISMI),

            // ═════════════════════════════════════════════════════════════════
            // ŞARKİ · SANATÇI + ŞARKİ
            // Kullanıcı sanatçı adı + şarkı adını birlikte yazmış
            // ═════════════════════════════════════════════════════════════════
            new BulguCase("BULGU_041", "messy lola young",               "Lola Young",           "Messy",                          S_SARKI_SANAT),
            new BulguCase("BULGU_052", "mabel kömür",                    "Mabel Matiz",          "Kömür",                          S_SARKI_SANAT),
            new BulguCase("BULGU_058", "y poizi",                        "Poizi",                "Y",                              S_SARKI_SANAT),
            new BulguCase("BULGU_060", "kts manifest",                   "Manifest",             "KTS",                            S_SARKI_SANAT),
            new BulguCase("BULGU_070", "Dua Lipa Shine",                 "Dua Lipa",             "Shine",                          S_SARKI_SANAT),
            new BulguCase("BULGU_097", "rüya manifest",                  "Manifest",             "Rüya",                           S_SARKI_SANAT),

            // ═════════════════════════════════════════════════════════════════
            // ŞARKİ · YAZIM TOLERANSI
            // Yazım yanlışı / eksik/yanlış karakter / Türkçe karakter eksikliği /
            // bitişik yazım / kelime sırası / çekim eki farklılığı
            // ═════════════════════════════════════════════════════════════════
            new BulguCase("BULGU_003", "acanım",                         "Mabel Matiz",          "A Canım",                        S_SARKI_YAZIM), // boşluk eksik
            new BulguCase("BULGU_014", "çok pardon",                     "Lvbel C5",             "COOOK PARDON",                   S_SARKI_YAZIM), // çok → coook
            new BulguCase("BULGU_029", "simarik",                        "Tarkan",               "Şımarık",                        S_SARKI_YAZIM), // Türkçe char eksik
            new BulguCase("BULGU_032", "dame un grr",                    "Fantomel",             "Dame Un Grrr",                   S_SARKI_YAZIM), // eksik harf
            new BulguCase("BULGU_040", "hav hav",                        "Lvbel C5",             "Havhavhav",                      S_SARKI_YAZIM), // boşluklu vs bitişik
            new BulguCase("BULGU_077", "karakedi",                       "Melis Fis",            "Kara Kedi",                      S_SARKI_YAZIM), // boşluk eksik
            // UAT
            new BulguCase("UAT_054",  "illede sen",                      "Azer Bülbül",          "İlle De Sen",                    S_SARKI_YAZIM), // bitişik yazım + Türkçe char
            new BulguCase("UAT_068",  "arabam",                          "Sefo",                 "Araba",                          S_SARKI_YAZIM), // iyelik eki farklılığı
            new BulguCase("UAT_145",  "lacivert eceler",                 "Ferhat Göçer",         "Lacivert Geceler",               S_SARKI_YAZIM), // hatalı harf (e→ge)
            new BulguCase("UAT_148",  "güldün ne güzel",                 "Pinhani",              "Ne Güzel Güldün",                S_SARKI_YAZIM), // kelime sırası farklı

            // ═════════════════════════════════════════════════════════════════
            // ŞARKİ · LYRİCS
            // Kullanıcı şarkı sözü parçasıyla arama yapmış;
            // arama terimi şarkı/sanatçı adıyla örtüşmüyor
            // ═════════════════════════════════════════════════════════════════
            new BulguCase("BULGU_066", "bak ben yara gibiyim",           "Emir Can İğrek",       "Nalan",                          S_SARKI_LYRICS),
            new BulguCase("BULGU_067", "çölüme yağmur oldun",            "Müslüm Gürses",        "Affet",                          S_SARKI_LYRICS),
            new BulguCase("BULGU_068", "sana hastayım anlasana",         "Derya Uluğ",           "Yansıma",                        S_SARKI_LYRICS),
            new BulguCase("BULGU_075", "hadi ya",                        "Melis Kar",            "Yatıya",                         S_SARKI_LYRICS),
            new BulguCase("BULGU_076", "babalar",                        "Blok3",                "PATLAT",                         S_SARKI_LYRICS),
            new BulguCase("BULGU_079", "silemez o beni",                 "Yıldız Tilbe",         "Dizine Dursun",                  S_SARKI_LYRICS),
            new BulguCase("BULGU_083", "çetin ceviz şerbetli mayam",     "Melike Şahin",         "Canın Beni Çekti",               S_SARKI_LYRICS),
            new BulguCase("BULGU_084", "bir motive",                     "Motive",               "",                               S_SARKI_LYRICS),
            // UAT
            new BulguCase("UAT_015",  "yandım ah",                       "Sakiler",              "Yalanı Bırak",                   S_SARKI_LYRICS),
            new BulguCase("UAT_022",  "bak ben yara gibiyim",            "Emir Can İğrek",       "Nalan",                          S_SARKI_LYRICS),
            new BulguCase("UAT_023",  "çölüme yağmur oldun",             "Müslüm Gürses",        "Affet",                          S_SARKI_LYRICS),
            new BulguCase("UAT_024",  "zaten aşklar hep yalan dolan",    "Yıldız Tilbe",         "Sana Değer",                     S_SARKI_LYRICS),
            new BulguCase("UAT_034",  "sana hastayım anlasana",          "Derya Uluğ",           "Yansıma",                        S_SARKI_LYRICS),
            new BulguCase("UAT_117",  "hadi ya",                         "Melis Kar",            "Yatıya",                         S_SARKI_LYRICS),
            new BulguCase("UAT_119",  "babalar",                         "Blok3",                "PATLAT",                         S_SARKI_LYRICS),
            new BulguCase("UAT_128",  "sarışınlar",                      "Derya Uluğ",           "Esmerin Adı Oya",                S_SARKI_LYRICS),
            new BulguCase("UAT_136",  "silemez o beni",                  "Yıldız Tilbe",         "Dizine Dursun",                  S_SARKI_LYRICS),
            new BulguCase("UAT_137",  "babalar sözünü tutar",            "Blok3",                "PATLAT",                         S_SARKI_LYRICS),
            new BulguCase("UAT_143",  "çok geç şmdi",                    "Edis",                 "Yalan",                          S_SARKI_LYRICS),
            new BulguCase("UAT_144",  "affet bu gece istedim ölmek",     "Model",                "Pembe Mezarlık",                 S_SARKI_LYRICS),

            // ═════════════════════════════════════════════════════════════════
            // SANATÇI · TAM EŞLEŞMe
            // Kullanıcı sanatçı adını birebir yazmış
            // ═════════════════════════════════════════════════════════════════
            new BulguCase("BULGU_008", "mfö",                            "MFÖ",                  "",                               S_SANAT_TAM),
            new BulguCase("BULGU_016", "Manifest",                       "Manifest",             "",                               S_SANAT_TAM),
            new BulguCase("BULGU_017", "semicenk",                       "Semicenk",             "",                               S_SANAT_TAM),
            new BulguCase("BULGU_055", "utku akkaya",                    "Utku Akkaya",          "",                               S_SANAT_TAM),
            new BulguCase("BULGU_088", "derya bedavacı",                  "Derya Bedavacı",       "",                               S_SANAT_TAM),
            new BulguCase("BULGU_090", "ceza",                           "Ceza",                 "",                               S_SANAT_TAM),
            new BulguCase("BULGU_091", "Ceza",                           "Ceza",                 "",                               S_SANAT_TAM),
            new BulguCase("BULGU_102", "yaşar",                          "Yaşar",                "",                               S_SANAT_TAM),
            new BulguCase("BULGU_112", "Gökhan Özen",                    "Gökhan Özen",          "",                               S_SANAT_TAM),
            new BulguCase("BULGU_118", "çelik",                          "Çelik",                "",                               S_SANAT_TAM),
            new BulguCase("BULGU_119_B","Haluk Levent",                  "Haluk Levent",         "",                               S_SANAT_TAM),
            new BulguCase("BULGU_120", "Mustafa Yıldızdoğan",            "Mustafa Yıldızdoğan",  "",                               S_SANAT_TAM),
            // UAT
            new BulguCase("UAT_001",  "u2",                              "u2",                   "",                               S_SANAT_TAM),
            new BulguCase("UAT_010",  "edis",                            "Edis",                 "",                               S_SANAT_TAM),
            new BulguCase("UAT_013",  "uzi",                             "Uzi",                  "",                               S_SANAT_TAM),
            new BulguCase("UAT_030",  "sıla",                            "Sıla",                 "",                               S_SANAT_TAM),
            new BulguCase("UAT_045",  "eminem",                          "Eminem",               "",                               S_SANAT_TAM),
            new BulguCase("UAT_051",  "güneş",                           "Güneş",                "",                               S_SANAT_TAM),
            new BulguCase("UAT_052",  "dua lipa",                        "Dua Lipa",             "",                               S_SANAT_TAM),
            new BulguCase("UAT_059",  "mero",                            "Mero",                 "",                               S_SANAT_TAM),
            new BulguCase("UAT_061",  "murda",                           "Murda",                "",                               S_SANAT_TAM),
            new BulguCase("UAT_064",  "inna",                            "Inna",                 "",                               S_SANAT_TAM),
            new BulguCase("UAT_067",  "adele",                           "Adele",                "",                               S_SANAT_TAM),
            new BulguCase("UAT_070",  "mor ve ötesi",                    "mor ve ötesi",          "",                               S_SANAT_TAM),
            new BulguCase("UAT_075",  "patron",                          "Patron",               "",                               S_SANAT_TAM),
            new BulguCase("UAT_076",  "tefo",                            "Tefo",                 "",                               S_SANAT_TAM),
            new BulguCase("UAT_077",  "doğuş",                           "Doğuş",                "",                               S_SANAT_TAM),
            new BulguCase("UAT_084",  "ben fero",                        "Ben Fero",             "",                               S_SANAT_TAM),
            new BulguCase("UAT_086",  "inji",                            "INJI",                 "",                               S_SANAT_TAM),
            new BulguCase("UAT_087",  "rihanna",                         "Rihanna",              "",                               S_SANAT_TAM),
            new BulguCase("UAT_089",  "mavi",                            "Mavi",                 "",                               S_SANAT_TAM),
            new BulguCase("UAT_090",  "velet",                           "Velet",                "",                               S_SANAT_TAM),
            new BulguCase("UAT_091",  "adamlar",                         "Adamlar",              "",                               S_SANAT_TAM),
            new BulguCase("UAT_102",  "blackpink",                       "BLACKPINK",            "",                               S_SANAT_TAM),
            new BulguCase("UAT_104",  "sia",                             "Sia",                  "",                               S_SANAT_TAM),
            new BulguCase("UAT_110",  "shakira",                         "Shakira",              "",                               S_SANAT_TAM),
            new BulguCase("UAT_139",  "madonna",                         "Madonna",              "",                               S_SANAT_TAM),

            // ═════════════════════════════════════════════════════════════════
            // SANATÇI · KISMİ AD
            // Kullanıcı sanatçının adını / soyadını / ilk kelimesini yazmış
            // ═════════════════════════════════════════════════════════════════
            new BulguCase("BULGU_006", "blok",                           "Blok3",                "",                               S_SANAT_KISMI),
            new BulguCase("BULGU_022", "teo",                            "Teoman",               "",                               S_SANAT_KISMI),
            new BulguCase("BULGU_073", "arabam",                         "Sefo",                 "",                               S_SANAT_KISMI), // şarkı adıyla sanatçı arama
            // UAT
            new BulguCase("UAT_016",  "aleyna",                          "Aleyna Tilki",         "",                               S_SANAT_KISMI),
            new BulguCase("UAT_025",  "ferdi",                           "Ferdi Tayfur",         "",                               S_SANAT_KISMI),
            new BulguCase("UAT_026",  "mabel",                           "Mabel Matiz",          "",                               S_SANAT_KISMI),
            new BulguCase("UAT_027",  "yıldız",                          "Yıldız Tilbe",         "",                               S_SANAT_KISMI),
            new BulguCase("UAT_028",  "azer",                            "Azer Bülbül",          "",                               S_SANAT_KISMI),
            new BulguCase("UAT_032",  "blok",                            "BLOK3",                "",                               S_SANAT_KISMI),
            new BulguCase("UAT_033",  "serdar",                          "Serdar Ortaç",         "",                               S_SANAT_KISMI),
            new BulguCase("UAT_036",  "cengiz",                          "Cengiz Kurtoğlu",      "",                               S_SANAT_KISMI),
            new BulguCase("UAT_038",  "neşet",                           "Neşet Ertaş",          "",                               S_SANAT_KISMI),
            new BulguCase("UAT_039",  "melike",                          "Melike Şahin",         "",                               S_SANAT_KISMI),
            new BulguCase("UAT_042",  "orhan",                           "Orhan Gencebay",       "",                               S_SANAT_KISMI),
            new BulguCase("UAT_049",  "soner",                           "Soner Sarıkabadayı",   "",                               S_SANAT_KISMI),
            new BulguCase("UAT_050",  "ati",                             "Ati242",               "",                               S_SANAT_KISMI),
            new BulguCase("UAT_053",  "norm",                            "Norm Ender",           "",                               S_SANAT_KISMI),
            new BulguCase("UAT_055",  "sibel",                           "Sibel Can",            "",                               S_SANAT_KISMI),
            new BulguCase("UAT_056",  "irem",                            "İrem Derici",          "",                               S_SANAT_KISMI),
            new BulguCase("UAT_058",  "musa",                            "Musa Eroğlu",          "",                               S_SANAT_KISMI),
            new BulguCase("UAT_062",  "kurtuluş",                        "Kurtuluş Kuş",         "",                               S_SANAT_KISMI),
            new BulguCase("UAT_063",  "cash",                            "Cash Flow",            "",                               S_SANAT_KISMI),
            new BulguCase("UAT_071",  "reyn",                            "Reynmen",              "",                               S_SANAT_KISMI),
            new BulguCase("UAT_072",  "mahsun",                          "Mahsun Kırmızıgül",    "",                               S_SANAT_KISMI),
            new BulguCase("UAT_078",  "funda",                           "Funda Arar",           "",                               S_SANAT_KISMI),
            new BulguCase("UAT_080",  "sura",                            "Sura İskenderli",      "",                               S_SANAT_KISMI),
            new BulguCase("UAT_081",  "rafet",                           "Rafet El Roman",       "",                               S_SANAT_KISMI),
            new BulguCase("UAT_085",  "haluk",                           "Haluk Levent",         "",                               S_SANAT_KISMI),
            new BulguCase("UAT_088",  "lvbel",                           "Lvbel C5",             "",                               S_SANAT_KISMI),
            new BulguCase("UAT_092",  "zerrin",                          "Zerrin Özer",          "",                               S_SANAT_KISMI),
            new BulguCase("UAT_093",  "selda",                           "Selda Bağcan",         "",                               S_SANAT_KISMI),
            new BulguCase("UAT_094",  "bilal",                           "Bilal Sonses",         "",                               S_SANAT_KISMI),
            new BulguCase("UAT_096",  "gülden",                          "Gülden Karaböcek",     "",                               S_SANAT_KISMI),
            new BulguCase("UAT_097",  "ibrahim tat",                     "İbrahim Tatlıses",     "",                               S_SANAT_KISMI),
            new BulguCase("UAT_098",  "engin",                           "Engin Nurşani",        "",                               S_SANAT_KISMI),
            new BulguCase("UAT_099",  "şebnem",                          "Şebnem Ferah",         "",                               S_SANAT_KISMI),
            new BulguCase("UAT_100",  "ayaz",                            "Ayaz Erdoğan",         "",                               S_SANAT_KISMI),
            new BulguCase("UAT_101",  "ajda",                            "Ajda Pekkan",          "",                               S_SANAT_KISMI),
            new BulguCase("UAT_107",  "aynur",                           "Aynur Aydın",          "",                               S_SANAT_KISMI),
            new BulguCase("UAT_109",  "hayko",                           "Hayko Cepkin",         "",                               S_SANAT_KISMI),
            new BulguCase("UAT_113",  "koray",                           "Koray Avcı",           "",                               S_SANAT_KISMI),
            new BulguCase("UAT_114",  "ümit",                            "Ümit Besen",           "",                               S_SANAT_KISMI),
            new BulguCase("UAT_115",  "elif buse",                       "Elif Buse Doğan",      "",                               S_SANAT_KISMI),
            new BulguCase("UAT_123",  "özcan",                           "Özcan Deniz",          "",                               S_SANAT_KISMI),
            new BulguCase("UAT_125",  "deha",                            "DEHA INC.",            "",                               S_SANAT_KISMI),
            new BulguCase("UAT_129",  "taylor",                          "Taylor Swift",         "",                               S_SANAT_KISMI),

            // ═════════════════════════════════════════════════════════════════
            // SANATÇI · YAZIM TOLERANSI
            // Yazım yanlışı / fonetik benzerlik / Türkçe karakter eksikliği /
            // bitişik yazım / kelime sırası farklılığı (sanatçı araması)
            // ═════════════════════════════════════════════════════════════════
            new BulguCase("BULGU_021", "tarkn",                          "Tarkan",               "",                               S_SANAT_YAZIM), // sesli harf düşmesi
            new BulguCase("BULGU_053", "kök$l",                          "kök$vl",               "",                               S_SANAT_YAZIM), // yazım yanlışı
            new BulguCase("BULGU_099", "çakal",                          "cakal",                "",                               S_SANAT_YAZIM), // Türkçe char (ç→c)
            new BulguCase("BULGU_115", "pozi",                           "Poizi",                "",                               S_SANAT_YAZIM), // eksik harf
            new BulguCase("BULGU_119", "hşdra",                          "Hidra",                "",                               S_SANAT_YAZIM), // sesli harfler düşmüş
            // UAT
            new BulguCase("UAT_003",  "goksel",                          "Göksel",               "",                               S_SANAT_YAZIM), // Türkçe char (ö→o)
            new BulguCase("UAT_014",  "can ozan",                        "Canozan",              "",                               S_SANAT_YAZIM), // boşluk ekleme
            new BulguCase("UAT_020",  "ezel",                            "Ezhel",                "",                               S_SANAT_YAZIM), // fonetik (z↔zh)
            new BulguCase("UAT_035",  "reymen",                          "Reynmen",              "",                               S_SANAT_YAZIM), // eksik n
            new BulguCase("UAT_041",  "çakal",                           "cakal",                "",                               S_SANAT_YAZIM), // Türkçe char
            new BulguCase("UAT_047",  "emircan",                         "Emir Can İğrek",       "",                               S_SANAT_YAZIM), // bitişik yazım (kısmi + birleşik)
            new BulguCase("UAT_074",  "emircan iğrek",                   "Emir Can İğrek",       "",                               S_SANAT_YAZIM), // boşluksuz birinci kelime
            new BulguCase("UAT_105",  "izel",                            "İzel",                 "",                               S_SANAT_YAZIM), // Türkçe char (i→İ)
            new BulguCase("UAT_118",  "hejan",                           "Heijan",               "",                               S_SANAT_YAZIM), // fonetik (j→ij)
            new BulguCase("UAT_127",  "semicek",                         "Semicenk",             "",                               S_SANAT_YAZIM), // hatalı harf (k→nk)
            new BulguCase("UAT_130",  "beyonce",                         "Beyoncé",              "",                               S_SANAT_YAZIM), // aksan eksikliği
            new BulguCase("UAT_131",  "emre gel",                        "Emre Fel",             "",                               S_SANAT_YAZIM), // fonetik (g→f)
            new BulguCase("UAT_133",  "sibelcan",                        "Sibel Can",            "",                               S_SANAT_YAZIM), // boşluk eksik
            new BulguCase("UAT_135",  "kofn",                            "KÖFN",                 "",                               S_SANAT_YAZIM), // Türkçe char (o→ö)
            new BulguCase("UAT_142",  "sertap",                          "Sertab Erener",        "",                               S_SANAT_YAZIM), // fonetik (p→b) + kısmi
            new BulguCase("UAT_146",  "mr ve ötei",                      "Mor ve Ötesi",         "",                               S_SANAT_YAZIM), // kısaltma + yazım yanlışı
            new BulguCase("UAT_147",  "dolu kadhi tut",                  "Dolu Kadehi Ters Tut", "",                               S_SANAT_YAZIM), // yazım yanlışı + eksik kelime

            // ═════════════════════════════════════════════════════════════════
            // SANATÇI · KISALTMA / ALİAS
            // Resmi ismin kısaltması, rumuz, sayısal alias, içiçe yazım
            // ═════════════════════════════════════════════════════════════════
            new BulguCase("BULGU_110", "lvc5",                           "Lvbel C5",             "",                               S_SANAT_ALIAS),
            new BulguCase("BULGU_116", "level c5",                       "Lvbel C5",             "",                               S_SANAT_ALIAS), // fonetik alias
            new BulguCase("BULGU_117", "levelc5",                        "Lvbel C5",             "",                               S_SANAT_ALIAS), // bitişik fonetik alias
            // UAT
            new BulguCase("UAT_002",  "84",                              "seksendört",           "",                               S_SANAT_ALIAS), // sayısal alias
            new BulguCase("UAT_046",  "sago",                            "Sagopa Kajmer",        "",                               S_SANAT_ALIAS), // yaygın rumuz
            new BulguCase("UAT_103",  "no1",                             "No.1",                 "",                               S_SANAT_ALIAS), // bitişik + nokta eksik
            new BulguCase("UAT_111",  "halo",                            "Halodayı",             "",                               S_SANAT_ALIAS), // yaygın kısaltma
            new BulguCase("UAT_112",  "50",                              "50 Cent",              "",                               S_SANAT_ALIAS), // sayısal kısaltma
            new BulguCase("UAT_120",  "no 1",                            "No.1",                 "",                               S_SANAT_ALIAS), // boşluklu + nokta eksik
            new BulguCase("UAT_149",  "dktt",                            "Dolu Kadehi Ters Tut", "",                               S_SANAT_ALIAS), // harf kısaltması

            // ═════════════════════════════════════════════════════════════════
            // PLAYLİST
            // Kullanıcı kategori / tür adıyla çalma listesi arıyor
            // ═════════════════════════════════════════════════════════════════
            new BulguCase("BULGU_026", "90 lar",                         "",  "[Playlist] 90",           S_PLAYLIST),
            new BulguCase("BULGU_027", "çocuk",                          "",  "[Playlist] çocuk",        S_PLAYLIST),
            new BulguCase("BULGU_064", "pop",                            "",  "[Playlist] pop",          S_PLAYLIST),
            new BulguCase("BULGU_069", "yabancı",                        "",  "[Playlist] yabancı",      S_PLAYLIST),
            new BulguCase("BULGU_087", "akustik",                        "",  "[Playlist] akustik",      S_PLAYLIST),
            // UAT
            new BulguCase("UAT_004",  "pop",              "",  "[Playlist] pop",          S_PLAYLIST),
            new BulguCase("UAT_005",  "90",               "",  "[Playlist] 90",           S_PLAYLIST),
            new BulguCase("UAT_006",  "90lar",            "",  "[Playlist] 90",           S_PLAYLIST),
            new BulguCase("UAT_007",  "90'lar",           "",  "[Playlist] 90",           S_PLAYLIST),
            new BulguCase("UAT_008",  "90s",              "",  "[Playlist] 90",           S_PLAYLIST),
            new BulguCase("UAT_009",  "90 lar",           "",  "[Playlist] 90",           S_PLAYLIST),
            new BulguCase("UAT_029",  "arabesk",          "",  "[Playlist] arabesk",      S_PLAYLIST),
            new BulguCase("UAT_031",  "ilahi",            "",  "[Playlist] ilahi",        S_PLAYLIST),
            new BulguCase("UAT_037",  "karadeniz",        "",  "[Playlist] karadeniz",    S_PLAYLIST),
            new BulguCase("UAT_040",  "halay",            "",  "[Playlist] halay",        S_PLAYLIST),
            new BulguCase("UAT_043",  "yabancı",          "",  "[Playlist] yabancı",      S_PLAYLIST),
            new BulguCase("UAT_044",  "roman",            "",  "[Playlist] roman",        S_PLAYLIST),
            new BulguCase("UAT_048",  "çocuk",            "",  "[Playlist] çocuk",        S_PLAYLIST),
            new BulguCase("UAT_065",  "oyun hava",        "",  "[Playlist] oyun hava",    S_PLAYLIST),
            new BulguCase("UAT_069",  "spor",             "",  "[Playlist] spor",         S_PLAYLIST),
            new BulguCase("UAT_073",  "klasik",           "",  "[Playlist] klasik",       S_PLAYLIST),
            new BulguCase("UAT_079",  "ankara",           "",  "[Playlist] ankara",       S_PLAYLIST),
            new BulguCase("UAT_082",  "akustik",          "",  "[Playlist] akustik",      S_PLAYLIST),
            new BulguCase("UAT_083",  "çocuk şarkıları",  "",  "[Playlist] çocuk",        S_PLAYLIST),
            new BulguCase("UAT_106",  "türkçe",           "",  "[Playlist] türkçe",       S_PLAYLIST),
            new BulguCase("UAT_108",  "80",               "",  "[Playlist] 80",           S_PLAYLIST),
            new BulguCase("UAT_116",  "rock",             "",  "[Playlist] rock",         S_PLAYLIST),
            new BulguCase("UAT_121",  "dans",             "",  "[Playlist] dans",         S_PLAYLIST),
            new BulguCase("UAT_122",  "türk sanat",       "",  "[Playlist] türk sanat",   S_PLAYLIST),
            new BulguCase("UAT_134",  "meditasyon",       "",  "[Playlist] meditasyon",   S_PLAYLIST),
            new BulguCase("UAT_138",  "90 lar pop",       "",  "[Playlist] pop",          S_PLAYLIST),
            new BulguCase("UAT_140",  "ramazan",          "",  "[Playlist] ramazan",      S_PLAYLIST)
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

            boolean isAlbumResult = song.isEmpty() && !album.isEmpty();
            if (isAlbumResult && !expTl.isEmpty()) continue;

            String trackName = song.isEmpty() ? album : song;
            String tl = normalizeTrack(trackName.trim().toLowerCase(TR));
            String pl = performer.trim().toLowerCase(TR);

            boolean trackOk  = expTl.isEmpty() || tl.equals(expTl);
            boolean artistOk = expAl.isEmpty() || pl.equals(expAl);

            if ((!trackOk || !artistOk) && pl.isEmpty() && !expAl.isEmpty()) {
                for (String sep : new String[]{" \u2014 ", " \u2013 ", " - ", " -", "- "}) {
                    int idx = tl.lastIndexOf(sep);
                    if (idx > 0) {
                        String left  = normalizeTrack(tl.substring(0, idx).trim());
                        String right = normalizeTrack(tl.substring(idx + sep.length()).trim());
                        if ((expTl.isEmpty() || left.equals(expTl)) && right.equals(expAl)) {
                            trackOk = true; artistOk = true; break;
                        }
                        if ((expTl.isEmpty() || right.equals(expTl)) && left.equals(expAl)) {
                            trackOk = true; artistOk = true; break;
                        }
                    }
                }
            }

            boolean hasContent      = !trackName.isEmpty() || (!performer.isEmpty() && expTl.isEmpty());
            boolean performerMissing = performer.isEmpty() && !trackName.isEmpty() && !expTl.isEmpty();

            if (trackOk && (artistOk || performerMissing) && hasContent) {
                return i + 1;
            }
        }
        return 0;
    }

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
        if (bc.caseId().startsWith("UAT_")) return uatAciklamasi(bc);
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
            return "'" + bc.term() + "' araması için ilgili içerik bekleniyor — gözlem case'i.";
        }
    }

    private static String bulguAciklamasi(String caseId) {
        switch (caseId) {
            case "BULGU_001": return "Hermes yazdığımızda Batuflexin Hermès 2.0 şarkısını bulmuyor.";
            case "BULGU_002": return "a canım araması yapıldığında mabel matiz a canım şarkısını bulmuyor.";
            case "BULGU_003": return "acanım araması yapılınca mabel matiz a canım şarkısını bulmuyor.";
            case "BULGU_004": return "olmazlara vuruluyorum araması yapıldığında albüm sonucu şarkıdan önce geliyor.";
            case "BULGU_005": return "çıkmaz bir sokakta araması yapınca aynı şarkının albümü daha önde görünüyor.";
            case "BULGU_006": return "blok araması yapınca sanatçı blok3 ilk sırada gelmiyor bulması beklenirdi.";
            case "BULGU_007": return "kusura bakma yazınca blok 3 kusura bakma şarkısını ilk sıralarda çıkmıyor.";
            case "BULGU_008": return "mfö yazınca mfö sanatçısı ilk sırada çıkmıyor.";
            case "BULGU_010": return "maraton yazınca Ati242 Maraton şarkısı çıkmıyor.";
            case "BULGU_011": return "geri ver araması yapıyorum, Wegh geri ver şarkısı ilk sırada gelmiyor.";
            case "BULGU_012": return "saygımdan araması yapıyorum Bengü saygımdan şarkısı ilk sırada değil albüm ilk sırada.";
            case "BULGU_013": return "meğerse yazıyorum, Liner meğerse şarkısını bulmuyor.";
            case "BULGU_014": return "çok pardon yazıyorum, Lvbel C5 COOOK PARDON şarkısını bulmadı.";
            case "BULGU_015": return "dacia yazıyorum Lvbel C5 Dacia şarkısını ilk sırada getirmiyor.";
            case "BULGU_016": return "Manifest yazıyorum manifest sanatçısı en üstte çıkarmıyor.";
            case "BULGU_017": return "semicenk araması yapılır — Semicenk şarkıları sanatçı kartlarının ardında 7. sıraya düşüyor.";
            case "BULGU_019": return "yalnızlığın çaresini yazıyorum gripin yalnızlığın çaresini bulmuşlar şarkısını bulmuyor.";
            case "BULGU_020": return "yalnızlığın çaresini bulmuşlar yazıyorum şarkı yerine albüm en üstte geliyor.";
            case "BULGU_021": return "tarkn yazıyorum tarkan olduğunu anlıyor arama yapıyor ancak tarkan sanatçısını en üstte göstermiyor.";
            case "BULGU_022": return "teo yazıyorum Teoman sanatçısının gelmesi beklenir.";
            case "BULGU_023": return "yapar mısın yazıyorum Poizi yapar mısın şarkısı ilk sırada gelmiyor.";
            case "BULGU_024": return "yerinde yazıyorum Sefo Yerinde Dur şarkısı ilk sırada gelmiyor, başka sanatçı öne geçiyor.";
            case "BULGU_025": return "yerinde dur yazıyorum Bora Duran sanatçısı ilk sırada geliyor, Sefo Yerinde Dur şarkısı ilk sırada değil.";
            case "BULGU_028": return "ey aşk yazıyorum sezen aksu ey aşk şarkısı birebir eşleşmesine rağmen ilk sırada gelmiyor eypio sanatçısı ilk sırada geliyor.";
            case "BULGU_029": return "simarik araması yapılır — fonetik benzerlik (simarik ~ simpatik) yanlış eşleşmeye yol açıyor.";
            case "BULGU_030": return "şımarık araması yapıldığında Tarkan Şımarık 1. sırada doğru geliyor.";
            case "BULGU_031": return "giderim kırağınan birebir eşleşmesine rağmen Onur Şan - giderim kırağınan şarkısı 6-7. sırada geliyor.";
            case "BULGU_032": return "dame un grr araması yapılır aynı isimdeki şarkıyı bulamıyor.";
            case "BULGU_033": return "ara beni lütfen araması yapılır aynı isimdeki şarkı 2. sırada geliyor Funda Arar ilk sırada geliyor.";
            case "BULGU_034": return "vidrado em yazıyorum ya da vidrado em voce yazıyorum Vidrado Em Você şarkısını bulamıyor.";
            case "BULGU_035": return "aşk yok olmaktır araması yapılır birebir eşleşen şarkı 2. sırada çıkmaktadır ilk sırada başka içerik geliyor.";
            case "BULGU_036": return "can efendim yazıyorum aynı isimli şarkıyı bulamıyor.";
            case "BULGU_037": return "çıt çıt yazıyorum aynı isimdeki şarkılar ilk 10da gelmiyor.";
            case "BULGU_038": return "çıt çıt çedene araması yapılır, çıt çıt şarkısı top-10da gelmiyor.";
            case "BULGU_039": return "çıkar biri karşıma yazıyorum aynı isimli Poizi şarkısı 4. sırada çıkıyor.";
            case "BULGU_040": return "hav hav yazıyorum Lvbel C5 havhavhav şarkısını bulmuyor.";
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
            case "BULGU_053": return "kök$l aramasında kök sanatçısını bulmuyor.";
            case "BULGU_054": return "just the way you are yazıyorum aynı isimli şarkılar ilk 10da çıkmıyor.";
            case "BULGU_055": return "utku akkaya yazıyorum aynı isimli birebir eşleşen sanatçı olmasına rağmen ilkay akkaya ilk sırada çıkıyor.";
            case "BULGU_056": return "snap yazıyorum aynı isimli manifest şarkısı 7. sırada geliyor.";
            case "BULGU_058": return "y poizi yazıyorum Poizi Y şarkısını bulmuyor.";
            case "BULGU_059": return "ama başaramadım yazıyorum aynı isimli burak bulut şarkısı 2. sırada geliyor.";
            case "BULGU_060": return "kts manifest yazıyorum manifestin kts şarkısı 2. sırada geliyor.";
            case "BULGU_061": return "adına bir çizik çektim yazıyorum aynı isme sahip şarkı 4. sırada geliyor.";
            case "BULGU_062": return "yaramızda kalsın yazıyorum Merve Özbeyin klibi ilk sırada geliyor aynı sanatçının şarkısı aramada hiç çıkmıyor.";
            case "BULGU_063": return "sev yeter yazıyorum birebir eşleşen şarkılar 4. ve 5. sırada geliyor.";
            case "BULGU_064": return "pop araması yapılır pop kategorisinde bir playlist top-10da görünmeli.";
            case "BULGU_065": return "kaybolurum gülüşünde araması yapılıyor İkilem sanatçısının şarkısından önce klip ve albümü geliyor.";
            case "BULGU_066": return "bak ben yara gibiyim araması yapıyorum, Nalan şarkısının 1. sırada çıkması beklenirken sanatçı 2. sırada şarkı 3. sırada çıkıyor.";
            case "BULGU_067": return "çölüme yağmur oldun araması yapıyorum müslüm gürses affet şarkısı gelmiyor.";
            case "BULGU_068": return "sana hastayım anlasana yazıyorum Derya Uluğ Yansıma şarkısı ilk sırada gelmiyor.";
            case "BULGU_069": return "yabancı araması yapılır yabancı playlist top-10da görünmeli.";
            case "BULGU_070": return "Dua Lipa Shine öneriye tıklanır böyle bir içerik getirmiyor şarkı bulmuyor.";
            case "BULGU_071": return "ağlama ben ağlarım araması yapıyorum, Canozan şarkısı 6. sırada çıkıyor ve aynı isimli albümden daha aşağıda.";
            case "BULGU_072": return "ağlama ben araması yapıyorum Canozan şarkısının gelmesi beklenir.";
            case "BULGU_073": return "arabam yazıyorum Sefo sanatçısının gelmesi beklenir.";
            case "BULGU_074": return "erik yazıyorum erik dalı şarkısı ilk sırada çıkması gerekirken şarkının klibi ilk sırada çıkıyor.";
            case "BULGU_075": return "hadi ya araması yapılır melis kar yatıya şarkısının çıkmasını bekliyoruz.";
            case "BULGU_076": return "babalar araması yapılır Blok3-PATLAT şarkısının ilk sırada gelmesi beklenir.";
            case "BULGU_077": return "karakedi yazıyorum Melis Fis sanatçısını ilk sırada çıkarıyor ancak Kara Kedi şarkısını 6. sırada getiriyor.";
            case "BULGU_078": return "doğuştan yazıyorum Lvbel C5 Doğuştan Beri Haklıyım şarkısı gelmiyor.";
            case "BULGU_079": return "silemez o beni yazıyorum, Yıldız Tilbe sanatçısına ait Dizine Dursun şarkısının ilk sırada olması beklenir.";
            case "BULGU_080": return "düldül araması yapılır, birebir eşleşen Mabel Matiz şarkısı ilk sıralarda yer almıyor.";
            case "BULGU_081": return "bunca yıl araması yapılır Dedüblümanın aynı isimli şarkısı 5. sırada çıkmaktadır.";
            case "BULGU_082": return "düldül araması öneriden tıklanır; Düldül şarkısı 4. sırada çıkar ilk sırada çıkmalıydı.";
            case "BULGU_083": return "çetin ceviz şerbetli mayam araması yapılır şarkı sözlerinden Melike Şahin - Canın Beni Çekti şarkısı bulamuyor.";
            case "BULGU_084": return "bir ya da bir motive araması yapıldığında Motive sanatçısının bir şarkısını bulamıyor.";
            case "BULGU_086": return "perde araması yapılır, Poizi perde şarkısı popüler olmasına ve birebir eşleşmesine rağmen 5. sırada gelmektedir.";
            case "BULGU_087": return "akustik araması yapılır akustik playlist top-10da görünmeli.";
            case "BULGU_088": return "derya bedavacı araması yapıyorum aynı isme sahip sanatçı 3. sırada çıkıyor Derya Uluğ ilk sırada çıkıyor.";
            case "BULGU_089": return "phonk yazıyorum DEHA INC. Phonk şarkısının gelmesi beklenir.";
            case "BULGU_090": return "ceza yazıyorum Ceza sanatçısı ilk sırada çıkıyor, Ceza büyük harfle yazıyorum Ceza sanatçısı 2. sırada çıkıyor.";
            case "BULGU_091": return "Ceza büyük harfle yazıldığında sanatçı 2. sırada çıkıyor (case sensitivity sorunu).";
            case "BULGU_092": return "sonbahar yazıyorum Era7Capone sanatçısının SONBAHAR şarkısı çok alt sıralarda geliyor ilk 3te görünmesi beklenir.";
            case "BULGU_093": return "acem kızı yazıyorum birebir eşleşen şarkılar olmasına rağmen ilk sırada Fikret Kızılok sanatçısı gösteriliyor.";
            case "BULGU_094": return "Hacel obası yazıyorum ilk sırada klipler geliyor.";
            case "BULGU_095": return "yalan yazıyorum ilk sırada Majid Yalan isminde bir sanatçı geliyor, şarkıların gelmesi önemli.";
            case "BULGU_096": return "bana sor yazıyorum aynı isme sahip şarkılar olmasına rağmen ilk sırada ferdi tayfur sanatçısı çıkıyor.";
            case "BULGU_097": return "rüya manifest yazıyorum, aynı isimli manifest şarkısı 6. sırada geliyor.";
            case "BULGU_098": return "rüya yazıyorum manifestin aynı isimli şarkısı ilk 10 sonuçta gelmiyor.";
            case "BULGU_099": return "çakal araması yapıldığında bu isimdeki sanatçı için aramalar yapılır.";
            case "BULGU_100": return "ara kelimesi yazılır ilk sırada birebir eşleşen zeynep bastık ara şarkısı çıkması beklenirken Funda Arar sanatçısı çıkmaktadır.";
            case "BULGU_101": return "14 bahar araması yapılır aynı isme sahip Mert Demir şarkısı 9. sırada çıkmaktadır.";
            case "BULGU_102": return "yaşar araması yapıldığında sanatçı Yaşar yerine ilk sırada Ebru Yaşar çıkmaktadır.";
            case "BULGU_103": return "ela mana yazıyorum, birebir eşleşen şarkı 8. sırada geliyor.";
            case "BULGU_104": return "yekten yazıyorum Demet Akalın Yekten şarkısının gelmesi beklenir.";
            case "BULGU_105": return "erik dalı yazıyorum ilk sonuçlar klip geliyor.";
            case "BULGU_106": return "elfida yazıyorum birebir eşleşen şarkı 2. sırada geliyor ilk sırada Gelida isminde bir sanatçı geliyor.";
            case "BULGU_107": return "yazan kalem siyah araması yapıyorum ilk 20 sonuçta birebir eşleşmesine rağmen bir şarkı sonucu gelmiyor.";
            case "BULGU_108": return "Mihriban araması yapıyorum aynı isimli şarkının gelmesi beklenir.";
            case "BULGU_110": return "lvc5 yazıldığında Lvbel C5 sanatçısının gelmesi beklenir ancak gelmiyor.";
            case "BULGU_111": return "merdo yazıldığında birebir eşleşen şarkıların daha üst sırada gelmesi beklenir.";
            case "BULGU_112": return "Gökhan Özen yazılır birebir eşleşen sanatçı 2. sırada gelmektedir.";
            case "BULGU_113": return "sigara yazıyorum aynı isimli şarkının gelmesi beklenir.";
            case "BULGU_114": return "dandini yazıyorum Ninni Bebek dandini dandini dastana şarkısının gelmesi beklenir.";
            case "BULGU_115": return "pozi aramasında poizi sanatçısını bulması beklenir şuan bulamıyor.";
            case "BULGU_116": return "level c5 aramasında Lvbel C5 sanatçısını bulması beklenir.";
            case "BULGU_117": return "levelc5 aramasında Lvbel C5 sanatçısını bulması beklenir.";
            case "BULGU_118": return "çelik yazıyorum sanatçı çelik 2. sırada çıkıyor birebir eşleşmesine rağmen. İlk sırada Ayla Çelik geliyor.";
            case "BULGU_119":  return "hşdra yazıyorum Hidra sanatçısının gelmesi beklenir.";
            case "BULGU_119_B": return "Haluk Levent yazıyorum ilk sırada Levent Yüksel çıkıyor. Birebir eşleşen sanatçı ilk sırada gelmeli.";
            case "BULGU_119_C": return "farzet yazıyorum İlyas Yalçıntaş Farzet şarkısının gelmesi beklenir.";
            case "BULGU_120": return "Mustafa Yıldızdoğan araması yapıyorum 2. ve 3. sırada ismi mustafa olan başka sanatçılar geliyor.";
            case "BULGU_121": return "misket yazıyorum birebir eşleşen şarkı en üstte yer almıyor.";
            case "BULGU_122": return "kara sevda yazıyorum birebir eşleşen şarkı 4. sırada çıkıyor.";
            case "BULGU_123": return "parla yazıyorum birebir eşleşen şarkı üst sıralarda çıkmıyor sadece sanatçılar üstte çıkıyor.";
            case "BULGU_124": return "kırmızı balık yazıyorum ilk sırada Mahsun Kırmızıgül çıkıyor birebir eşleşen başka bir şarkı olmasına rağmen.";
            case "BULGU_026": return "90 lar araması yapılır 90'lar playlist top-10da görünmeli.";
            case "BULGU_027": return "çocuk araması yapılır çocuk playlist top-10da görünmeli.";
            default:           return caseId + " — Bulgu açıklaması tanımlanmamış.";
        }
    }
}