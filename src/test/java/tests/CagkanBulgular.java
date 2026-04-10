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
 * UAT Case Dokümanı — Search Engine — Muud
 *
 * Orijinal UAT dokümanındaki 149 case (sıralama UAT Excel ile birebir).
 * hareketli ve kuran: sistemde mevcut olmadığı için atlandı.
 *
 * Kıyas mantığı (KapsayiciTest ile aynı yapı):
 *   FIRST_ARTIST_IS / TOPN_HAS_ARTIST     → general vs performer (indeks 10)
 *   TOPN_RELATED_PLAYLIST                 → general vs playlist  (indeks 11)
 *   TOPN_HAS_ARTIST_AND_TRACK             → general vs songs     (indeks 48)
 *   ALLOW_EMPTY_RESULTS                   → yalnızca general (kıyas yok)
 */
public class CagkanBulgular extends TestConfig {

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

                // ─────────────────────────────────────────────────────────────────
                // BÖLÜM 1 — İÇERİK HİÇ GELMİYOR (içerik sistemde var ama sonuçlarda yok)
                // ─────────────────────────────────────────────────────────────────
                new SearchCase("BULGU_001", "Hermes",              "general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Batuflex",       "Hermès 2.0",                200, false),
                new SearchCase("BULGU_002", "a canım",             "general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Mabel Matiz",    "A Canım",                   200, false),
                new SearchCase("BULGU_003", "acanım",              "general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Mabel Matiz",    "A Canım",                   200, false),
                new SearchCase("BULGU_010", "maraton",             "general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Ati242",         "Maraton",                   200, false),
                new SearchCase("BULGU_013", "meğerse",             "general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Liner",          "Meğerse",                   200, false),
                new SearchCase("BULGU_014", "çok pardon",          "general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Lvbel C5",       "COOOK PARDON",              200, false),
                new SearchCase("BULGU_032", "dame un grr",         "general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Dame Un Grr",               200, false),
                new SearchCase("BULGU_034", "vidrado em",          "general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Vidrado",                   200, false),
                new SearchCase("BULGU_036", "can efendim",         "general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Can Efendim",               200, false),
                new SearchCase("BULGU_037", "çıt çıt",             "general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Çıt Çıt",                   200, false),
                new SearchCase("BULGU_038", "çıt çıt çedene",      "general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Çıt Çıt",                   200, false),
                new SearchCase("BULGU_040", "hav hav",             "general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Lvbel C5",       "Havhavhav",                 200, false),
                new SearchCase("BULGU_046", "can ozan",            "general", 10, Rule.TOPN_HAS_ARTIST,           "Canozan",        "",                          200, false),
                new SearchCase("BULGU_053", "kök$l",               "general", 10, Rule.TOPN_HAS_ARTIST,           "Kök",            "",                          200, false),
                new SearchCase("BULGU_054", "just the way you are","general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Just The Way You Are",      200, false),
                new SearchCase("BULGU_057", "y",                   "general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Poizi",          "Y",                         200, false),
                new SearchCase("BULGU_058", "y poizi",             "general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Poizi",          "Y",                         200, false),
                new SearchCase("BULGU_062", "yaramızda kalsın",    "general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Merve Özbey",    "Yaramızda Kalsın",          200, false),
                new SearchCase("BULGU_110", "lvc5",                "general", 10, Rule.TOPN_HAS_ARTIST,           "Lvbel C5",       "",                          200, false),
                new SearchCase("BULGU_115", "pozi",                "general", 10, Rule.TOPN_HAS_ARTIST,           "Poizi",          "",                          200, false),
                new SearchCase("BULGU_116", "level c5",            "general", 10, Rule.TOPN_HAS_ARTIST,           "Lvbel C5",       "",                          200, false),
                new SearchCase("BULGU_117", "levelc5",             "general", 10, Rule.TOPN_HAS_ARTIST,           "Lvbel C5",       "",                          200, false),

                // ─────────────────────────────────────────────────────────────────
                // BÖLÜM 2 — EXACT MATCH İLK SIRAYA GELEMİYOR
                // Şarkı sistemde var ama ilk sırada değil → limit=5 ile top-5 varlık testi
                // ─────────────────────────────────────────────────────────────────
                new SearchCase("BULGU_004", "olmazlara vuruluyorum","general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Mert Demir",     "Olmazlara Vuruluyorum",     200, false),
                new SearchCase("BULGU_005", "çıkmaz bir sokakta",   "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Çıkmaz Bir Sokakta",        200, false),
                new SearchCase("BULGU_011", "geri ver",             "general", 3, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Wegh",           "Geri Ver",                  200, false),
                new SearchCase("BULGU_012", "saygımdan",            "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Bengü",          "Saygımdan",                 200, false),
                new SearchCase("BULGU_015", "dacia",                "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Lvbel C5",       "Dacia",                     200, false),
                new SearchCase("BULGU_020", "yalnızlığın çaresini bulmuşlar","general",5,Rule.TOPN_HAS_ARTIST_AND_TRACK,"",          "Yalnızlığın Çaresini Bulmuşlar",200,false),
                new SearchCase("BULGU_023", "yapar mısın",          "general", 3, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Poizi",          "Yapar Mısın",               200, false),
                new SearchCase("BULGU_024", "yerinde",              "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Sefo",           "Yerinde Dur",               200, false),
                new SearchCase("BULGU_025", "yerinde dur",          "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Sefo",           "Yerinde Dur",               200, false),
                new SearchCase("BULGU_028", "ey aşk",               "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Sezen Aksu",     "Ey Aşk",                    200, false),
                new SearchCase("BULGU_031", "giderim kırağınan",    "general", 3, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Onur Şan",       "Giderim Kırağınan",         200, false),
                new SearchCase("BULGU_033", "ara beni lütfen",      "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Funda Arar",     "Ara Beni Lütfen",           200, false),
                new SearchCase("BULGU_035", "aşk yok olmaktır",     "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Aşk Yok Olmaktır",          200, false),
                new SearchCase("BULGU_039", "çıkar biri karşıma",   "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Poizi",          "Çıkar Biri Karşıma",        200, false),
                new SearchCase("BULGU_041", "messy lola young",     "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Lola Young",     "Messy",                     200, false),
                new SearchCase("BULGU_042", "sen yanlış yaptın",    "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Şahin Kendirci", "Sen Yanlış Yaptın",         200, false),
                new SearchCase("BULGU_043", "vay dayı",             "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Aynur Polat",    "Vay Dayı",                  200, false),
                new SearchCase("BULGU_044", "silinmez",             "general", 3, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Mansur Ark",     "Silinmez",                  200, false),
                new SearchCase("BULGU_045", "halbuki",              "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Yalın",          "Halbuki",                   200, false),
                new SearchCase("BULGU_047", "duydun mu",            "general", 3, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Yusuf Güney",    "Duydun Mu",                 200, false),
                new SearchCase("BULGU_048", "sana güvenmiyorum",    "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Dedüblüman",     "Sana Güvenmiyorum",         200, false),
                new SearchCase("BULGU_049", "yasemen",              "general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Afra",           "Yasemen",                   200, false),
                new SearchCase("BULGU_050", "düşer o",              "general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK, "İzel",           "Düşer O",                   200, false),
                new SearchCase("BULGU_051", "kömür",                "general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Mabel Matiz",    "Kömür",                     200, false),
                new SearchCase("BULGU_052", "mabel kömür",          "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Mabel Matiz",    "Kömür",                     200, false),
                new SearchCase("BULGU_056", "snap",                 "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Manifest",       "Snap",                      200, false),
                new SearchCase("BULGU_059", "ama başaramadım",      "general", 3, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Burak Bulut",    "Ama Başaramadım",           200, false),
                new SearchCase("BULGU_060", "kts manifest",         "general", 3, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Manifest",       "KTS",                       200, false),
                new SearchCase("BULGU_061", "adına bir çizik çektim","general",5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Adına Bir Çizik Çektim",    200, false),
                new SearchCase("BULGU_063", "sev yeter",            "general", 3, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Sev Yeter",                 200, false),
                new SearchCase("BULGU_065", "kaybolurum gülüşünde", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "İkilem",         "Kaybolurum Gülüşünde",      200, false),
                new SearchCase("BULGU_066", "bak ben yara gibiyim", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Emir Can İğrek", "Nalan",                     200, false),
                new SearchCase("BULGU_071", "ağlama ben ağlarım",   "general", 3, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Canozan",        "Ağlama Ben Ağlarım",        200, false),
                new SearchCase("BULGU_072", "ağlama ben",           "general", 3, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Canozan",        "Ağlama Ben Ağlarım",        200, false),
                new SearchCase("BULGU_074", "erik",                 "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Erik Dalı",                 200, false),
                new SearchCase("BULGU_077", "karakedi",             "general", 3, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Melis Fis",      "Kara Kedi",                 200, false),
                new SearchCase("BULGU_080", "şikayetim var",        "general", 3, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Şikayetim Var",             200, false),
                new SearchCase("BULGU_081", "bunca yıl",            "general", 3, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Dedüblüman",     "Bunca Yıl",                 200, false),
                new SearchCase("BULGU_082", "düldül",               "general", 3, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Mabel Matiz",    "Düldül",                    200, false),
                new SearchCase("BULGU_086", "perde",                "general", 3, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Poizi",          "Perde",                     200, false),
                new SearchCase("BULGU_092", "sonbahar",             "general", 3, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Era7Capone",     "SONBAHAR",                  200, false),
                new SearchCase("BULGU_093", "acem kızı",            "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Acem Kızı",                 200, false),
                new SearchCase("BULGU_094", "hacel obası",          "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Hacel Obası",               200, false),
                new SearchCase("BULGU_095", "yalan",                "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Yalan",                     200, false),
                new SearchCase("BULGU_096", "bana sor",             "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Ferdi Tayfur",   "Bana Sor",                  200, false),
                new SearchCase("BULGU_097", "rüya manifest",        "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Manifest",       "Rüya",                      200, false),
                new SearchCase("BULGU_098", "rüya",                 "general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK,"Manifest",       "Rüya",                      200, false),
                new SearchCase("BULGU_100", "ara",                  "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Zeynep Bastık",  "Ara",                       200, false),
                new SearchCase("BULGU_101", "14 bahar",             "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Mert Demir",     "14 Bahar",                  200, false),
                new SearchCase("BULGU_103", "ela mana",             "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Ela Mana",                  200, false),
                new SearchCase("BULGU_105", "erik dalı",            "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Erik Dalı",                 200, false),
                new SearchCase("BULGU_106", "elfida",               "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Elfida",                    200, false),
                new SearchCase("BULGU_107", "yazan kalem siyah",    "general", 10, Rule.TOPN_HAS_ARTIST_AND_TRACK,"",               "Yazan Kalem Siyah",         200, false),
                new SearchCase("BULGU_111", "merdo",                "general", 3, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Merdo",                     200, false),
                new SearchCase("BULGU_121", "misket",               "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Misket",                    200, false),
                new SearchCase("BULGU_122", "kara sevda",           "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Kara Sevda",                200, false),
                new SearchCase("BULGU_123", "parla",                "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Parla",                     200, false),
                new SearchCase("BULGU_124", "kırmızı balık",        "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "",               "Kırmızı Balık",             200, false),

                // ─────────────────────────────────────────────────────────────────
                // BÖLÜM 3 — ŞARKI LYRIC / YAZIM HATALI ARAMALAR
                // ─────────────────────────────────────────────────────────────────
                new SearchCase("BULGU_067", "çölüme yağmur oldun",  "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Müslüm Gürses",  "Affet",                     200, false),
                new SearchCase("BULGU_068", "sana hastayım anlasana","general",5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Derya Uluğ",     "Yansıma",                   200, false),
                new SearchCase("BULGU_070", "Dua Lipa Shine",       "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Dua Lipa",       "Shine",                     200, false),
                new SearchCase("BULGU_075", "hadi ya",              "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Melis Kar",      "Yatıya",                    200, false),
                new SearchCase("BULGU_076", "babalar",              "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Blok3",          "PATLAT",                    200, false),
                new SearchCase("BULGU_079", "silemez o beni",       "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Yıldız Tilbe",   "Dizine Dursun",             200, false),
                new SearchCase("BULGU_083", "çetin ceviz şerbetli mayam","general",5,Rule.TOPN_HAS_ARTIST_AND_TRACK,"Melike Şahin", "Canın Beni Çekti",          200, false),
                new SearchCase("BULGU_084", "bir motive",           "general", 5, Rule.TOPN_HAS_ARTIST,           "Motive",         "",                          200, false),

                // ─────────────────────────────────────────────────────────────────
                // BÖLÜM 4 — SANATÇI SIRALAMA SORUNLARI (FIRST_ARTIST_IS)
                // Beklenen sanatçı 1. sırada gelmeli; başka biri geliyor
                // ─────────────────────────────────────────────────────────────────
                new SearchCase("BULGU_008", "mfö",                  "general", 5, Rule.FIRST_ARTIST_IS, "MFÖ",              "", 200, false),
                new SearchCase("BULGU_009", "mfo",                  "general", 5, Rule.FIRST_ARTIST_IS, "MFÖ",              "", 200, false),
                new SearchCase("BULGU_016", "Manifest",             "general", 5, Rule.FIRST_ARTIST_IS, "Manifest",         "", 200, false),
                new SearchCase("BULGU_017", "semicenk",             "general", 5, Rule.FIRST_ARTIST_IS, "Semicenk",         "", 200, false),
                new SearchCase("BULGU_018", "Semicenk",             "general", 5, Rule.FIRST_ARTIST_IS, "Semicenk",         "", 200, false),
                new SearchCase("BULGU_055", "utku akkaya",          "general", 5, Rule.FIRST_ARTIST_IS, "Utku Akkaya",      "", 200, false),
                new SearchCase("BULGU_088", "derya bedavacı",       "general", 5, Rule.FIRST_ARTIST_IS, "Derya Bedavacı",   "", 200, false),
                new SearchCase("BULGU_090", "ceza",                 "general", 5, Rule.FIRST_ARTIST_IS, "Ceza",             "", 200, false),
                new SearchCase("BULGU_091", "Ceza",                 "general", 5, Rule.FIRST_ARTIST_IS, "Ceza",             "", 200, false),
                new SearchCase("BULGU_099", "çakal",                "general", 5, Rule.TOPN_HAS_ARTIST, "çakal",            "", 200, false),
                new SearchCase("BULGU_102", "yaşar",                "general", 5, Rule.FIRST_ARTIST_IS, "Yaşar",            "", 200, false),
                new SearchCase("BULGU_112", "Gökhan Özen",          "general", 5, Rule.FIRST_ARTIST_IS, "Gökhan Özen",      "", 200, false),
                new SearchCase("BULGU_118", "çelik",                "general", 5, Rule.FIRST_ARTIST_IS, "Çelik",            "", 200, false),
                new SearchCase("BULGU_120", "Mustafa Yıldızdoğan",  "general", 5, Rule.FIRST_ARTIST_IS, "Mustafa Yıldızdoğan","",200,false),
                new SearchCase("BULGU_119_B","Haluk Levent",        "general", 5, Rule.FIRST_ARTIST_IS, "Haluk Levent",     "", 200, false),

                // ─────────────────────────────────────────────────────────────────
                // BÖLÜM 5 — SANATÇI TOPN (yazım toleransı / kısaltma aramaları)
                // ─────────────────────────────────────────────────────────────────
                new SearchCase("BULGU_006", "blok",                 "general", 5, Rule.TOPN_HAS_ARTIST, "Blok3",            "", 200, false),
                new SearchCase("BULGU_007", "kusura bakma",         "general", 3, Rule.TOPN_HAS_ARTIST_AND_TRACK,"Blok3",    "Kusura Bakma",              200, false),
                new SearchCase("BULGU_021", "tarkn",                "general", 5, Rule.TOPN_HAS_ARTIST, "Tarkan",           "", 200, false),
                new SearchCase("BULGU_029", "simarik",              "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK,"Tarkan",   "Şımarık",                   200, false),
                new SearchCase("BULGU_030", "şımarık",              "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK,"Tarkan",   "Şımarık",                   200, false),

                // ─────────────────────────────────────────────────────────────────
                // BÖLÜM 6 — PLAYLİST / KATEGORİ ARAMALARI
                // expArtistOrKeyword = playlist adında geçmesi beklenen anahtar kelime
                // ─────────────────────────────────────────────────────────────────
                new SearchCase("BULGU_026", "90 lar",               "general", 10, Rule.TOPN_RELATED_ALBUM, "90",           "", 200, false),
                new SearchCase("BULGU_027", "çocuk",                "general", 10, Rule.TOPN_RELATED_ALBUM, "çocuk",        "", 200, false),
                new SearchCase("BULGU_064", "pop",                  "general", 10, Rule.TOPN_RELATED_ALBUM, "pop",          "", 200, false),
                new SearchCase("BULGU_069", "yabancı",              "general", 10, Rule.TOPN_RELATED_ALBUM, "yabancı",      "", 200, false),
                new SearchCase("BULGU_087", "akustik",              "general", 10, Rule.TOPN_RELATED_ALBUM, "akustik",      "", 200, false),

                // ─────────────────────────────────────────────────────────────────
                // BÖLÜM 7 — AUTO-CORRECT / YAZIM HATALI ARAMA SORUNLARI
                // Autocorrect İngilizce'ye çekiyor; Türkçe içerik gelmiyor.
                // ALLOW_EMPTY_RESULTS → API cevabı gözlemleniyor, ne bulduğu Excel'e yazılıyor.
                // ─────────────────────────────────────────────────────────────────
                new SearchCase("BULGU_073", "arabam",               "general", 5, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("BULGU_078", "doğuştan",             "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK,"Lvbel C5","Doğuştan Beri Haklıyım",    200, false),
                new SearchCase("BULGU_104", "yekten",               "general", 5, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("BULGU_113", "sigara",               "general", 5, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("BULGU_114", "dandini",              "general", 5, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("BULGU_119", "hşdra",                "general", 5, Rule.TOPN_HAS_ARTIST,    "Hidra",        "", 200, true),
                new SearchCase("BULGU_119_C","farzet",              "general", 5, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),

                // ─────────────────────────────────────────────────────────────────
                // BÖLÜM 8 — ÖNERI / CASE SENSİTİVİTY GÖZLEM (ALLOW_EMPTY_RESULTS)
                // Öneri testi API seviyesinde yapılamaz; yalnızca API sonucu gözlemlenir.
                // ─────────────────────────────────────────────────────────────────
                new SearchCase("BULGU_019", "yalnızlığın çaresini", "general", 5, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("BULGU_022", "teo",                  "general", 5, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("BULGU_085", "remix",                "general", 5, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),
                new SearchCase("BULGU_089", "phonk",                "general", 5, Rule.ALLOW_EMPTY_RESULTS, "", "", 200, true),

                // ─────────────────────────────────────────────────────────────────
                // BÖLÜM 9 — CASE SENSITIVITY (aynı kelime büyük/küçük harf → farklı sonuç)
                // ─────────────────────────────────────────────────────────────────
                new SearchCase("BULGU_108", "Mihriban",             "general", 3, Rule.TOPN_HAS_ARTIST_AND_TRACK, "", "Mihriban", 200, false),
                new SearchCase("BULGU_109", "mihriban",             "general", 3, Rule.TOPN_HAS_ARTIST_AND_TRACK, "", "Mihriban", 200, false)

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
        String specificType  = specificTypeFor(sc);
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
    /**
     * Rule'a göre spesifik indeks tipini döndürür (UAT_ prefix'li caseId'ler için).
     * FIRST_ARTIST_IS / TOPN_HAS_ARTIST → "performer" (indeks 10)
     * TOPN_RELATED_PLAYLIST             → "playlist"  (indeks 11)
     * TOPN_HAS_ARTIST_AND_TRACK         → "songs"     (indeks 48)
     * Diğerleri (ALLOW_EMPTY_RESULTS vb.) → null (kıyas yok)
     */
    private static String specificTypeFor(SearchCase sc) {
        if (sc == null) return null;
        return switch (sc.rule()) {
            case FIRST_ARTIST_IS, TOPN_HAS_ARTIST -> "performer";
            case TOPN_RELATED_PLAYLIST             -> "playlist";
            case TOPN_HAS_ARTIST_AND_TRACK         -> "songs";
            default                                -> null;
        };
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
        if (EXCEL_REPORT_ENABLED) ExcelTestReportWriter.write(REPORT_ROWS);
    }
}