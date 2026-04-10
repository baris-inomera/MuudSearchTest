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
public class MuudSearchApiKapsayiciUATTest extends TestConfig {

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

                new SearchCase("UAT_001", "u2", "general", 5, Rule.FIRST_ARTIST_IS, "u2", "", 200, false),
                new SearchCase("UAT_002", "84", "general", 5, Rule.FIRST_ARTIST_IS, "seksendört", "", 200, false),
                new SearchCase("UAT_003", "goksel", "general", 5, Rule.FIRST_ARTIST_IS, "Göksel", "", 200, false),
                new SearchCase("UAT_004", "pop", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "pop", "", 200, false),
                new SearchCase("UAT_005", "90", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "90", "", 200, false),
                new SearchCase("UAT_006", "90lar", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "90", "", 200, false),
                new SearchCase("UAT_007", "90'lar", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "90", "", 200, false),
                new SearchCase("UAT_008", "90s", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "90", "", 200, false),
                new SearchCase("UAT_009", "90 lar", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "90", "", 200, false),
                new SearchCase("UAT_010", "edis", "general", 5, Rule.FIRST_ARTIST_IS, "Edis", "", 200, false),
                new SearchCase("UAT_011", "nasır", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Melike Şahin", "Nasır", 200, false),
                new SearchCase("UAT_012", "kaybolurum gülüşünde", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "İkilem", "Kaybolurum Gülüşünde", 200, false),
                new SearchCase("UAT_013", "uzi", "general", 5, Rule.FIRST_ARTIST_IS, "Uzi", "", 200, false),
                new SearchCase("UAT_014", "can ozan", "general", 5, Rule.FIRST_ARTIST_IS, "Canozan", "", 200, false),
                new SearchCase("UAT_015", "yandım ah", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Sakiler", "Yalanı Bırak", 200, false),
                new SearchCase("UAT_016", "aleyna", "general", 5, Rule.FIRST_ARTIST_IS, "Aleyna Tilki", "", 200, false),
                new SearchCase("UAT_017", "sus", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Ceza", "Suspus", 200, false),
                new SearchCase("UAT_018", "pus", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Ceza", "Suspus", 200, false),
                new SearchCase("UAT_019", "bodrum", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Yüzyüzeyken Konuşuruz", "Bodrum", 200, false),
                new SearchCase("UAT_020", "ezel", "general", 5, Rule.FIRST_ARTIST_IS, "Ezhel", "", 200, false),
                new SearchCase("UAT_021", "mfö", "general", 5, Rule.FIRST_ARTIST_IS, "MFÖ", "", 200, false),
                new SearchCase("UAT_022", "bak ben yara gibiyim", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Emir Can İğrek", "Nalan", 200, false),
                new SearchCase("UAT_023", "çölüme yağmur oldun", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Müslüm Gürses", "Affet", 200, false),
                new SearchCase("UAT_024", "zaten aşklar hep yalan dolan", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Yıldız Tilbe", "Sana Değer", 200, false),
                new SearchCase("UAT_025", "ferdi", "general", 5, Rule.FIRST_ARTIST_IS, "Ferdi Tayfur", "", 200, false),
                new SearchCase("UAT_026", "mabel", "general", 5, Rule.FIRST_ARTIST_IS, "Mabel Matiz", "", 200, false),
                new SearchCase("UAT_027", "yıldız", "general", 5, Rule.FIRST_ARTIST_IS, "Yıldız Tilbe", "", 200, false),
                new SearchCase("UAT_028", "azer", "general", 5, Rule.FIRST_ARTIST_IS, "Azer Bülbül", "", 200, false),
                new SearchCase("UAT_029", "arabesk", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "arabesk", "", 200, false),
                new SearchCase("UAT_030", "sıla", "general", 5, Rule.FIRST_ARTIST_IS, "Sıla", "", 200, false),
                new SearchCase("UAT_031", "ilahi", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "ilahi", "", 200, false),
                new SearchCase("UAT_032", "blok", "general", 5, Rule.FIRST_ARTIST_IS, "BLOK3", "", 200, false),
                new SearchCase("UAT_033", "serdar", "general", 5, Rule.FIRST_ARTIST_IS, "Serdar Ortaç", "", 200, false),
                new SearchCase("UAT_034", "sana hastayım anlasana", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Derya Uluğ", "Yansıma", 200, false),
                new SearchCase("UAT_035", "reymen", "general", 5, Rule.FIRST_ARTIST_IS, "Reynmen", "", 200, false),
                new SearchCase("UAT_036", "cengiz", "general", 5, Rule.FIRST_ARTIST_IS, "Cengiz Kurtoğlu", "", 200, false),
                new SearchCase("UAT_037", "karadeniz", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "karadeniz", "", 200, false),
                new SearchCase("UAT_038", "neşet", "general", 5, Rule.FIRST_ARTIST_IS, "Neşet Ertaş", "", 200, false),
                new SearchCase("UAT_039", "melike", "general", 5, Rule.FIRST_ARTIST_IS, "Melike Şahin", "", 200, false),
                new SearchCase("UAT_040", "halay", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "halay", "", 200, false),
                new SearchCase("UAT_041", "çakal", "general", 5, Rule.FIRST_ARTIST_IS, "cakal", "", 200, false),
                new SearchCase("UAT_042", "orhan", "general", 5, Rule.FIRST_ARTIST_IS, "Orhan Gencebay", "", 200, false),
                new SearchCase("UAT_043", "yabancı", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "yabancı", "", 200, false),
                new SearchCase("UAT_044", "roman", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "roman", "", 200, false),
                new SearchCase("UAT_045", "eminem", "general", 5, Rule.FIRST_ARTIST_IS, "Eminem", "", 200, false),
                new SearchCase("UAT_046", "sago", "general", 5, Rule.FIRST_ARTIST_IS, "Sagopa Kajmer", "", 200, false),
                new SearchCase("UAT_047", "emircan", "general", 5, Rule.FIRST_ARTIST_IS, "Emir Can İğrek", "", 200, false),
                new SearchCase("UAT_048", "çocuk", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "çocuk", "", 200, false),
                new SearchCase("UAT_049", "soner", "general", 5, Rule.FIRST_ARTIST_IS, "Soner Sarıkabadayı", "", 200, false),
                new SearchCase("UAT_050", "ati", "general", 5, Rule.FIRST_ARTIST_IS, "Ati242", "", 200, false),
                new SearchCase("UAT_051", "güneş", "general", 5, Rule.FIRST_ARTIST_IS, "Güneş", "", 200, false),
                new SearchCase("UAT_052", "dua lipa", "general", 5, Rule.FIRST_ARTIST_IS, "Dua Lipa", "", 200, false),
                new SearchCase("UAT_053", "norm", "general", 5, Rule.FIRST_ARTIST_IS, "Norm Ender", "", 200, false),
                new SearchCase("UAT_054", "illede sen", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Azer Bülbül", "İlle De Sen", 200, false),
                new SearchCase("UAT_055", "sibel", "general", 5, Rule.FIRST_ARTIST_IS, "Sibel Can", "", 200, false),
                new SearchCase("UAT_056", "irem", "general", 5, Rule.FIRST_ARTIST_IS, "İrem Derici", "", 200, false),
                new SearchCase("UAT_057", "ağlama ben ağlarım", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Canozan", "Ağlama ben ağlarım", 200, false),
                new SearchCase("UAT_058", "musa", "general", 5, Rule.FIRST_ARTIST_IS, "Musa Eroğlu", "", 200, false),
                new SearchCase("UAT_059", "mero", "general", 5, Rule.FIRST_ARTIST_IS, "Mero", "", 200, false),
                new SearchCase("UAT_060", "bana sor", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Ferdi Tayfur", "Bana Sor", 200, false),
                new SearchCase("UAT_061", "murda", "general", 5, Rule.FIRST_ARTIST_IS, "Murda", "", 200, false),
                new SearchCase("UAT_062", "kurtuluş", "general", 5, Rule.FIRST_ARTIST_IS, "Kurtuluş Kuş", "", 200, false),
                new SearchCase("UAT_063", "cash", "general", 5, Rule.FIRST_ARTIST_IS, "Cash Flow", "", 200, false),
                new SearchCase("UAT_064", "inna", "general", 5, Rule.FIRST_ARTIST_IS, "Inna", "", 200, false),
                new SearchCase("UAT_065", "oyun hava", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "oyun hava", "", 200, false),
                new SearchCase("UAT_066", "ağlama ben", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Canozan", "Ağlama ben ağlarım", 200, false),
                new SearchCase("UAT_067", "adele", "general", 5, Rule.FIRST_ARTIST_IS, "Adele", "", 200, false),
                new SearchCase("UAT_068", "arabam", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Sefo", "Araba", 200, false), // DACIA da bekleniyordu ancak Sefo - Araba olarak bırakıldı,
                new SearchCase("UAT_069", "spor", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "spor", "", 200, false),
                new SearchCase("UAT_070", "mor ve ötesi", "general", 5, Rule.FIRST_ARTIST_IS, "mor ve ötesi", "", 200, false),
                new SearchCase("UAT_071", "reyn", "general", 5, Rule.FIRST_ARTIST_IS, "Reynmen", "", 200, false),
                new SearchCase("UAT_072", "mahsun", "general", 5, Rule.FIRST_ARTIST_IS, "Mahsun Kırmızıgül", "", 200, false),
                new SearchCase("UAT_073", "klasik", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "klasik", "", 200, false),
                new SearchCase("UAT_074", "emircan iğrek", "general", 5, Rule.FIRST_ARTIST_IS, "Emir Can İğrek", "", 200, false),
                new SearchCase("UAT_075", "patron", "general", 5, Rule.FIRST_ARTIST_IS, "Patron", "", 200, false),
                new SearchCase("UAT_076", "tefo", "general", 5, Rule.FIRST_ARTIST_IS, "Tefo", "", 200, false),
                new SearchCase("UAT_077", "doğuş", "general", 5, Rule.FIRST_ARTIST_IS, "Doğuş", "", 200, false),
                new SearchCase("UAT_078", "funda", "general", 5, Rule.FIRST_ARTIST_IS, "Funda Arar", "", 200, false),
                new SearchCase("UAT_079", "ankara", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "ankara", "", 200, false),
                new SearchCase("UAT_080", "sura", "general", 5, Rule.FIRST_ARTIST_IS, "Sura İskenderli", "", 200, false),
                new SearchCase("UAT_081", "rafet", "general", 5, Rule.FIRST_ARTIST_IS, "Rafet El Roman", "", 200, false),
                new SearchCase("UAT_082", "akustik", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "akustik", "", 200, false),
                new SearchCase("UAT_083", "çocuk şarkıları", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "çocuk", "", 200, false),
                new SearchCase("UAT_084", "ben fero", "general", 5, Rule.FIRST_ARTIST_IS, "Ben Fero", "", 200, false),
                new SearchCase("UAT_085", "haluk", "general", 5, Rule.FIRST_ARTIST_IS, "Haluk Levent", "", 200, false),
                new SearchCase("UAT_086", "inji", "general", 5, Rule.FIRST_ARTIST_IS, "INJI", "", 200, false),
                new SearchCase("UAT_087", "rihanna", "general", 5, Rule.FIRST_ARTIST_IS, "Rihanna", "", 200, false),
                new SearchCase("UAT_088", "lvbel", "general", 5, Rule.FIRST_ARTIST_IS, "Lvbel C5", "", 200, false),
                new SearchCase("UAT_089", "mavi", "general", 5, Rule.FIRST_ARTIST_IS, "Mavi", "", 200, false),
                new SearchCase("UAT_090", "velet", "general", 5, Rule.FIRST_ARTIST_IS, "Velet", "", 200, false),
                new SearchCase("UAT_091", "adamlar", "general", 5, Rule.FIRST_ARTIST_IS, "Adamlar", "", 200, false),
                new SearchCase("UAT_092", "zerrin", "general", 5, Rule.FIRST_ARTIST_IS, "Zerrin Özer", "", 200, false),
                new SearchCase("UAT_093", "selda", "general", 5, Rule.FIRST_ARTIST_IS, "Selda Bağcan", "", 200, false),
                new SearchCase("UAT_094", "bilal", "general", 5, Rule.FIRST_ARTIST_IS, "Bilal Sonses", "", 200, false),
                new SearchCase("UAT_095", "erik", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "", "Erik Dalı", 200, false),
                new SearchCase("UAT_096", "gülden", "general", 5, Rule.FIRST_ARTIST_IS, "Gülden Karaböcek", "", 200, false),
                new SearchCase("UAT_097", "ibrahim tat", "general", 5, Rule.FIRST_ARTIST_IS, "İbrahim Tatlıses", "", 200, false),
                new SearchCase("UAT_098", "engin", "general", 5, Rule.FIRST_ARTIST_IS, "Engin Nurşani", "", 200, false),
                new SearchCase("UAT_099", "şebnem", "general", 5, Rule.FIRST_ARTIST_IS, "Şebnem Ferah", "", 200, false),
                new SearchCase("UAT_100", "ayaz", "general", 5, Rule.FIRST_ARTIST_IS, "Ayaz Erdoğan", "", 200, false),
                new SearchCase("UAT_101", "ajda", "general", 5, Rule.FIRST_ARTIST_IS, "Ajda Pekkan", "", 200, false),
                new SearchCase("UAT_102", "blackpink", "general", 5, Rule.FIRST_ARTIST_IS, "BLACKPINK", "", 200, false),
                new SearchCase("UAT_103", "no1", "general", 5, Rule.FIRST_ARTIST_IS, "No.1", "", 200, false),
                new SearchCase("UAT_104", "sia", "general", 5, Rule.FIRST_ARTIST_IS, "Sia", "", 200, false),
                new SearchCase("UAT_105", "izel", "general", 5, Rule.FIRST_ARTIST_IS, "İzel", "", 200, false),
                new SearchCase("UAT_106", "türkçe", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "türkçe", "", 200, false),
                new SearchCase("UAT_107", "aynur", "general", 5, Rule.FIRST_ARTIST_IS, "Aynur Aydın", "", 200, false),
                new SearchCase("UAT_108", "80", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "80", "", 200, false),
                new SearchCase("UAT_109", "hayko", "general", 5, Rule.FIRST_ARTIST_IS, "Hayko Cepkin", "", 200, false),
                new SearchCase("UAT_110", "shakira", "general", 5, Rule.FIRST_ARTIST_IS, "Shakira", "", 200, false),
                new SearchCase("UAT_111", "halo", "general", 5, Rule.FIRST_ARTIST_IS, "Halodayı", "", 200, false),
                new SearchCase("UAT_112", "50", "general", 5, Rule.FIRST_ARTIST_IS, "50 Cent", "", 200, false),
                new SearchCase("UAT_113", "koray", "general", 5, Rule.FIRST_ARTIST_IS, "Koray Avcı", "", 200, false),
                new SearchCase("UAT_114", "ümit", "general", 5, Rule.FIRST_ARTIST_IS, "Ümit Besen", "", 200, false),
                new SearchCase("UAT_115", "elif buse", "general", 5, Rule.FIRST_ARTIST_IS, "Elif Buse Doğan", "", 200, false),
                new SearchCase("UAT_116", "rock", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "rock", "", 200, false),
                new SearchCase("UAT_117", "hadi ya", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Melis Kar", "Yatıya", 200, false),
                new SearchCase("UAT_118", "hejan", "general", 5, Rule.FIRST_ARTIST_IS, "Heijan", "", 200, false),
                new SearchCase("UAT_119", "babalar", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Blok3", "PATLAT", 200, false),
                new SearchCase("UAT_120", "no 1", "general", 5, Rule.FIRST_ARTIST_IS, "No.1", "", 200, false),
                new SearchCase("UAT_121", "dans", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "dans", "", 200, false),
                new SearchCase("UAT_122", "türk sanat", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "türk sanat", "", 200, false),
                new SearchCase("UAT_123", "özcan", "general", 5, Rule.FIRST_ARTIST_IS, "Özcan Deniz", "", 200, false),
                new SearchCase("UAT_124", "karakedi", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Melis Fis", "Kara Kedi", 200, false),
                new SearchCase("UAT_125", "deha", "general", 5, Rule.FIRST_ARTIST_IS, "DEHA INC", "", 200, false),
                new SearchCase("UAT_126", "gözlerime bak", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Mert Demir", "Gözlerime Bak", 200, false),
                new SearchCase("UAT_127", "semicek", "general", 5, Rule.FIRST_ARTIST_IS, "Semicenk", "", 200, false),
                new SearchCase("UAT_128", "sarışınlar", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Derya Uluğ", "Esmerin Adı Oya", 200, false),
                new SearchCase("UAT_129", "taylor", "general", 5, Rule.FIRST_ARTIST_IS, "Taylor Swift", "", 200, false),
                new SearchCase("UAT_130", "beyonce", "general", 5, Rule.FIRST_ARTIST_IS, "Beyoncé", "", 200, false),
                new SearchCase("UAT_131", "emre gel", "general", 5, Rule.FIRST_ARTIST_IS, "Emre Fel", "", 200, false),
                new SearchCase("UAT_132", "doğuştan", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Lvbel C5", "Doğuştan Beri Haklıyım", 200, false),
                new SearchCase("UAT_133", "sibelcan", "general", 5, Rule.FIRST_ARTIST_IS, "Sibel Can", "", 200, false),
                new SearchCase("UAT_134", "meditasyon", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "meditasyon", "", 200, false),
                new SearchCase("UAT_135", "kofn", "general", 5, Rule.FIRST_ARTIST_IS, "KÖFN", "", 200, false),
                new SearchCase("UAT_136", "silemez o beni", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Yıldız Tilbe", "Dizine Dursun", 200, false),
                new SearchCase("UAT_137", "babalar sözünü tutar", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Blok3", "PATLAT", 200, false),
                new SearchCase("UAT_138", "90 lar pop", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "90'lar", "", 200, false),
                new SearchCase("UAT_139", "madonna", "general", 5, Rule.FIRST_ARTIST_IS, "Madonna", "", 200, false),
                new SearchCase("UAT_140", "ramazan", "general", 5, Rule.TOPN_RELATED_PLAYLIST, "ramazan", "", 200, false),
                new SearchCase("UAT_141", "kalbimin sahibine", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "İrem Derici", "Kalbimin Tek Sahibine", 200, false),
                new SearchCase("UAT_142", "sertap", "general", 5, Rule.FIRST_ARTIST_IS, "Sertab Erener", "", 200, false),
                new SearchCase("UAT_143", "çok geç şmdi", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Edis", "Yalan", 200, false),
                new SearchCase("UAT_144", "affet bu gece istedim ölmek", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Model", "Pembe Mezarlık", 200, false),
                new SearchCase("UAT_145", "lacivert eceler", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Ferhat Göçer", "Lacivert Geceler", 200, false),
                new SearchCase("UAT_146", "mr ve ötei", "general", 5, Rule.FIRST_ARTIST_IS, "Mor ve Ötesi", "", 200, false),
                new SearchCase("UAT_147", "dolu kadhi tut", "general", 5, Rule.FIRST_ARTIST_IS, "Dolu Kadehi Ters Tut", "", 200, false),
                new SearchCase("UAT_148", "güldün ne güzel", "general", 5, Rule.TOPN_HAS_ARTIST_AND_TRACK, "Pinhani", "Ne Güzel Güldün", 200, false),
                new SearchCase("UAT_149", "dktt", "general", 5, Rule.FIRST_ARTIST_IS, "Dolu Kadehi Ters Tut", "", 200, false)

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