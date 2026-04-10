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
import util.MuudSearchUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static util.MuudSearchUtils.*;

/**
 * Kapsamlı arama test sınıfı — 34 gereksinimi karşılayan tüm caseler bir arada.
 *
 * Yapı:
 *   G1  — Sanatçı: tam/tek kelime doğrudan arama               (29 case)
 *   G2  — Sanatçı: kısmi ad / ilk isim ile arama               (39 case)
 *   G3  — Sanatçı: kısaltma, alias ve rumuz araması             (15 case)
 *   G4  — Sanatçı: yazım hatalı / fonetik benzer arama         (10 case)
 *   G5  — Kategori: tür ve kültürel arama                      (15 case)
 *   G6  — Kategori: dönem araması ve format varyasyonları       ( 7 case)
 *   G7  — Kategori: atmosfer, aktivite ve dil tercihi           ( 5 case)
 *   G8  — Şarkı: doğrudan / kısmi şarkı adı ile arama          (11 case)
 *   G9  — Şarkı: şarkı sözü parçası ile arama                  (15 case)
 *   G10 — Şarkı: yazım hatalı şarkı adı / söz araması          ( 3 case)
 *   G11 — Güvenlik ve sınır koşulları                          ( 3 case)
 *   ─── YENİ GRUPLAR ───────────────────────────────────────────────────────
 *   G12 — Albüm adı ile doğrudan arama       (#3)              ( 6 case)
 *   G13 — Sanatçı + şarkı kombine arama      (#5)              ( 8 case)
 *   G14 — Büyük/küçük harf duyarsızlığı      (#6)              ( 6 case)
 *   G15 — Türkçe klavye ASCII alternatif      (#7, #8)         ( 6 case)
 *   G16 — Kısa sorgu (1-2 karakter)          (#9, #10)         ( 8 case)
 *   G17 — Yanıt süresi performans testi      (#20)             ( 5 case)
 *   G18 — Fazla/tekrarlayan karakter toleransı (#26)           ( 4 case)
 *   G19 — Emoji ve özel karakter araması     (#27)             ( 4 case)
 *   G20 — Anlamsız/rastgele sorgu            (#28)             ( 4 case)
 *
 * Toplam: 205 test case
 *
 * Kıyas mantığı (AktifIndexTest ile aynı):
 *   G1-G4, G14, G15, G18 → general (active-indices) vs performer (indeks 10)
 *   G5-G7, G12            → general (active-indices) vs playlist/album
 *   G8-G10, G13           → general (active-indices) vs songs (indeks 48)
 *   G11, G16-G20          → yalnızca general (kıyas yok)
 */
public class MuudSearchApiKapsayiciTest extends TestConfig {

    private static final int DEBUG_LOOKUP_LIMIT = 50;
    private static final boolean EXCEL_REPORT_ENABLED =
            Boolean.parseBoolean(System.getProperty("excelReport", "false"));
    private static final List<TestResultRow> REPORT_ROWS = new ArrayList<>();
    private static MuudSearchApi api;

    @BeforeAll
    static void setupClient() {
        api = new MuudSearchApi();
    }

    // =========================================================================
    // TEST CASE'LERİ
    // =========================================================================
    static Stream<SearchCase> cases() {
        return Stream.of(

                // =========================================================================
                // G1 — SANATÇI: TAM / TEK KELİME DOĞRUDAN ARAMA (29 case)
                // =========================================================================
                new SearchCase("G1_001", "u2",          "general", 5, Rule.FIRST_ARTIST_IS, "u2",          "", 200, false),
                new SearchCase("G1_002", "goksel",       "general", 5, Rule.FIRST_ARTIST_IS, "Göksel",      "", 200, false),
                new SearchCase("G1_003", "edis",         "general", 5, Rule.FIRST_ARTIST_IS, "Edis",        "", 200, false),
                new SearchCase("G1_004", "uzi",          "general", 5, Rule.FIRST_ARTIST_IS, "Uzi",         "", 200, false),
                new SearchCase("G1_005", "mfö",          "general", 5, Rule.FIRST_ARTIST_IS, "MFÖ",         "", 200, false),
                new SearchCase("G1_006", "sıla",         "general", 5, Rule.FIRST_ARTIST_IS, "Sıla",        "", 200, false),
                new SearchCase("G1_007", "çakal",        "general", 5, Rule.FIRST_ARTIST_IS, "cakal",       "", 200, false),
                new SearchCase("G1_008", "eminem",       "general", 5, Rule.FIRST_ARTIST_IS, "Eminem",      "", 200, false),
                new SearchCase("G1_009", "güneş",        "general", 5, Rule.FIRST_ARTIST_IS, "Güneş",       "", 200, false),
                new SearchCase("G1_010", "dua lipa",     "general", 5, Rule.FIRST_ARTIST_IS, "Dua Lipa",    "", 200, false),
                new SearchCase("G1_011", "mero",         "general", 5, Rule.FIRST_ARTIST_IS, "Mero",        "", 200, false),
                new SearchCase("G1_012", "murda",        "general", 5, Rule.FIRST_ARTIST_IS, "Murda",       "", 200, false),
                new SearchCase("G1_013", "inna",         "general", 5, Rule.FIRST_ARTIST_IS, "Inna",        "", 200, false),
                new SearchCase("G1_014", "adele",        "general", 5, Rule.FIRST_ARTIST_IS, "Adele",       "", 200, false),
                new SearchCase("G1_015", "mor ve ötesi", "general", 5, Rule.FIRST_ARTIST_IS, "mor ve ötesi","", 200, false),
                new SearchCase("G1_016", "patron",       "general", 5, Rule.FIRST_ARTIST_IS, "Patron",      "", 200, false),
                new SearchCase("G1_017", "tefo",         "general", 5, Rule.FIRST_ARTIST_IS, "Tefo",        "", 200, false),
                new SearchCase("G1_018", "doğuş",        "general", 5, Rule.FIRST_ARTIST_IS, "Doğuş",       "", 200, false),
                new SearchCase("G1_019", "ben fero",     "general", 5, Rule.FIRST_ARTIST_IS, "Ben Fero",    "", 200, false),
                new SearchCase("G1_020", "inji",         "general", 5, Rule.FIRST_ARTIST_IS, "INJI",        "", 200, false),
                new SearchCase("G1_021", "rihanna",      "general", 5, Rule.FIRST_ARTIST_IS, "Rihanna",     "", 200, false),
                new SearchCase("G1_022", "mavi",         "general", 5, Rule.FIRST_ARTIST_IS, "Mavi",        "", 200, false),
                new SearchCase("G1_023", "velet",        "general", 5, Rule.FIRST_ARTIST_IS, "Velet",       "", 200, false),
                new SearchCase("G1_024", "adamlar",      "general", 5, Rule.FIRST_ARTIST_IS, "Adamlar",     "", 200, false),
                new SearchCase("G1_025", "blackpink",    "general", 5, Rule.FIRST_ARTIST_IS, "BLACKPINK",   "", 200, false),
                new SearchCase("G1_026", "sia",          "general", 5, Rule.FIRST_ARTIST_IS, "Sia",         "", 200, false),
                new SearchCase("G1_027", "shakira",      "general", 5, Rule.FIRST_ARTIST_IS, "Shakira",     "", 200, false),
                new SearchCase("G1_028", "beyonce",      "general", 5, Rule.FIRST_ARTIST_IS, "Beyoncé",     "", 200, false),
                new SearchCase("G1_029", "madonna",      "general", 5, Rule.FIRST_ARTIST_IS, "Madonna",     "", 200, false),

                // =========================================================================
                // G2 — SANATÇI: KISMİ AD / İLK İSİM İLE ARAMA (39 case)
                // =========================================================================
                new SearchCase("G2_001", "aleyna",        "general", 5, Rule.FIRST_ARTIST_IS, "Aleyna Tilki",          "", 200, false),
                new SearchCase("G2_002", "ferdi",         "general", 5, Rule.FIRST_ARTIST_IS, "Ferdi Tayfur",          "", 200, false),
                new SearchCase("G2_003", "mabel",         "general", 5, Rule.FIRST_ARTIST_IS, "Mabel Matiz",           "", 200, false),
                new SearchCase("G2_004", "yıldız",        "general", 5, Rule.FIRST_ARTIST_IS, "Yıldız Tilbe",          "", 200, false),
                new SearchCase("G2_005", "azer",          "general", 5, Rule.FIRST_ARTIST_IS, "Azer Bülbül",           "", 200, false),
                new SearchCase("G2_006", "serdar",        "general", 5, Rule.FIRST_ARTIST_IS, "Serdar Ortaç",          "", 200, false),
                new SearchCase("G2_007", "cengiz",        "general", 5, Rule.FIRST_ARTIST_IS, "Cengiz Kurtoğlu",       "", 200, false),
                new SearchCase("G2_008", "neşet",         "general", 5, Rule.FIRST_ARTIST_IS, "Neşet Ertaş",           "", 200, false),
                new SearchCase("G2_009", "melike",        "general", 5, Rule.FIRST_ARTIST_IS, "Melike Şahin",          "", 200, false),
                new SearchCase("G2_010", "orhan",         "general", 5, Rule.FIRST_ARTIST_IS, "Orhan Gencebay",        "", 200, false),
                new SearchCase("G2_011", "emircan",       "general", 5, Rule.FIRST_ARTIST_IS, "Emir Can İğrek",        "", 200, false),
                new SearchCase("G2_012", "soner",         "general", 5, Rule.FIRST_ARTIST_IS, "Soner Sarıkabadayı",    "", 200, false),
                new SearchCase("G2_013", "norm",          "general", 5, Rule.FIRST_ARTIST_IS, "Norm Ender",            "", 200, false),
                new SearchCase("G2_014", "sibel",         "general", 5, Rule.FIRST_ARTIST_IS, "Sibel Can",             "", 200, false),
                new SearchCase("G2_015", "irem",          "general", 5, Rule.FIRST_ARTIST_IS, "İrem Derici",           "", 200, false),
                new SearchCase("G2_016", "musa",          "general", 5, Rule.FIRST_ARTIST_IS, "Musa Eroğlu",           "", 200, false),
                new SearchCase("G2_017", "kurtuluş",      "general", 5, Rule.FIRST_ARTIST_IS, "Kurtuluş Kuş",          "", 200, false),
                new SearchCase("G2_018", "mahsun",        "general", 5, Rule.FIRST_ARTIST_IS, "Mahsun Kırmızıgül",     "", 200, false),
                new SearchCase("G2_019", "funda",         "general", 5, Rule.FIRST_ARTIST_IS, "Funda Arar",            "", 200, false),
                new SearchCase("G2_020", "sura",          "general", 5, Rule.FIRST_ARTIST_IS, "Sura İskenderli",       "", 200, false),
                new SearchCase("G2_021", "rafet",         "general", 5, Rule.FIRST_ARTIST_IS, "Rafet El Roman",        "", 200, false),
                new SearchCase("G2_022", "haluk",         "general", 5, Rule.FIRST_ARTIST_IS, "Haluk Levent",          "", 200, false),
                new SearchCase("G2_023", "lvbel",         "general", 5, Rule.FIRST_ARTIST_IS, "Lvbel C5",              "", 200, false),
                new SearchCase("G2_024", "zerrin",        "general", 5, Rule.FIRST_ARTIST_IS, "Zerrin Özer",           "", 200, false),
                new SearchCase("G2_025", "selda",         "general", 5, Rule.FIRST_ARTIST_IS, "Selda Bağcan",          "", 200, false),
                new SearchCase("G2_026", "bilal",         "general", 5, Rule.FIRST_ARTIST_IS, "Bilal Sonses",          "", 200, false),
                new SearchCase("G2_027", "gülden",        "general", 5, Rule.FIRST_ARTIST_IS, "Gülden Karaböcek",      "", 200, false),
                new SearchCase("G2_028", "engin",         "general", 5, Rule.FIRST_ARTIST_IS, "Engin Nurşani",         "", 200, false),
                new SearchCase("G2_029", "şebnem",        "general", 5, Rule.FIRST_ARTIST_IS, "Şebnem Ferah",          "", 200, false),
                new SearchCase("G2_030", "ayaz",          "general", 5, Rule.FIRST_ARTIST_IS, "Ayaz Erdoğan",          "", 200, false),
                new SearchCase("G2_031", "ajda",          "general", 5, Rule.FIRST_ARTIST_IS, "Ajda Pekkan",           "", 200, false),
                new SearchCase("G2_032", "izel",          "general", 5, Rule.FIRST_ARTIST_IS, "İzel",                  "", 200, false),
                new SearchCase("G2_033", "aynur",         "general", 5, Rule.FIRST_ARTIST_IS, "Aynur Aydın",           "", 200, false),
                new SearchCase("G2_034", "hayko",         "general", 5, Rule.FIRST_ARTIST_IS, "Hayko Cepkin",          "", 200, false),
                new SearchCase("G2_035", "koray",         "general", 5, Rule.FIRST_ARTIST_IS, "Koray Avcı",            "", 200, false),
                new SearchCase("G2_036", "ümit",          "general", 5, Rule.FIRST_ARTIST_IS, "Ümit Besen",            "", 200, false),
                new SearchCase("G2_037", "elif buse",     "general", 5, Rule.FIRST_ARTIST_IS, "Elif Buse Doğan",       "", 200, false),
                new SearchCase("G2_038", "özcan",         "general", 5, Rule.FIRST_ARTIST_IS, "Özcan Deniz",           "", 200, false),
                new SearchCase("G2_039", "taylor",        "general", 5, Rule.FIRST_ARTIST_IS, "Taylor Swift",          "", 200, false),

                // =========================================================================
                // G3 — SANATÇI: KISALTMA, ALIAS VE RUMUZ ARAMASI (15 case)
                // =========================================================================
                new SearchCase("G3_001", "84",             "general", 5, Rule.FIRST_ARTIST_IS, "seksendört",            "", 200, false),
                new SearchCase("G3_002", "can ozan",       "general", 5, Rule.FIRST_ARTIST_IS, "Canozan",               "", 200, false),
                new SearchCase("G3_003", "blok",           "general", 5, Rule.FIRST_ARTIST_IS, "BLOK3",                 "", 200, false),
                new SearchCase("G3_004", "sago",           "general", 5, Rule.FIRST_ARTIST_IS, "Sagopa Kajmer",         "", 200, false),
                new SearchCase("G3_005", "ati",            "general", 5, Rule.FIRST_ARTIST_IS, "Ati242",                "", 200, false),
                new SearchCase("G3_006", "cash",           "general", 5, Rule.FIRST_ARTIST_IS, "Cash Flow",             "", 200, false),
                new SearchCase("G3_007", "reyn",           "general", 5, Rule.FIRST_ARTIST_IS, "Reynmen",               "", 200, false),
                new SearchCase("G3_008", "emircan iğrek",  "general", 5, Rule.FIRST_ARTIST_IS, "Emir Can İğrek",        "", 200, false),
                new SearchCase("G3_009", "no1",            "general", 5, Rule.FIRST_ARTIST_IS, "No.1",                  "", 200, false),
                new SearchCase("G3_010", "halo",           "general", 5, Rule.FIRST_ARTIST_IS, "Halodayı",              "", 200, false),
                new SearchCase("G3_011", "50",             "general", 5, Rule.FIRST_ARTIST_IS, "50 Cent",               "", 200, false),
                new SearchCase("G3_012", "no 1",           "general", 5, Rule.FIRST_ARTIST_IS, "No.1",                  "", 200, false),
                new SearchCase("G3_013", "deha",           "general", 5, Rule.FIRST_ARTIST_IS, "DEHA INC",              "", 200, false),
                new SearchCase("G3_014", "sibelcan",       "general", 5, Rule.FIRST_ARTIST_IS, "Sibel Can",             "", 200, false),
                new SearchCase("G3_015", "dktt",           "general", 5, Rule.FIRST_ARTIST_IS, "Dolu Kadehi Ters Tut",  "", 200, false),

                // =========================================================================
                // G4 — SANATÇI: YAZIM HATALI / FONETİK BENZER ARAMA (10 case)
                // =========================================================================
                new SearchCase("G4_001", "ezel",            "general", 5, Rule.FIRST_ARTIST_IS, "Ezhel",                  "", 200, false),
                new SearchCase("G4_002", "reymen",           "general", 5, Rule.FIRST_ARTIST_IS, "Reynmen",                "", 200, false),
                new SearchCase("G4_003", "ibrahim tat",      "general", 5, Rule.FIRST_ARTIST_IS, "İbrahim Tatlıses",       "", 200, false),
                new SearchCase("G4_004", "hejan",            "general", 5, Rule.FIRST_ARTIST_IS, "Heijan",                 "", 200, false),
                new SearchCase("G4_005", "semicek",          "general", 5, Rule.FIRST_ARTIST_IS, "Semicenk",               "", 200, false),
                new SearchCase("G4_006", "emre gel",         "general", 5, Rule.FIRST_ARTIST_IS, "Emre Fel",               "", 200, false),
                new SearchCase("G4_007", "kofn",             "general", 5, Rule.FIRST_ARTIST_IS, "KÖFN",                   "", 200, false),
                new SearchCase("G4_008", "sertap",           "general", 5, Rule.FIRST_ARTIST_IS, "Sertab Erener",          "", 200, false),
                new SearchCase("G4_009", "mr ve ötei",       "general", 5, Rule.FIRST_ARTIST_IS, "Mor ve Ötesi",           "", 200, false),
                new SearchCase("G4_010", "dolu kadhi tut",   "general", 5, Rule.FIRST_ARTIST_IS, "Dolu Kadehi Ters Tut",   "", 200, false),

                // =========================================================================
                // G5 — KATEGORİ: TÜR VE KÜLTÜREL ARAMA (15 case)
                // =========================================================================
                new SearchCase("G5_001", "pop",            "general", 5, Rule.TOPN_RELATED_PLAYLIST, "pop",          "", 200, false),
                new SearchCase("G5_002", "arabesk",        "general", 5, Rule.TOPN_RELATED_PLAYLIST, "arabesk",      "", 200, false),
                new SearchCase("G5_003", "ilahi",          "general", 5, Rule.TOPN_RELATED_PLAYLIST, "ilahi",        "", 200, false),
                new SearchCase("G5_004", "karadeniz",      "general", 5, Rule.TOPN_RELATED_PLAYLIST, "karadeniz",    "", 200, false),
                new SearchCase("G5_005", "halay",          "general", 5, Rule.TOPN_RELATED_PLAYLIST, "halay",        "", 200, false),
                new SearchCase("G5_006", "roman",          "general", 5, Rule.TOPN_RELATED_PLAYLIST, "roman",        "", 200, false),
                new SearchCase("G5_007", "çocuk",          "general", 5, Rule.TOPN_RELATED_PLAYLIST, "çocuk",        "", 200, false),
                new SearchCase("G5_008", "klasik",         "general", 5, Rule.TOPN_RELATED_PLAYLIST, "klasik",       "", 200, false),
                new SearchCase("G5_009", "ankara",         "general", 5, Rule.TOPN_RELATED_PLAYLIST, "ankara",       "", 200, false),
                new SearchCase("G5_010", "akustik",        "general", 5, Rule.TOPN_RELATED_PLAYLIST, "akustik",      "", 200, false),
                new SearchCase("G5_011", "çocuk şarkıları","general", 5, Rule.TOPN_RELATED_PLAYLIST, "çocuk",        "", 200, false),
                new SearchCase("G5_012", "türkçe",         "general", 5, Rule.TOPN_RELATED_PLAYLIST, "türkçe",       "", 200, false),
                new SearchCase("G5_013", "rock",           "general", 5, Rule.TOPN_RELATED_PLAYLIST, "rock",         "", 200, false),
                new SearchCase("G5_014", "türk sanat",     "general", 5, Rule.TOPN_RELATED_PLAYLIST, "türk sanat",   "", 200, false),
                new SearchCase("G5_015", "ramazan",        "general", 5, Rule.TOPN_RELATED_PLAYLIST, "ramazan",      "", 200, false),

                // =========================================================================
                // G6 — KATEGORİ: DÖNEM ARAMASI VE FORMAT VARYASYONLARI (7 case)
                // =========================================================================
                new SearchCase("G6_001", "90",        "general", 5, Rule.TOPN_RELATED_PLAYLIST, "90",     "", 200, false),
                new SearchCase("G6_002", "90lar",      "general", 5, Rule.TOPN_RELATED_PLAYLIST, "90",     "", 200, false),
                new SearchCase("G6_003", "90'lar",     "general", 5, Rule.TOPN_RELATED_PLAYLIST, "90",     "", 200, false),
                new SearchCase("G6_004", "90s",        "general", 5, Rule.TOPN_RELATED_PLAYLIST, "90",     "", 200, false),
                new SearchCase("G6_005", "90 lar",     "general", 5, Rule.TOPN_RELATED_PLAYLIST, "90",     "", 200, false),
                new SearchCase("G6_006", "80",         "general", 5, Rule.TOPN_RELATED_PLAYLIST, "80",     "", 200, false),
                new SearchCase("G6_007", "90 lar pop", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "90'lar", "", 200, false),

                // =========================================================================
                // G7 — KATEGORİ: ATMOSFER, AKTİVİTE VE DİL TERCİHİ ARAMASI (5 case)
                // =========================================================================
                new SearchCase("G7_001", "yabancı",   "general", 5, Rule.TOPN_RELATED_PLAYLIST, "yabancı",   "", 200, false),
                new SearchCase("G7_002", "oyun hava", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "oyun hava", "", 200, false),
                new SearchCase("G7_003", "spor",      "general", 5, Rule.TOPN_RELATED_PLAYLIST, "spor",      "", 200, false),
                new SearchCase("G7_004", "dans",      "general", 5, Rule.TOPN_RELATED_PLAYLIST, "dans",      "", 200, false),
                new SearchCase("G7_005", "meditasyon","general", 5, Rule.TOPN_RELATED_PLAYLIST, "meditasyon","", 200, false),

                // =========================================================================
                // G8 — ŞARKI: DOĞRUDAN / KISMİ ŞARKI ADI İLE ARAMA (11 case)
                // =========================================================================
                new SearchCase("G8_001", "nasır",               "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Melike Şahin",          "Nasır",                   200, false),
                new SearchCase("G8_002", "kaybolurum gülüşünde","general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "İkilem",                "Kaybolurum Gülüşünde",    200, false),
                new SearchCase("G8_003", "bodrum",              "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Yüzyüzeyken Konuşuruz", "Bodrum",                  200, false),
                new SearchCase("G8_004", "ağlama ben ağlarım",  "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Canozan",               "Ağlama ben ağlarım",      200, false),
                new SearchCase("G8_005", "bana sor",            "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Ferdi Tayfur",          "Bana Sor",                200, false),
                new SearchCase("G8_006", "ağlama ben",          "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Canozan",               "Ağlama ben ağlarım",      200, false),
                new SearchCase("G8_007", "erik",                "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",                      "Erik Dalı",               200, false),
                new SearchCase("G8_008", "karakedi",            "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Melis Fis",             "Kara Kedi",               200, false),
                new SearchCase("G8_009", "gözlerime bak",       "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Mert Demir",            "Gözlerime Bak",           200, false),
                new SearchCase("G8_010", "doğuştan",            "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Lvbel C5",              "Doğuştan Beri Haklıyım",  200, false),
                new SearchCase("G8_011", "kalbimin sahibine",   "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "İrem Derici",           "Kalbimin Tek Sahibine",   200, false),

                // =========================================================================
                // G9 — ŞARKI: ŞARKI SÖZÜ PARÇASI İLE ARAMA (15 case)
                // =========================================================================
                new SearchCase("G9_001", "yandım ah",                    "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Sakiler",       "Yalanı Bırak",        200, false),
                new SearchCase("G9_002", "sus",                          "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Ceza",          "Suspus",              200, false),
                new SearchCase("G9_003", "pus",                          "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Ceza",          "Suspus",              200, false),
                new SearchCase("G9_004", "bak ben yara gibiyim",         "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Emir Can İğrek","Nalan",               200, false),
                new SearchCase("G9_005", "çölüme yağmur oldun",          "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Müslüm Gürses", "Affet",               200, false),
                new SearchCase("G9_006", "zaten aşklar hep yalan dolan", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Yıldız Tilbe",  "Sana Değer",          200, false),
                new SearchCase("G9_007", "sana hastayım anlasana",       "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Derya Uluğ",    "Yansıma",             200, false),
                new SearchCase("G9_008", "arabam",                       "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Sefo",          "Araba",               200, false),
                new SearchCase("G9_009", "hadi ya",                      "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Melis Kar",     "Yatıya",              200, false),
                new SearchCase("G9_010", "babalar",                      "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Blok3",         "PATLAT",              200, false),
                new SearchCase("G9_011", "sarışınlar",                   "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Derya Uluğ",    "Esmerin Adı Oya",     200, false),
                new SearchCase("G9_012", "silemez o beni",               "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Yıldız Tilbe",  "Dizine Dursun",       200, false),
                new SearchCase("G9_013", "babalar sözünü tutar",         "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Blok3",         "PATLAT",              200, false),
                new SearchCase("G9_014", "affet bu gece istedim ölmek",  "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Model",         "Pembe Mezarlık",      200, false),
                new SearchCase("G9_015", "güldün ne güzel",              "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Pinhani",       "Ne Güzel Güldün",     200, false),

                // =========================================================================
                // G10 — ŞARKI: YAZIM HATALI ŞARKI ADI / SÖZ ARAMASI (3 case)
                // =========================================================================
                new SearchCase("G10_001", "illede sen",      "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Azer Bülbül",  "İlle De Sen",      200, false),
                new SearchCase("G10_002", "çok geç şmdi",    "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Edis",         "Yalan",            200, false),
                new SearchCase("G10_003", "lacivert eceler", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Ferhat Göçer", "Lacivert Geceler", 200, false),

                // =========================================================================
                // G11 — GÜVENLİK VE SINIR KOŞULLARI (3 case)
                // =========================================================================
                new SearchCase("G11_001", "   ",                             "general", 10, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("G11_002", "a".repeat(150),                   "general", 10, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("G11_003", "<script>alert('test')</script>",  "general",  5, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),

                // =========================================================================
                // G12 — ALBÜM ADI İLE DOĞRUDAN ARAMA (6 case) — Gereksinim #3
                // =========================================================================
                new SearchCase("G12_001", "kalbim yaralı",  "general", 5, Rule.TOPN_RELATED_ALBUM, "kalbim",    "", 200, false),
                new SearchCase("G12_002", "best of",        "general", 5, Rule.TOPN_RELATED_ALBUM, "best",      "", 200, false),
                new SearchCase("G12_003", "greatest hits",  "general", 5, Rule.TOPN_RELATED_ALBUM, "greatest",  "", 200, false),
                new SearchCase("G12_004", "akustik set",    "general", 5, Rule.TOPN_RELATED_ALBUM, "akustik",   "", 200, false),
                new SearchCase("G12_005", "annem",          "general", 5, Rule.TOPN_RELATED_ALBUM, "annem",     "", 200, false),
                new SearchCase("G12_006", "hasret",         "general", 5, Rule.TOPN_RELATED_ALBUM, "hasret",    "", 200, false),

                // =========================================================================
                // G13 — SANATÇI + ŞARKI KOMBİNE ARAMA (8 case) — Gereksinim #5
                // =========================================================================
                new SearchCase("G13_001", "tarkan şımarık",         "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Tarkan",         "Şımarık",          200, false),
                new SearchCase("G13_002", "sıla boş ver",           "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Sıla",           "Boş Ver",          200, false),
                new SearchCase("G13_003", "ezhel müptezel",         "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Ezhel",          "Müptezel",         200, false),
                new SearchCase("G13_004", "duman senden daha güzel","general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Duman",          "Senden Daha Güzel",200, false),
                new SearchCase("G13_005", "göksel ceylan",          "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Göksel",         "Ceylan",           200, false),
                new SearchCase("G13_006", "ceza yerlan",            "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Ceza",           "Yerlan",           200, false),
                new SearchCase("G13_007", "rihanna diamonds",       "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Rihanna",        "Diamonds",         200, false),
                new SearchCase("G13_008", "eminem lose yourself",   "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Eminem",         "Lose Yourself",    200, false),

                // =========================================================================
                // G14 — BÜYÜK/KÜÇÜK HARF DUYARSIZLIĞI (6 case) — Gereksinim #6
                // =========================================================================
                new SearchCase("G14_001", "TARKAN",        "general", 5, Rule.FIRST_ARTIST_IS, "Tarkan",         "", 200, false),
                new SearchCase("G14_002", "SEZEN AKSU",    "general", 5, Rule.FIRST_ARTIST_IS, "Sezen Aksu",     "", 200, false),
                new SearchCase("G14_003", "EMRE AYDIN",    "general", 5, Rule.FIRST_ARTIST_IS, "Emre Aydın",     "", 200, false),
                new SearchCase("G14_004", "CEZA",          "general", 5, Rule.FIRST_ARTIST_IS, "Ceza",           "", 200, false),
                new SearchCase("G14_005", "MOR VE ÖTESİ", "general", 5, Rule.FIRST_ARTIST_IS, "Mor ve Ötesi",   "", 200, false),
                new SearchCase("G14_006", "DUMAN",         "general", 5, Rule.FIRST_ARTIST_IS, "Duman",          "", 200, false),

                // =========================================================================
                // G15 — TÜRKÇE KLAVYE ASCII ALTERNATİF ARAMA (6 case) — Gereksinim #7, #8
                // G1/G2 ile aynı sanatçıları test eder ancak Türkçe karakter OLMADAN (ı→i, ü→u vb.)
                // =========================================================================
                new SearchCase("G15_001", "sila",          "general", 5, Rule.FIRST_ARTIST_IS, "Sıla",           "", 200, false),
                new SearchCase("G15_002", "gunes",         "general", 5, Rule.FIRST_ARTIST_IS, "Güneş",          "", 200, false),
                new SearchCase("G15_003", "dogus",         "general", 5, Rule.FIRST_ARTIST_IS, "Doğuş",          "", 200, false),
                new SearchCase("G15_004", "kurtulus",      "general", 5, Rule.FIRST_ARTIST_IS, "Kurtuluş Kuş",   "", 200, false),
                new SearchCase("G15_005", "sebnem",        "general", 5, Rule.FIRST_ARTIST_IS, "Şebnem Ferah",   "", 200, false),
                new SearchCase("G15_006", "ozcan",         "general", 5, Rule.FIRST_ARTIST_IS, "Özcan Deniz",    "", 200, false),

                // =========================================================================
                // G16 — KISA SORGU (2-3 KARAKTER) (6 case) — Gereksinim #9, #10
                // =========================================================================
                new SearchCase("G16_001", "ta", "general", 10, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("G16_002", "si", "general", 10, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("G16_003", "uz", "general", 10, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("G16_004", "dj", "general", 10, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("G16_005", "rih", "general", 10, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("G16_006", "tar", "general", 10, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),

                // =========================================================================
                // G17 — YANIT SÜRESİ PERFORMANS TESTİ (5 case) — Gereksinim #20
                // =========================================================================
                new SearchCase("G17_001", "tarkan",         "general", 5, Rule.RESPONSE_TIME_UNDER_THRESHOLD, "", "", 200, false),
                new SearchCase("G17_002", "pop müzik",      "general", 5, Rule.RESPONSE_TIME_UNDER_THRESHOLD, "", "", 200, false),
                new SearchCase("G17_003", "sıla",           "general", 5, Rule.RESPONSE_TIME_UNDER_THRESHOLD, "", "", 200, false),
                new SearchCase("G17_004", "90 lar",         "general", 5, Rule.RESPONSE_TIME_UNDER_THRESHOLD, "", "", 200, false),
                new SearchCase("G17_005", "en iyi türkçe",  "general", 5, Rule.RESPONSE_TIME_UNDER_THRESHOLD, "", "", 200, false),

                // =========================================================================
                // G18 — FAZLA / TEKRARLAYAN KARAKTER TOLERANSI (4 case) — Gereksinim #26
                // =========================================================================
                new SearchCase("G18_001", "tarkannnn",  "general", 5, Rule.FIRST_ARTIST_IS, "Tarkan", "", 200, false),
                new SearchCase("G18_002", "sılaaa",     "general", 5, Rule.FIRST_ARTIST_IS, "Sıla",   "", 200, false),
                new SearchCase("G18_003", "emiiinem",   "general", 5, Rule.FIRST_ARTIST_IS, "Eminem", "", 200, false),
                new SearchCase("G18_004", "edissss",    "general", 5, Rule.FIRST_ARTIST_IS, "Edis",   "", 200, false),

                // =========================================================================
                // G19 — EMOJİ VE ÖZEL KARAKTER ARAMASI (4 case) — Gereksinim #27
                // =========================================================================
                new SearchCase("G19_001", "🎵 tarkan",  "general", 5, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("G19_002", "❤️ sıla",    "general", 5, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("G19_003", "🎤",          "general", 5, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("G19_004", "🎧🎶",        "general", 5, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),

                // =========================================================================
                // G20 — ANLAMSIZ / RASTGELE SORGU (4 case) — Gereksinim #28
                // =========================================================================
                new SearchCase("G20_001", "xktzplmns", "general", 5, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("G20_002", "asdfghjkl", "general", 5, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("G20_003", "zzzzzzz",   "general", 5, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("G20_004", "qwerty",    "general", 5, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true)
        );
    }

    // =========================================================================
    // TEST KOŞTURUCU — her case için çift API çağrısı yapar
    // =========================================================================
    @ParameterizedTest(name = "[{0}] term={1} type={2}")
    @MethodSource("cases")
    void run_case(SearchCase sc) {

        // ── 1. GENERAL TEST (active-indices) ──────────────────────────────────
        String generalIndex = getIndexName("general"); // → "active-indices"
        Response generalRes = api.search(sc.term(), generalIndex, sc.limit());
        String[] gen = doEvaluate(sc, generalRes, generalIndex);

        System.out.printf("[GEN ] %s | '%s' → %s | %s%n",
                sc.caseId(), sc.term(), generalIndex, gen[1]);

        // ── 2. SPESİFİK TEST (sadece kıyas grubu varsa) ───────────────────────
        String specificType  = specificTypeFor(sc.caseId());
        String specificIndex = specificType != null ? getIndexName(specificType) : null;
        String[] spe = null;
        if (specificType != null) {
            Response specificRes = api.search(sc.term(), specificIndex, sc.limit());
            spe = doEvaluate(sc, specificRes, specificIndex);
            System.out.printf("[TEK ] %s | '%s' → %s | %s%n",
                    sc.caseId(), sc.term(), specificIndex, spe[1]);
        }

        // ── 3. RAPOR SATIRI ───────────────────────────────────────────────────
        addReportRow(sc,
                gen[0], generalIndex, gen[1], gen[2],
                specificType, specificIndex,
                spe != null ? spe[1] : null,
                spe != null ? spe[2] : null);

        // ── 4. JUnit TEST SONUCU — general sonucuna göre pass / fail ──────────
        if ("NOK".equals(gen[1])) {
            fail(gen[2]);
        }
    }

    // =========================================================================
    // KURAL DEĞERLENDİRME — fail() veya addReportRow() ÇAĞIRMAZ
    // Dönüş: String[]{ expectedText, "OK"/"NOK", detailMessage }
    // =========================================================================
    private String[] doEvaluate(SearchCase sc, Response res, String indexName) {
        int httpStatus = res.statusCode();

        if (sc.expectedStatus() != -1 && httpStatus != sc.expectedStatus()) {
            return new String[]{
                    "API HTTP " + sc.expectedStatus() + " dönmelidir.",
                    "NOK", "SİSTEM HATASI: API HTTP " + httpStatus + " döndürdü!"};
        }
        if (sc.expectedStatus() == -1 && httpStatus != 200 && httpStatus != 400) {
            return new String[]{
                    "API HTTP 200 veya 400 dönmelidir.",
                    "NOK", "SİSTEM HATASI: API HTTP " + httpStatus + " döndürdü!"};
        }
        if (httpStatus == 400 && sc.rule() == Rule.DISCOVER_LIMIT_ZERO_STATUS) {
            return new String[]{
                    "API 400 veya 200 dönmelidir (Discovery).",
                    "OK", "API 400 döndürdü. Discovery senaryosu kabul edildi."};
        }

        JsonPath jp   = res.jsonPath();
        List<Object> list = resultsList(jp);

        if (list == null) {
            return new String[]{
                    "Arama sonucunda en az 1 kayıt dönmesi beklenir.",
                    "NOK", "Results listesi null! Gateway formatı bozuk."};
        }
        if (!sc.allowEmptyResults() && list.isEmpty()) {
            return new String[]{
                    "Arama sonucunda en az 1 kayıt dönmesi beklenir.",
                    "NOK", "Liste boş döndü! Beklenen veri bulunamadı."};
        }

        return switch (sc.rule()) {
            case FIRST_ARTIST_IS              -> evalFirstArtistIs(sc, jp, list, indexName);
            case TOPN_HAS_ARTIST              -> evalTopNHasArtist(sc, jp, indexName);
            case TOPN_HAS_ARTIST_AND_TRACK    -> evalTopNHasArtistAndTrack(sc, jp, indexName);
            case TOPN_RELATED_ALBUM           -> evalTopNRelatedAlbum(sc, jp, indexName);
            case TOPN_RELATED_PLAYLIST        -> evalTopNRelatedPlaylist(sc, jp, indexName);
            case CONTRACT_HAS_FIELDS          -> evalContract(list);
            case RESULTCOUNT_MATCHES_SIZE     -> evalResultCountMatchesSize(list);
            case LIMIT_RESPECTED              -> evalLimitRespected(sc, list);
            case ALLOW_EMPTY_RESULTS          -> evalAllowEmpty(list);
            case DISCOVER_LIMIT_ZERO_STATUS   -> evalDiscoverLimitZero(list);
            case RESPONSE_TIME_UNDER_THRESHOLD -> evalResponseTime(res);
            default -> new String[]{"Kural değerlendirilmedi.", "OK", "Bilinmeyen kural: " + sc.rule()};
        };
    }

    // ── Kural bazlı eval metotları ────────────────────────────────────────────

    private String[] evalFirstArtistIs(SearchCase sc, JsonPath jp, List<Object> list, String indexName) {
        String expected = "Arama sonucunda dönen listenin 1. sırasındaki sanatçı adı '"
                + sc.expArtistOrKeyword() + "' olmalıdır.";
        if (list.isEmpty()) return new String[]{expected, "NOK", "Liste boş döndü."};
        String basePath    = MuudSearchUtils.getBasePath(jp);
        String firstArtist = safeStr(jp.getString(basePath + "[0].data.performerName"));
        if (!containsTRInsensitive(firstArtist, sc.expArtistOrKeyword())) {
            String note = findArtistPositionNote(sc, indexName);
            return new String[]{expected, "NOK",
                    "1. sıradaki kayıt farklı: '" + firstArtist + "'. " + note};
        }
        return new String[]{expected, "OK",
                "Başarılı. İlk sırada '" + firstArtist + "' sanatçısı geldi."};
    }

    private String[] evalTopNHasArtist(SearchCase sc, JsonPath jp, String indexName) {
        String expected = "Arama sonuçlarının ilk " + sc.limit() + " kaydı içerisinde '"
                + sc.expArtistOrKeyword() + "' sanatçısı yer almalıdır.";
        int idx = findArtistIndex(jp, sc.limit(), sc.expArtistOrKeyword());
        if (idx == -1) {
            String note = findArtistPositionNote(sc, indexName);
            return new String[]{expected, "NOK",
                    "İlk " + sc.limit() + " sonuçta bulunamadı. " + note};
        }
        String bp0 = MuudSearchUtils.getBasePath(jp);
        String fa0 = safeStr(jp.getString(bp0 + "[" + idx + "].data.performerName"));
        return new String[]{expected, "OK",
                "Başarılı. Sanatçı " + (idx + 1) + ". sırada bulundu: '" + fa0 + "'"};
    }

    private String[] evalTopNHasArtistAndTrack(SearchCase sc, JsonPath jp, String indexName) {
        String expected = "İlk " + sc.limit() + " sonuç içinde Sanatçı: '"
                + sc.expArtistOrKeyword() + "' ve Şarkı: '" + sc.expTrack() + "' eşleşmesi bulunmalıdır.";
        int idx = findArtistAndTrackIndex(jp, sc.limit(), sc.expArtistOrKeyword(), sc.expTrack());
        if (idx == -1) {
            String note = findArtistTrackPositionNote(sc, indexName);
            return new String[]{expected, "NOK", "Eşleşme bulunamadı. " + note};
        }
        String bp1 = MuudSearchUtils.getBasePath(jp);
        String fa1 = safeStr(jp.getString(bp1 + "[" + idx + "].data.performerName"));
        String fs1 = safeStr(jp.getString(bp1 + "[" + idx + "].data.songName"));
        if (fs1.isEmpty()) fs1 = safeStr(jp.getString(bp1 + "[" + idx + "].data.albumName"));
        return new String[]{expected, "OK",
                "Başarılı. " + (idx + 1) + ". sırada bulundu: '" + fa1 + "' - '" + fs1 + "'"};
    }

    /**
     * Albüm, playlist, şarkı veya sanatçı adında keyword geçiyor mu?
     * findAlbumKeywordIndex tüm içerik tiplerini kontrol eder.
     */
    private String[] evalTopNRelatedAlbum(SearchCase sc, JsonPath jp, String indexName) {
        String expected = "İlk " + sc.limit() + " sonuç içinde '"
                + sc.expArtistOrKeyword() + "' ifadesi geçmelidir.";
        int idx = findAlbumKeywordIndex(jp, sc.limit(), sc.expArtistOrKeyword());
        if (idx == -1) {
            return new String[]{expected, "NOK", "İlgili keyword içeriklerde bulunamadı."};
        }
        String bp2 = MuudSearchUtils.getBasePath(jp);
        String fn2 = safeStr(jp.getString(bp2 + "[" + idx + "].data.albumName"));
        if (fn2.isEmpty()) fn2 = safeStr(jp.getString(bp2 + "[" + idx + "].data.playlistName"));
        if (fn2.isEmpty()) fn2 = safeStr(jp.getString(bp2 + "[" + idx + "].data.songName"));
        if (fn2.isEmpty()) fn2 = safeStr(jp.getString(bp2 + "[" + idx + "].data.performerName"));
        return new String[]{expected, "OK",
                "Başarılı. " + (idx + 1) + ". sırada bulundu: '" + fn2 + "'"};
    }

    private String[] evalTopNRelatedPlaylist(SearchCase sc, JsonPath jp, String indexName) {
        String expected = "İlk " + sc.limit() + " sonuç içinde '"
                + sc.expArtistOrKeyword() + "' adını içeren bir playlist bulunmalıdır.";
        String basePath = MuudSearchUtils.getBasePath(jp);
        for (int i = 0; i < sc.limit(); i++) {
            String playlistName = safeStr(jp.getString(basePath + "[" + i + "].data.playlistName"));
            if (!playlistName.isEmpty() && containsTRInsensitive(playlistName, sc.expArtistOrKeyword())) {
                return new String[]{expected, "OK",
                        "Başarılı. Playlist " + (i + 1) + ". sırada bulundu: '" + playlistName + "'"};
            }
        }
        // Debug: daha geniş arama
        for (int i = sc.limit(); i < DEBUG_LOOKUP_LIMIT; i++) {
            String playlistName = safeStr(jp.getString(basePath + "[" + i + "].data.playlistName"));
            if (!playlistName.isEmpty() && containsTRInsensitive(playlistName, sc.expArtistOrKeyword())) {
                return new String[]{expected, "NOK",
                        "İlk " + sc.limit() + " sonuçta bulunamadı. Daha geniş aramada " + (i + 1) + ". sırada bulundu."};
            }
        }
        return new String[]{expected, "NOK",
                "İlk " + sc.limit() + " sonuçta '" + sc.expArtistOrKeyword() + "' adını içeren playlist bulunamadı."};
    }

    private String[] evalContract(List<Object> list) {
        String expected = "API cevabı JSON formatında veri dizisini içermelidir.";
        if (list == null) return new String[]{expected, "NOK", "Veri dizisi cevapta bulunamadı!"};
        return new String[]{expected, "OK", "Başarılı. Zorunlu alanlar mevcut."};
    }

    private String[] evalResultCountMatchesSize(List<Object> list) {
        String expected = "Arama sonucunda tutarlı bir liste dönmelidir.";
        if (list == null) return new String[]{expected, "NOK", "Hata: Liste bulunamadı."};
        return new String[]{expected, "OK", "Başarılı. Dönen Veri: " + list.size()};
    }

    private String[] evalLimitRespected(SearchCase sc, List<Object> list) {
        String expected = "Dönen sonuç sayısı limit (" + sc.limit() + ") değerini aşmamalıdır.";
        if (list.size() > sc.limit()) {
            return new String[]{expected, "NOK",
                    "Limit aşıldı! Dönen kayıt sayısı: " + list.size()};
        }
        return new String[]{expected, "OK", "Başarılı. Dönen kayıt sayısı: " + list.size()};
    }

    private String[] evalAllowEmpty(List<Object> list) {
        String expected = "Sistem bu input için 200 OK dönmeli ve çökmemelidir.";
        return new String[]{expected, "OK",
                "Başarılı. Sistem stabil yanıt verdi. Sonuç sayısı: " + list.size()};
    }

    private String[] evalDiscoverLimitZero(List<Object> list) {
        String expected = "Limit=0 gönderildiğinde sistem 200 veya 400 vermelidir.";
        return new String[]{expected, "OK",
                "Keşif Sonucu: API 200 döndürdü ve " + list.size() + " sonuç verdi."};
    }

    /**
     * Yanıt süresini eşikle karşılaştırır.
     * Eşik varsayılan 250ms; -DresponseThresholdMs=XXX ile override edilebilir.
     * REST Assured'ın Response.time() metodu millisaniye cinsinden döner.
     */
    private String[] evalResponseTime(Response res) {
        final long THRESHOLD_MS = Long.parseLong(
                System.getProperty("responseThresholdMs", "250"));
        long responseTime = res.time();
        String expected = "API yanıt süresi " + THRESHOLD_MS + "ms altında olmalıdır.";
        if (responseTime > THRESHOLD_MS) {
            return new String[]{expected, "NOK",
                    "Yanıt süresi eşiği aşıldı: " + responseTime + "ms (eşik: " + THRESHOLD_MS + "ms)"};
        }
        return new String[]{expected, "OK", "Yanıt süresi: " + responseTime + "ms"};
    }

    // =========================================================================
    // YARDIMCI METOTLAR
    // =========================================================================

    /**
     * caseId prefix'ine göre spesifik indeks tipini döndürür.
     *
     * Sanatçı grupları → "performer" (indeks 10)
     * Kategori/Liste grupları → "playlist" (indeks 11)
     * Albüm grupları → "album" (indeks 9)
     * Şarkı grupları → "songs" (indeks 48)
     * Güvenlik/Sınır/Performans grupları → null (kıyas yok)
     */
    private static String specificTypeFor(String caseId) {
        if (caseId == null) return null;
        // Sanatçı grupları
        if (caseId.startsWith("G1_") || caseId.startsWith("G2_")
                || caseId.startsWith("G3_") || caseId.startsWith("G4_")
                || caseId.startsWith("G14_") || caseId.startsWith("G15_")
                || caseId.startsWith("G18_")) return "performer";
        // Kategori/Liste grupları
        if (caseId.startsWith("G5_") || caseId.startsWith("G6_")
                || caseId.startsWith("G7_")) return "playlist";
        // Albüm araması
        if (caseId.startsWith("G12_")) return "album";
        // Şarkı grupları
        if (caseId.startsWith("G8_") || caseId.startsWith("G9_")
                || caseId.startsWith("G10_") || caseId.startsWith("G13_")) return "songs";
        // Kıyas yok: G11, G16, G17, G19, G20
        return null;
    }

    private String findArtistPositionNote(SearchCase sc, String indexName) {
        Response dbg = api.search(sc.term(), indexName, DEBUG_LOOKUP_LIMIT);
        int idx = findArtistIndex(dbg.jsonPath(), DEBUG_LOOKUP_LIMIT, sc.expArtistOrKeyword());
        return idx == -1
                ? "Geniş aramada da (" + DEBUG_LOOKUP_LIMIT + " kayıt) bulunamadı."
                : "Daha geniş aramada " + (idx + 1) + ". sırada bulundu.";
    }

    private String findArtistTrackPositionNote(SearchCase sc, String indexName) {
        Response dbg = api.search(sc.term(), indexName, DEBUG_LOOKUP_LIMIT);
        int idx = findArtistAndTrackIndex(dbg.jsonPath(), DEBUG_LOOKUP_LIMIT,
                sc.expArtistOrKeyword(), sc.expTrack());
        return idx == -1
                ? "Geniş aramada da (" + DEBUG_LOOKUP_LIMIT + " kayıt) bulunamadı."
                : "Daha geniş aramada " + (idx + 1) + ". sırada bulundu.";
    }

    private void addReportRow(SearchCase sc,
                              String expectedText,
                              String generalIndex,  String generalStatus,  String generalSonuc,
                              String specificType,  String specificIndex,
                              String specificStatus, String specificSonuc) {
        if (!EXCEL_REPORT_ENABLED) return;
        String testName    = "\"" + sc.term() + "\" araması yapılır";
        String description = "Arama terimi: '" + sc.term() + "' kullanılarak istek atılır.";
        REPORT_ROWS.add(new TestResultRow(
                sc.caseId(), testName, description, expectedText,
                "general", generalIndex, generalStatus, generalSonuc,
                specificType, specificIndex, specificStatus, specificSonuc
        ));
    }

    @AfterAll
    static void writeExcelReportIfEnabled() {
        if (EXCEL_REPORT_ENABLED) ExcelTestReportWriter.writeKapsayici(REPORT_ROWS);
    }
}