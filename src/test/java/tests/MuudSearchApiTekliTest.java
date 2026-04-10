package tests;

import client.MuudSearchApi;
import config.TestConfig;
import domain.Rule;
import domain.SearchCase;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import report.ExcelTestReportWriter;
import report.TestResultRow;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static util.MuudSearchUtils.*;

public class MuudSearchApiTekliTest extends TestConfig {

    private static final int DEBUG_LOOKUP_LIMIT = 50;
    private static final boolean EXCEL_REPORT_ENABLED =
            Boolean.parseBoolean(System.getProperty("excelReport", "false"));
    private static final List<TestResultRow> REPORT_ROWS = new ArrayList<>();
    private static MuudSearchApi api;

    @BeforeAll
    static void setupClient() {
        api = new MuudSearchApi();
    }

    static Stream<SearchCase> cases() {
        return Stream.of(

                // =========================================================================
                // G1 — SANATÇI: TAM / TEK KELİME DOĞRUDAN ARAMA (29 case)
                // Sanatçının tam adını veya tek isimli sanatçı adını doğrudan yazarak arama.
                // Diacritic (ö/o, ü/u vb.) farkları bu grupta tolere edilir.
                // Sistemin temel benchmark'ı; bu grupta alınan fail en kritik önceliktedir.
                // =========================================================================
                new SearchCase("G1_001", "u2",          "performer", 5, Rule.FIRST_ARTIST_IS, "u2",          "", 200, false),
                new SearchCase("G1_002", "goksel",       "performer", 5, Rule.FIRST_ARTIST_IS, "Göksel",      "", 200, false),
                new SearchCase("G1_003", "edis",         "performer", 5, Rule.FIRST_ARTIST_IS, "Edis",        "", 200, false),
                new SearchCase("G1_004", "uzi",          "performer", 5, Rule.FIRST_ARTIST_IS, "Uzi",         "", 200, false),
                new SearchCase("G1_005", "mfö",          "performer", 5, Rule.FIRST_ARTIST_IS, "MFÖ",         "", 200, false),
                new SearchCase("G1_006", "sıla",         "performer", 5, Rule.FIRST_ARTIST_IS, "Sıla",        "", 200, false),
                new SearchCase("G1_007", "çakal",        "performer", 5, Rule.FIRST_ARTIST_IS, "cakal",       "", 200, false),
                new SearchCase("G1_008", "eminem",       "performer", 5, Rule.FIRST_ARTIST_IS, "Eminem",      "", 200, false),
                new SearchCase("G1_009", "güneş",        "performer", 5, Rule.FIRST_ARTIST_IS, "Güneş",       "", 200, false),
                new SearchCase("G1_010", "dua lipa",     "performer", 5, Rule.FIRST_ARTIST_IS, "Dua Lipa",    "", 200, false),
                new SearchCase("G1_011", "mero",         "performer", 5, Rule.FIRST_ARTIST_IS, "Mero",        "", 200, false),
                new SearchCase("G1_012", "murda",        "performer", 5, Rule.FIRST_ARTIST_IS, "Murda",       "", 200, false),
                new SearchCase("G1_013", "inna",         "performer", 5, Rule.FIRST_ARTIST_IS, "Inna",        "", 200, false),
                new SearchCase("G1_014", "adele",        "performer", 5, Rule.FIRST_ARTIST_IS, "Adele",       "", 200, false),
                new SearchCase("G1_015", "mor ve ötesi", "performer", 5, Rule.FIRST_ARTIST_IS, "mor ve ötesi","", 200, false),
                new SearchCase("G1_016", "patron",       "performer", 5, Rule.FIRST_ARTIST_IS, "Patron",      "", 200, false),
                new SearchCase("G1_017", "tefo",         "performer", 5, Rule.FIRST_ARTIST_IS, "Tefo",        "", 200, false),
                new SearchCase("G1_018", "doğuş",        "performer", 5, Rule.FIRST_ARTIST_IS, "Doğuş",       "", 200, false),
                new SearchCase("G1_019", "ben fero",     "performer", 5, Rule.FIRST_ARTIST_IS, "Ben Fero",    "", 200, false),
                new SearchCase("G1_020", "inji",         "performer", 5, Rule.FIRST_ARTIST_IS, "INJI",        "", 200, false),
                new SearchCase("G1_021", "rihanna",      "performer", 5, Rule.FIRST_ARTIST_IS, "Rihanna",     "", 200, false),
                new SearchCase("G1_022", "mavi",         "performer", 5, Rule.FIRST_ARTIST_IS, "Mavi",        "", 200, false),
                new SearchCase("G1_023", "velet",        "performer", 5, Rule.FIRST_ARTIST_IS, "Velet",       "", 200, false),
                new SearchCase("G1_024", "adamlar",      "performer", 5, Rule.FIRST_ARTIST_IS, "Adamlar",     "", 200, false),
                new SearchCase("G1_025", "blackpink",    "performer", 5, Rule.FIRST_ARTIST_IS, "BLACKPINK",   "", 200, false),
                new SearchCase("G1_026", "sia",          "performer", 5, Rule.FIRST_ARTIST_IS, "Sia",         "", 200, false),
                new SearchCase("G1_027", "shakira",      "performer", 5, Rule.FIRST_ARTIST_IS, "Shakira",     "", 200, false),
                new SearchCase("G1_028", "beyonce",      "performer", 5, Rule.FIRST_ARTIST_IS, "Beyonce",     "", 200, false),
                new SearchCase("G1_029", "madonna",      "performer", 5, Rule.FIRST_ARTIST_IS, "Madonna",     "", 200, false),

                // =========================================================================
                // G2 — SANATÇI: KISMİ AD / İLK İSİM İLE ARAMA (39 case)
                // Kullanıcının yalnızca ilk isim, soyadı veya adın bir bölümünü yazarak arama.
                // Sistemin "ne demek istediğini anlıyor mu?" intent kapasitesini test eder.
                // Fail alan case'ler öneri motorunun zayıf olduğuna işaret eder.
                // =========================================================================
                new SearchCase("G2_001", "aleyna",        "performer", 5, Rule.FIRST_ARTIST_IS, "Aleyna Tilki",          "", 200, false),
                new SearchCase("G2_002", "ferdi",         "performer", 5, Rule.FIRST_ARTIST_IS, "Ferdi Tayfur",          "", 200, false),
                new SearchCase("G2_003", "mabel",         "performer", 5, Rule.FIRST_ARTIST_IS, "Mabel Matiz",           "", 200, false),
                new SearchCase("G2_004", "yıldız",        "performer", 5, Rule.FIRST_ARTIST_IS, "Yıldız Tilbe",          "", 200, false),
                new SearchCase("G2_005", "azer",          "performer", 5, Rule.FIRST_ARTIST_IS, "Azer Bülbül",           "", 200, false),
                new SearchCase("G2_006", "serdar",        "performer", 5, Rule.FIRST_ARTIST_IS, "Serdar Ortaç",          "", 200, false),
                new SearchCase("G2_007", "cengiz",        "performer", 5, Rule.FIRST_ARTIST_IS, "Cengiz Kurtoğlu",       "", 200, false),
                new SearchCase("G2_008", "neşet",         "performer", 5, Rule.FIRST_ARTIST_IS, "Neşet Ertaş",           "", 200, false),
                new SearchCase("G2_009", "melike",        "performer", 5, Rule.FIRST_ARTIST_IS, "Melike Şahin",          "", 200, false),
                new SearchCase("G2_010", "orhan",         "performer", 5, Rule.FIRST_ARTIST_IS, "Orhan Gencebay",        "", 200, false),
                new SearchCase("G2_011", "emircan",       "performer", 5, Rule.FIRST_ARTIST_IS, "Emir Can İğrek",        "", 200, false),
                new SearchCase("G2_012", "soner",         "performer", 5, Rule.FIRST_ARTIST_IS, "Soner Sarıkabadayı",    "", 200, false),
                new SearchCase("G2_013", "norm",          "performer", 5, Rule.FIRST_ARTIST_IS, "Norm Ender",            "", 200, false),
                new SearchCase("G2_014", "sibel",         "performer", 5, Rule.FIRST_ARTIST_IS, "Sibel Can",             "", 200, false),
                new SearchCase("G2_015", "irem",          "performer", 5, Rule.FIRST_ARTIST_IS, "İrem Derici",           "", 200, false),
                new SearchCase("G2_016", "musa",          "performer", 5, Rule.FIRST_ARTIST_IS, "Musa Eroğlu",           "", 200, false),
                new SearchCase("G2_017", "kurtuluş",      "performer", 5, Rule.FIRST_ARTIST_IS, "Kurtuluş Kuş",          "", 200, false),
                new SearchCase("G2_018", "mahsun",        "performer", 5, Rule.FIRST_ARTIST_IS, "Mahsun Kırmızıgül",     "", 200, false),
                new SearchCase("G2_019", "funda",         "performer", 5, Rule.FIRST_ARTIST_IS, "Funda Arar",            "", 200, false),
                new SearchCase("G2_020", "sura",          "performer", 5, Rule.FIRST_ARTIST_IS, "Sura İskenderli",       "", 200, false),
                new SearchCase("G2_021", "rafet",         "performer", 5, Rule.FIRST_ARTIST_IS, "Rafet El Roman",        "", 200, false),
                new SearchCase("G2_022", "haluk",         "performer", 5, Rule.FIRST_ARTIST_IS, "Haluk Levent",          "", 200, false),
                new SearchCase("G2_023", "lvbel",         "performer", 5, Rule.FIRST_ARTIST_IS, "Lvbel C5",              "", 200, false),
                new SearchCase("G2_024", "zerrin",        "performer", 5, Rule.FIRST_ARTIST_IS, "Zerrin Özer",           "", 200, false),
                new SearchCase("G2_025", "selda",         "performer", 5, Rule.FIRST_ARTIST_IS, "Selda Bağcan",          "", 200, false),
                new SearchCase("G2_026", "bilal",         "performer", 5, Rule.FIRST_ARTIST_IS, "Bilal Sonses",          "", 200, false),
                new SearchCase("G2_027", "gülden",        "performer", 5, Rule.FIRST_ARTIST_IS, "Gülden Karaböcek",      "", 200, false),
                new SearchCase("G2_028", "engin",         "performer", 5, Rule.FIRST_ARTIST_IS, "Engin Nurşani",         "", 200, false),
                new SearchCase("G2_029", "şebnem",        "performer", 5, Rule.FIRST_ARTIST_IS, "Şebnem Ferah",          "", 200, false),
                new SearchCase("G2_030", "ayaz",          "performer", 5, Rule.FIRST_ARTIST_IS, "Ayaz Erdoğan",          "", 200, false),
                new SearchCase("G2_031", "ajda",          "performer", 5, Rule.FIRST_ARTIST_IS, "Ajda Pekkan",           "", 200, false),
                new SearchCase("G2_032", "izel",          "performer", 5, Rule.FIRST_ARTIST_IS, "İzel",                  "", 200, false),
                new SearchCase("G2_033", "aynur",         "performer", 5, Rule.FIRST_ARTIST_IS, "Aynur Aydın",           "", 200, false),
                new SearchCase("G2_034", "hayko",         "performer", 5, Rule.FIRST_ARTIST_IS, "Hayko Cepkin",          "", 200, false),
                new SearchCase("G2_035", "koray",         "performer", 5, Rule.FIRST_ARTIST_IS, "Koray Avcı",            "", 200, false),
                new SearchCase("G2_036", "ümit",          "performer", 5, Rule.FIRST_ARTIST_IS, "Ümit Besen",            "", 200, false),
                new SearchCase("G2_037", "elif buse",     "performer", 5, Rule.FIRST_ARTIST_IS, "Elif Buse Doğan",       "", 200, false),
                new SearchCase("G2_038", "özcan",         "performer", 5, Rule.FIRST_ARTIST_IS, "Özcan Deniz",           "", 200, false),
                new SearchCase("G2_039", "taylor",        "performer", 5, Rule.FIRST_ARTIST_IS, "Taylor Swift",          "", 200, false),

                // =========================================================================
                // G3 — SANATÇI: KISALTMA, ALIAS VE RUMUZ ARAMASI (15 case)
                // Sanatçının yaygın lakabı, akronimi, birleşik/ayrık yazım varyantı
                // veya sayısal alias'ı ile arama. (ör: sago → Sagopa Kajmer, dktt → Dolu Kadehi Ters Tut)
                // Alias veri tabanı ve alternatif isim eşleştirme kapasitesini test eder.
                // =========================================================================
                new SearchCase("G3_001", "84",             "performer", 5, Rule.FIRST_ARTIST_IS, "seksendört",            "", 200, false),
                new SearchCase("G3_002", "can ozan",       "performer", 5, Rule.FIRST_ARTIST_IS, "Canozan",               "", 200, false),
                new SearchCase("G3_003", "blok",           "performer", 5, Rule.FIRST_ARTIST_IS, "BLOK3",                 "", 200, false),
                new SearchCase("G3_004", "sago",           "performer", 5, Rule.FIRST_ARTIST_IS, "Sagopa Kajmer",         "", 200, false),
                new SearchCase("G3_005", "ati",            "performer", 5, Rule.FIRST_ARTIST_IS, "Ati242",                "", 200, false),
                new SearchCase("G3_006", "cash",           "performer", 5, Rule.FIRST_ARTIST_IS, "Cash Flow",             "", 200, false),
                new SearchCase("G3_007", "reyn",           "performer", 5, Rule.FIRST_ARTIST_IS, "Reynmen",               "", 200, false),
                new SearchCase("G3_008", "emircan iğrek",  "performer", 5, Rule.FIRST_ARTIST_IS, "Emir Can İğrek",        "", 200, false),
                new SearchCase("G3_009", "no1",            "performer", 5, Rule.FIRST_ARTIST_IS, "No.1",                  "", 200, false),
                new SearchCase("G3_010", "halo",           "performer", 5, Rule.FIRST_ARTIST_IS, "Halodayı",              "", 200, false),
                new SearchCase("G3_011", "50",             "performer", 5, Rule.FIRST_ARTIST_IS, "50 Cent",               "", 200, false),
                new SearchCase("G3_012", "no 1",           "performer", 5, Rule.FIRST_ARTIST_IS, "No.1",                  "", 200, false),
                new SearchCase("G3_013", "deha",           "performer", 5, Rule.FIRST_ARTIST_IS, "DEHA INC",              "", 200, false),
                new SearchCase("G3_014", "sibelcan",       "performer", 5, Rule.FIRST_ARTIST_IS, "Sibel Can",             "", 200, false),
                new SearchCase("G3_015", "dktt",           "performer", 5, Rule.FIRST_ARTIST_IS, "Dolu Kadehi Ters Tut",  "", 200, false),

                // =========================================================================
                // G4 — SANATÇI: YAZIM HATALI / FONETİK BENZER ARAMA (10 case)
                // Eksik harf, harf yer değiştirme, yanlış harf veya çoklu typo ile sanatçı arama.
                // (ör: ezel → Ezhel, semicek → Semicenk, mr ve ötei → Mor ve Ötesi)
                // Fuzzy matching ve spelling correction motorunu test eder.
                // =========================================================================
                new SearchCase("G4_001", "ezel",            "performer", 5, Rule.FIRST_ARTIST_IS, "Ezhel",                  "", 200, false),
                new SearchCase("G4_002", "reymen",           "performer", 5, Rule.FIRST_ARTIST_IS, "Reynmen",                "", 200, false),
                new SearchCase("G4_003", "ibrahim tat",      "performer", 5, Rule.FIRST_ARTIST_IS, "İbrahim Tatlıses",       "", 200, false),
                new SearchCase("G4_004", "hejan",            "performer", 5, Rule.FIRST_ARTIST_IS, "Heijan",                 "", 200, false),
                new SearchCase("G4_005", "semicek",          "performer", 5, Rule.FIRST_ARTIST_IS, "Semicenk",               "", 200, false),
                new SearchCase("G4_006", "emre gel",         "performer", 5, Rule.FIRST_ARTIST_IS, "Emre Fel",               "", 200, false),
                new SearchCase("G4_007", "kofn",             "performer", 5, Rule.FIRST_ARTIST_IS, "KÖFN",                   "", 200, false),
                new SearchCase("G4_008", "sertap",           "performer", 5, Rule.FIRST_ARTIST_IS, "Sertab Erener",          "", 200, false),
                new SearchCase("G4_009", "mr ve ötei",       "performer", 5, Rule.FIRST_ARTIST_IS, "Mor ve Ötesi",           "", 200, false),
                new SearchCase("G4_010", "dolu kadhi tut",   "performer", 5, Rule.FIRST_ARTIST_IS, "Dolu Kadehi Ters Tut",   "", 200, false),

                // =========================================================================
                // G5 — KATEGORİ: TÜR VE KÜLTÜREL ARAMA (15 case)
                // Müzik türü, kültürel/bölgesel stil veya tematik anahtar kelimeyle
                // playlist/kategori araması. İçerik etiketleme (tagging) ve sınıflandırma
                // doğruluğunu test eder. Fail genellikle metadata/tagging sorununa işaret eder.
                // =========================================================================
                new SearchCase("G5_001", "pop",            "playlist", 5, Rule.TOPN_RELATED_ALBUM, "pop",          "", 200, false),
                new SearchCase("G5_002", "arabesk",        "playlist", 5, Rule.TOPN_RELATED_ALBUM, "arabesk",      "", 200, false),
                new SearchCase("G5_003", "ilahi",          "playlist", 5, Rule.TOPN_RELATED_ALBUM, "ilahi",        "", 200, false),
                new SearchCase("G5_004", "karadeniz",      "playlist", 5, Rule.TOPN_RELATED_ALBUM, "karadeniz",    "", 200, false),
                new SearchCase("G5_005", "halay",          "playlist", 5, Rule.TOPN_RELATED_ALBUM, "halay",        "", 200, false),
                new SearchCase("G5_006", "roman",          "playlist", 5, Rule.TOPN_RELATED_ALBUM, "roman",        "", 200, false),
                new SearchCase("G5_007", "çocuk",          "playlist", 5, Rule.TOPN_RELATED_ALBUM, "çocuk",        "", 200, false),
                new SearchCase("G5_008", "klasik",         "playlist", 5, Rule.TOPN_RELATED_ALBUM, "klasik",       "", 200, false),
                new SearchCase("G5_009", "ankara",         "playlist", 5, Rule.TOPN_RELATED_ALBUM, "ankara",       "", 200, false),
                new SearchCase("G5_010", "akustik",        "playlist", 5, Rule.TOPN_RELATED_ALBUM, "akustik",      "", 200, false),
                new SearchCase("G5_011", "çocuk şarkıları","playlist", 5, Rule.TOPN_RELATED_ALBUM, "çocuk",        "", 200, false),
                new SearchCase("G5_012", "türkçe",         "playlist", 5, Rule.TOPN_RELATED_ALBUM, "türkçe",       "", 200, false),
                new SearchCase("G5_013", "rock",           "playlist", 5, Rule.TOPN_RELATED_ALBUM, "rock",         "", 200, false),
                new SearchCase("G5_014", "türk sanat",     "playlist", 5, Rule.TOPN_RELATED_ALBUM, "türk sanat",   "", 200, false),
                new SearchCase("G5_015", "ramazan",        "playlist", 5, Rule.TOPN_RELATED_ALBUM, "ramazan",      "", 200, false),

                // =========================================================================
                // G6 — KATEGORİ: DÖNEM ARAMASI VE FORMAT VARYASYONLARI (7 case)
                // Aynı semantiğin (90'lar, 80'ler) farklı yazım biçimleriyle aranması.
                // (ör: 90 / 90lar / 90'lar / 90s / 90 lar)
                // Yazım normalizasyonu ve token standardizasyon kapasitesini test eder.
                // Bu gruptaki kısmi fail'ler normalizasyon motorunun hangi formatı ıskaladığını gösterir.
                // =========================================================================
                new SearchCase("G6_001", "90",        "playlist", 5, Rule.TOPN_RELATED_ALBUM, "90",     "", 200, false),
                new SearchCase("G6_002", "90lar",      "playlist", 5, Rule.TOPN_RELATED_ALBUM, "90",     "", 200, false),
                new SearchCase("G6_003", "90'lar",     "playlist", 5, Rule.TOPN_RELATED_ALBUM, "90",     "", 200, false),
                new SearchCase("G6_004", "90s",        "playlist", 5, Rule.TOPN_RELATED_ALBUM, "90",     "", 200, false),
                new SearchCase("G6_005", "90 lar",     "playlist", 5, Rule.TOPN_RELATED_ALBUM, "90",     "", 200, false),
                new SearchCase("G6_006", "80",         "playlist", 5, Rule.TOPN_RELATED_ALBUM, "80",     "", 200, false),
                new SearchCase("G6_007", "90 lar pop", "playlist", 5, Rule.TOPN_RELATED_ALBUM, "90'lar", "", 200, false),

                // =========================================================================
                // G7 — KATEGORİ: ATMOSFER, AKTİVİTE VE DİL TERCİHİ ARAMASI (5 case)
                // Kullanıcının ruh halini, fiziksel aktivitesini veya dil tercihini
                // yazarak playlist araması. Kullanıcı niyetini (intent) yorumlama
                // kapasitesini test eder. Fail alan case'ler semantic öneri motorunun
                // zayıflığına işaret eder.
                // =========================================================================
                new SearchCase("G7_001", "yabancı",   "playlist", 5, Rule.TOPN_RELATED_ALBUM, "yabancı",   "", 200, false), // yabancı pop hedeflendi
                new SearchCase("G7_002", "oyun hava", "playlist", 5, Rule.TOPN_RELATED_ALBUM, "oyun hava", "", 200, false), // oyun havaları → ankara oyun havası
                new SearchCase("G7_003", "spor",      "playlist", 5, Rule.TOPN_RELATED_ALBUM, "spor",      "", 200, false),
                new SearchCase("G7_004", "dans",      "playlist", 5, Rule.TOPN_RELATED_ALBUM, "dans",      "", 200, false),
                new SearchCase("G7_005", "meditasyon","playlist", 5, Rule.TOPN_RELATED_ALBUM, "meditasyon","", 200, false),

                // =========================================================================
                // G8 — ŞARKI: DOĞRUDAN / KISMİ ŞARKI ADI İLE ARAMA (11 case)
                // Tam veya kısmi şarkı adını yazarak track araması.
                // (ör: "bodrum" → Bodrum, "ağlama ben" → Ağlama Ben Ağlarım)
                // Track retrieval ve şarkı adı prefix/kısmi eşleşme doğruluğunu test eder.
                // =========================================================================
                new SearchCase("G8_001", "nasır",               "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Melike Şahin",          "Nasır",                   200, false),
                new SearchCase("G8_002", "kaybolurum gülüşünde","songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "İkilem",                "Kaybolurum Gülüşünde",    200, false),
                new SearchCase("G8_003", "bodrum",              "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Yüzyüzeyken Konuşuruz", "Bodrum",                  200, false),
                new SearchCase("G8_004", "ağlama ben ağlarım",  "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Canozan",               "Ağlama ben ağlarım",      200, false),
                new SearchCase("G8_005", "bana sor",            "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Ferdi Tayfur",          "Bana Sor",                200, false),
                new SearchCase("G8_006", "ağlama ben",          "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Canozan",               "Ağlama ben ağlarım",      200, false),
                new SearchCase("G8_007", "erik",                "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",                      "Erik Dalı",               200, false),
                new SearchCase("G8_008", "karakedi",            "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Melis Fis",             "Kara Kedi",               200, false),
                new SearchCase("G8_009", "gözlerime bak",       "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Mert Demir",            "Gözlerime Bak",           200, false),
                new SearchCase("G8_010", "doğuştan",            "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Lvbel C5",              "Doğuştan Beri Haklıyım", 200, false),
                new SearchCase("G8_011", "kalbimin sahibine",   "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "İrem Derici",           "Kalbimin Tek Sahibine",   200, false),

                // =========================================================================
                // G9 — ŞARKI: ŞARKI SÖZÜ PARÇASI İLE ARAMA (15 case)
                // Kullanıcının şarkı adını bilmeyip sadece aklında kalan bir söz dizini
                // yazarak şarkıyı bulmaya çalışması. Şarkı adından farklı sözler içerir.
                // Lyrics indexleme ve semantik eşleşme kapasitesini test eder.
                // =========================================================================
                new SearchCase("G9_001", "yandım ah",                    "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Sakiler",       "Yalanı Bırak",         200, false),
                new SearchCase("G9_002", "sus",                          "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Ceza",          "Suspus",               200, false),
                new SearchCase("G9_003", "pus",                          "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Ceza",          "Suspus",               200, false),
                new SearchCase("G9_004", "bak ben yara gibiyim",         "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Emir Can İğrek","Nalan",                200, false),
                new SearchCase("G9_005", "çölüme yağmur oldun",          "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Müslüm Gürses", "Affet",                200, false),
                new SearchCase("G9_006", "zaten aşklar hep yalan dolan", "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Yıldız Tilbe",  "Sana Değer",           200, false),
                new SearchCase("G9_007", "sana hastayım anlasana",       "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Derya Uluğ",    "Yansıma",              200, false),
                new SearchCase("G9_008", "arabam",                       "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Sefo",          "Araba",                200, false),
                new SearchCase("G9_009", "hadi ya",                      "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Melis Kar",     "Yatıya",               200, false),
                new SearchCase("G9_010", "babalar",                      "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Blok3",         "PATLAT",               200, false),
                new SearchCase("G9_011", "sarışınlar",                   "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Derya Uluğ",    "Esmerin Adı Oya",      200, false),
                new SearchCase("G9_012", "silemez o beni",               "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Yıldız Tilbe",  "Dizine Dursun",        200, false),
                new SearchCase("G9_013", "babalar sözünü tutar",         "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Blok3",         "PATLAT",               200, false),
                new SearchCase("G9_014", "affet bu gece istedim ölmek",  "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Model",         "Pembe Mezarlık",       200, false),
                new SearchCase("G9_015", "güldün ne güzel",              "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Pinhani",       "Ne Güzel Güldün",      200, false),

                // =========================================================================
                // G10 — ŞARKI: YAZIM HATALI ŞARKI ADI / SÖZ ARAMASI (3 case)
                // Eksik harf veya yanlış yazılmış şarkı adı ya da söz dizini ile arama.
                // (ör: "çok geç şmdi" → şimdi, "lacivert eceler" → geceler)
                // Şarkı ve lyrics indexinde fuzzy matching toleransını test eder.
                // =========================================================================
                new SearchCase("G10_001", "illede sen",      "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Azer Bülbül",  "İlle De Sen",      200, false),
                new SearchCase("G10_002", "çok geç şmdi",    "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Edis",         "Yalan",            200, false),
                new SearchCase("G10_003", "lacivert eceler", "songs", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Ferhat Göçer", "Lacivert Geceler", 200, false),

                // =========================================================================
                // G11 — GÜVENLİK VE SINIR KOŞULLARI (3 case)
                // Boş/boşluk girişi, maksimum uzunluk aşımı ve injection payload aramaları.
                // Beklenen davranış: exception fırlatmadan boş/güvenli yanıt dönmek.
                // Bu grupta "fail" kriteri farklıdır; HTTP 200 + boş sonuç beklenir.
                // =========================================================================
                new SearchCase("G11_001", "   ",                             "general", 10, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("G11_002", "a".repeat(150),                   "general", 10, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("G11_003", "<script>alert('test')</script>",  "general",  5, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true)
        );
    }


    @ParameterizedTest(name = "[{0}] term={1} type={2}")
    @MethodSource("cases")
    void run_case(SearchCase sc) {

        String indexName = getIndexName(sc.excelType());
        Response res = api.search(sc.term(), indexName, sc.limit());
        int status = res.statusCode();

        System.out.printf("[RUN ] %s | Arama='%s' | Tip='%s' -> İndeks='%s' | Kural=%s%n",
                sc.caseId(), sc.term(), sc.excelType(), indexName, prettyRule(sc.rule()));

        // 1. HTTP STATUS KONTROLÜ
        if (sc.expectedStatus() != -1 && status != sc.expectedStatus()) {
            addReportRow(sc, indexName, "API HTTP " + sc.expectedStatus() + " dönmelidir.", "NOK", "SİSTEM HATASI: API HTTP " + status + " döndürdü!");
            assertEquals(sc.expectedStatus(), status, "HTTP status bekleneni karşılamadı. API " + status + " döndürdü.");
        } else if (sc.expectedStatus() == -1 && (status != 200 && status != 400)) {
            addReportRow(sc, indexName, "API HTTP 200 veya 400 dönmelidir.", "NOK", "SİSTEM HATASI: API HTTP " + status + " döndürdü!");
            assertTrue(status == 200 || status == 400, "Discovery case: 200 veya 400 kabul. Gerçek=" + status);
        }

        if (status == 400 && sc.rule() == Rule.DISCOVER_LIMIT_ZERO_STATUS) {
            addReportRow(sc, indexName,
                    "API'nin limit=0 veya geçersiz parametre durumunda 400 hatası veya 200 dönmesi beklenir (Discovery).",
                    "OK",
                    "API 400 Bad Request döndürdü. Bu bir discovery senaryosu olduğu için kabul edildi.");
            return;
        }

        // 2. JSON KONTROLÜ (GÜNCELLENDİ: resultsList artık 'content' dizisine bakıyor)
        JsonPath jp = res.jsonPath();
        List<Object> list = resultsList(jp);

        assertNotNull(list, "Results listesi null olmamalı! Gateway formatı bozuk olabilir.");

        if (!sc.allowEmptyResults() && list.isEmpty()) {
            addReportRow(sc, indexName, "Arama sonucunda en az 1 kayıt dönmesi beklenir.", "NOK", "Liste boş döndü! Beklenen veri bulunamadı.");
            fail("Sonuç listesi boş döndü!");
        }

        // 3. KURAL YÖNETİMİ
        switch (sc.rule()) {
            case FIRST_ARTIST_IS -> verifyFirstArtistIs(sc, jp, list, indexName);
            case TOPN_HAS_ARTIST -> verifyTopNHasArtist(sc, jp, indexName);
            case TOPN_HAS_ARTIST_AND_TRACK -> verifyTopNHasArtistAndTrack(sc, jp, indexName);
            case TOPN_RELATED_ALBUM -> verifyTopNRelatedAlbum(sc, jp, indexName);
            case CONTRACT_HAS_FIELDS -> verifyContract(sc, jp, list, indexName);
            case RESULTCOUNT_MATCHES_SIZE -> verifyResultCountMatchesSize(sc, jp, list, indexName);
            case LIMIT_RESPECTED -> verifyLimitRespected(sc, list, indexName);
            case ALLOW_EMPTY_RESULTS -> verifyAllowEmpty(sc, list, indexName);
            case DISCOVER_LIMIT_ZERO_STATUS -> verifyDiscoverLimitZero(sc, list, indexName);
            case ENTITY_FIELD_PRESENT -> verifyEntityFieldPresent(sc, jp, list, indexName);
            case LYRICS_DISCOVERY_IF_RESULTS_THEN_MATCH -> verifyLyricsDiscovery(sc, jp, list, indexName);
            case RESPONSE_TIME_UNDER_THRESHOLD -> verifyResponseTime(sc, res, indexName);
            default -> System.out.println("Kural yok: " + sc.rule());
        }
    }

    // -----------------------------------------------------------
    // DOĞRULAMA METOTLARI (GATEWAY FORMATINA GÖRE GÜNCELLENDİ)
    // -----------------------------------------------------------

    private void verifyFirstArtistIs(SearchCase sc, JsonPath jp, List<Object> list, String indexName) {
        if (list.isEmpty()) fail("Liste boş.");
        String firstArtist = safeStr(jp.getString("content[0].data.performerName"));
        String expectedText = "Arama sonucunda dönen listenin 1. sırasındaki sanatçı adı '" + sc.expArtistOrKeyword() + "' olmalıdır.";

        if (!containsTRInsensitive(firstArtist, sc.expArtistOrKeyword())) {
            String note = findArtistPositionNote(sc, indexName);
            String resultText = "1. sıradaki kayıt farklı: '" + firstArtist + "'. " + note;
            addReportRow(sc, indexName, expectedText, "NOK", resultText);
            fail(resultText);
        } else {
            addReportRow(sc, indexName, expectedText, "OK", "Başarılı. İlk sırada '" + firstArtist + "' sanatçısı geldi.");
        }
    }

    private void verifyTopNHasArtist(SearchCase sc, JsonPath jp, String indexName) {
        String expectedText = "Arama sonuçlarının ilk " + sc.limit() + " kaydı içerisinde '" + sc.expArtistOrKeyword() + "' sanatçısı yer almalıdır.";

        int idx = findArtistIndex(jp, sc.limit(), sc.expArtistOrKeyword());
        if (idx == -1) {
            String note = findArtistPositionNote(sc, indexName);
            addReportRow(sc, indexName, expectedText, "NOK", "İlk " + sc.limit() + " sonuçta bulunamadı. " + note);
            fail("Top" + sc.limit() + " içinde artist bulunamadı.");
        } else {
            addReportRow(sc, indexName, expectedText, "OK", "Başarılı. Sanatçı " + (idx + 1) + ". sırada bulundu.");
        }
    }

    private void verifyTopNHasArtistAndTrack(SearchCase sc, JsonPath jp, String indexName) {
        String expectedText = "İlk " + sc.limit() + " sonuç içinde Sanatçı: '" + sc.expArtistOrKeyword() + "' ve Şarkı: '" + sc.expTrack() + "' eşleşmesi bulunmalıdır.";

        int idx = findArtistAndTrackIndex(jp, sc.limit(), sc.expArtistOrKeyword(), sc.expTrack());
        if (idx == -1) {
            String note = findArtistTrackPositionNote(sc, indexName);
            addReportRow(sc, indexName, expectedText, "NOK", "Eşleşme bulunamadı. " + note);
            fail("Track bulunamadı.");
        } else {
            addReportRow(sc, indexName, expectedText, "OK", "Başarılı. Şarkı " + (idx + 1) + ". sırada bulundu.");
        }
    }

    private void verifyTopNRelatedAlbum(SearchCase sc, JsonPath jp, String indexName) {
        String expectedText = "İlk " + sc.limit() + " albüm sonucunda '" + sc.expArtistOrKeyword() + "' ifadesi geçmelidir.";

        int idx = findAlbumKeywordIndex(jp, sc.limit(), sc.expArtistOrKeyword());
        if (idx == -1) {
            addReportRow(sc, indexName, expectedText, "NOK", "İlgili keyword albüm isimlerinde bulunamadı.");
            fail("Album keyword yok.");
        } else {
            addReportRow(sc, indexName, expectedText, "OK", "Başarılı. Albüm " + (idx + 1) + ". sırada bulundu.");
        }
    }

    private void verifyContract(SearchCase sc, JsonPath jp, List<Object> list, String indexName) {
        String expectedText = "API cevabı JSON formatında 'content' dizisini içermelidir.";
        if (list == null) {
            addReportRow(sc, indexName, expectedText, "NOK", "Hata: 'content' alanı cevapta bulunamadı!");
            fail("Contract failure");
        }
        addReportRow(sc, indexName, expectedText, "OK", "Başarılı. Zorunlu alanlar mevcut.");
    }

    private void verifyResultCountMatchesSize(SearchCase sc, JsonPath jp, List<Object> list, String indexName) {
        String expectedText = "Arama sonucunda tutarlı bir liste dönmelidir.";
        if (list == null) {
            addReportRow(sc, indexName, expectedText, "NOK", "Hata: Liste bulunamadı.");
            fail("Count missing");
        } else {
            addReportRow(sc, indexName, expectedText, "OK", "Başarılı. Dönen Veri: " + list.size());
        }
    }

    private void verifyLimitRespected(SearchCase sc, List<Object> list, String indexName) {
        String expectedText = "API tarafından dönen sonuç sayısı, talep edilen limit (" + sc.limit() + ") değerini aşmamalıdır.";

        if (list.size() > sc.limit()) {
            addReportRow(sc, indexName, expectedText, "NOK", "Hata: Limit aşıldı! Dönen kayıt sayısı: " + list.size());
            fail("Limit exceeded");
        } else {
            addReportRow(sc, indexName, expectedText, "OK", "Başarılı. Dönen kayıt sayısı: " + list.size());
        }
    }

    private void verifyAllowEmpty(SearchCase sc, List<Object> list, String indexName) {
        String expectedText = "Sistem bu input için 200 OK dönmeli ve çökmemelidir. Sonuç listesi boş olabilir.";
        addReportRow(sc, indexName, expectedText, "OK", "Başarılı. Sistem stabil yanıt verdi. Sonuç sayısı: " + list.size());
    }

    private void verifyDiscoverLimitZero(SearchCase sc, List<Object> list, String indexName) {
        String expectedText = "Limit=0 gönderildiğinde sistem 200 dönüyorsa boş liste, 400 dönüyorsa hata mesajı vermelidir.";
        addReportRow(sc, indexName, expectedText, "OK", "Keşif Sonucu: API 200 döndürdü ve " + list.size() + " adet sonuç verdi.");
    }

    private void verifyEntityFieldPresent(SearchCase sc, JsonPath jp, List<Object> list, String indexName) {
        String field = sc.expArtistOrKeyword();
        String expectedText = "Dönen sonuçların en az birinde 'data." + field + "' alanının dolu olduğu doğrulanmalıdır.";

        boolean found = false;
        int n = Math.min(sc.limit(), list.size());
        for (int i = 0; i < n; i++) {
            if (!safeStr(jp.getString("content[" + i + "].data." + field)).isBlank()) {
                found = true;
                break;
            }
        }

        if (!found) {
            addReportRow(sc, indexName, expectedText, "NOK", "Hata: '" + field + "' alanı tüm kayıtlarda boş veya eksik.");
            fail("Field missing");
        } else {
            addReportRow(sc, indexName, expectedText, "OK", "Başarılı. '" + field + "' alanı verilerde mevcut.");
        }
    }

    private void verifyLyricsDiscovery(SearchCase sc, JsonPath jp, List<Object> list, String indexName) {
        String expectedText = "Eğer sonuç dönerse, ilk " + sc.limit() + " kayıt içinde Sanatçı:'" + sc.expArtistOrKeyword() + "' ve Şarkı:'" + sc.expTrack() + "' bulunmalıdır.";

        if (list.isEmpty()) {
            addReportRow(sc, indexName, expectedText, "OK", "Bilgi: Şarkı sözü araması sonuç vermedi (Empty List). Test başarılı sayıldı.");
            return;
        }
        int idx = findArtistAndTrackIndex(jp, sc.limit(), sc.expArtistOrKeyword(), sc.expTrack());
        if (idx == -1) {
            String note = findArtistTrackPositionNote(sc, indexName);
            addReportRow(sc, indexName, expectedText, "NOK", "Sonuçlar geldi fakat beklenen şarkı bulunamadı. " + note);
            fail("Lyrics fail");
        } else {
            addReportRow(sc, indexName, expectedText, "OK", "Başarılı. Şarkı sözü eşleşmesi " + (idx + 1) + ". sırada bulundu.");
        }
    }

    private void verifyResponseTime(SearchCase sc, Response res, String indexName) {
        long threshold = 2000;
        String expectedText = "API cevap süresi " + threshold + " ms değerinin altında olmalıdır.";
        long time = res.time();

        if (time > threshold) {
            addReportRow(sc, indexName, expectedText, "NOK", "Performans Uyarısı: Cevap süresi " + time + " ms (Eşik aşıldı).");
        } else {
            addReportRow(sc, indexName, expectedText, "OK", "Başarılı. Cevap süresi: " + time + " ms.");
        }
    }

    @AfterAll
    static void writeExcelReportIfEnabled() {
        if (EXCEL_REPORT_ENABLED) ExcelTestReportWriter.write(REPORT_ROWS);
    }

    private void addReportRow(SearchCase sc, String indexName, String expected, String status, String result) {
        if (!EXCEL_REPORT_ENABLED) return;
        String testName = "\"" + sc.term() + "\" araması yapılır";
        String description = "Arama terimi: '" + sc.term() + "' kullanılarak istek atılır.";

        REPORT_ROWS.add(new TestResultRow(
                sc.caseId(),   // <-- YENİ: sheet routing için grup ID'si
                testName, description, expected, sc.excelType(), indexName, status, result
        ));
    }

    private String findArtistPositionNote(SearchCase sc, String indexName) {
        Response dbg = api.search(sc.term(), indexName, DEBUG_LOOKUP_LIMIT);
        JsonPath jp = dbg.jsonPath();
        int idx = findArtistIndex(jp, DEBUG_LOOKUP_LIMIT, sc.expArtistOrKeyword());
        return idx == -1 ? "Geniş aramada da (" + DEBUG_LOOKUP_LIMIT + " kayıt) bulunamadı." : "Daha geniş aramada " + (idx + 1) + ". sırada bulundu.";
    }

    private String findArtistTrackPositionNote(SearchCase sc, String indexName) {
        Response dbg = api.search(sc.term(), indexName, DEBUG_LOOKUP_LIMIT);
        JsonPath jp = dbg.jsonPath();
        int idx = findArtistAndTrackIndex(jp, DEBUG_LOOKUP_LIMIT, sc.expArtistOrKeyword(), sc.expTrack());
        return idx == -1 ? "Geniş aramada da (" + DEBUG_LOOKUP_LIMIT + " kayıt) bulunamadı." : "Daha geniş aramada " + (idx + 1) + ". sırada bulundu.";
    }
}