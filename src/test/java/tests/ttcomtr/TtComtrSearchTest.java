package tests.ttcomtr;

import client.TtComtrSearchApi;
import config.TestConfig;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import report.ExcelTestReportWriter;
import report.TestResultRow;
import util.MuudSearchUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * TT Comtr — UAT Arama Sonucu Testi (214 case)
 *
 * Excel UAT dokümanındaki 214 case'i otomatik koşturur.
 * Her sorgu için: ilk N sonuçtan herhangi birinin title veya URL'i keyword'ü içeriyor mu?
 *
 * Kaynak: ttcomtr/uat_search_cases.csv
 *   no|query|keyword|rank
 *
 * Kullanım:
 *   mvn test -Dtest=TtComtrSearchTest
 *   Test ortamı için: mvn test -Dtest=TtComtrSearchTest -DttSearchBaseUrl=https://smartsearch-test.turktelekom.com.tr
 *   Çıktı: proje kök dizininde TestReport_YYYYMMDD_HHmmss.xlsx
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TtComtrSearchTest extends TestConfig {

    private static final int FETCH_ROWS = 10;  // API'den kaç sonuç çekileceği
    private static final List<TestResultRow> ROWS = new ArrayList<>();
    private static TtComtrSearchApi api;

    // =========================================================================
    // CASE TANIMI
    // =========================================================================

    record SearchCase(int no, String query, String keyword, int rank) {}

    // =========================================================================
    // SETUP / TEARDOWN
    // =========================================================================

    @BeforeAll
    static void init() {
        api = new TtComtrSearchApi();
        System.out.println("✅ TtComtrSearchTest başlatıldı — " + TtComtrSearchApi.BASE_URL);
    }

    @AfterAll
    static void writeReport() {
        System.out.printf("%n📋 Toplam %d case işlendi.%n", ROWS.size());
        ExcelTestReportWriter.writeBulgu(ROWS, "reports/ttcomtr");
    }

    // =========================================================================
    // CASE YÜKLEME — ttcomtr/uat_search_cases.csv
    // =========================================================================

    static Stream<SearchCase> cases() {
        List<SearchCase> list = new ArrayList<>();

        try (InputStream is = TtComtrSearchTest.class.getClassLoader()
                .getResourceAsStream("ttcomtr/uat_search_cases.csv");
             BufferedReader br = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; } // başlık satırını atla
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] p = line.split("\\|", -1);
                if (p.length < 4) continue;

                int    no      = Integer.parseInt(p[0].trim());
                String query   = p[1].trim();
                String keyword = p[2].trim();
                int    rank    = Integer.parseInt(p[3].trim());

                list.add(new SearchCase(no, query, keyword, rank));
            }

        } catch (Exception e) {
            System.err.println("⚠ uat_search_cases.csv okuma hatası: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("📂 Toplam " + list.size() + " UAT case yüklendi.");
        return list.stream();
    }

    // =========================================================================
    // TEST — ASLA FAIL ETMEZ, tüm sonuçlar Excel'e yazılır
    // =========================================================================

    @ParameterizedTest(name = "[{0}] \"{1}\"")
    @MethodSource("cases")
    @Order(1)
    void run(SearchCase sc) {
        String expected = "İlk " + sc.rank() + " sonuçtan en az biri \""
                + sc.keyword() + "\" içermeli (title veya URL).";
        String[] result = new String[]{expected, "NOK", "API hatası"};

        try {
            Response res = api.searchAll(sc.query(), 0, FETCH_ROWS);
            JsonPath jp  = res.jsonPath();
            result = evaluate(sc, jp, expected);
        } catch (Exception e) {
            System.err.printf("⚠️  API hatası [%d '%s']: %s%n",
                    sc.no(), sc.query(), e.getMessage());
            result[2] = "API hatası: " + e.getMessage();
        }

        System.out.printf("[%s] %3d | %-35s → %s%n",
                result[1], sc.no(), "\"" + sc.query() + "\"",
                result[2].split("\n")[0]);

        ROWS.add(new TestResultRow(
                String.format("UAT_%03d", sc.no()),
                "\"" + sc.query() + "\" araması yapılır",
                "Keyword: " + sc.keyword() + " | İlk " + sc.rank() + " sonuçta",
                result[0],
                "TT Comtr Arama",
                "smartsearch",
                result[1],
                result[2]
        ));
    }

    // =========================================================================
    // DEĞERLENDİRME
    // =========================================================================

    private String[] evaluate(SearchCase sc, JsonPath jp, String expected) {
        List<Object> results = jp.getList("result");

        if (results == null || results.isEmpty()) {
            return new String[]{expected, "NOK",
                    "API hiç sonuç döndürmedi (result boş)."};
        }

        int total = results.size();
        int checkN = Math.min(sc.rank(), total);

        // İlk N sonuçta keyword var mı?
        for (int i = 0; i < checkN; i++) {
            String title = safeStr(jp.getString("result[" + i + "].title"));
            String url   = safeStr(jp.getString("result[" + i + "].url"));
            String kind  = safeStr(jp.getString("result[" + i + "].kind"));

            if (MuudSearchUtils.containsTRInsensitive(title, sc.keyword())
                    || MuudSearchUtils.containsTRInsensitive(url, sc.keyword())) {
                return new String[]{expected, "OK",
                        "Başarılı — " + (i + 1) + ". sırada eşleşti: [" + kind + "] " + title + "\n"
                        + topNDesc(jp, Math.min(sc.rank(), total))};
            }
        }

        // Bulunamadı — tam N'de de ara (detay için)
        int fullN = Math.min(FETCH_ROWS, total);
        int foundAt = -1;
        for (int i = checkN; i < fullN; i++) {
            String title = safeStr(jp.getString("result[" + i + "].title"));
            String url   = safeStr(jp.getString("result[" + i + "].url"));
            if (MuudSearchUtils.containsTRInsensitive(title, sc.keyword())
                    || MuudSearchUtils.containsTRInsensitive(url, sc.keyword())) {
                foundAt = i;
                break;
            }
        }

        String where = foundAt == -1
                ? "top-" + FETCH_ROWS + "'da da bulunamadı"
                : (foundAt + 1) + ". sırada bulundu";

        return new String[]{expected, "NOK",
                "İlk " + sc.rank() + "'de \"" + sc.keyword() + "\" yok — " + where + ".\n"
                + topNDesc(jp, Math.min(sc.rank(), total))};
    }

    // =========================================================================
    // YARDIMCILAR
    // =========================================================================

    private static String safeStr(String s) { return s == null ? "" : s.trim(); }

    private String topNDesc(JsonPath jp, int n) {
        StringBuilder sb = new StringBuilder("İlk " + n + " sonuç:");
        for (int i = 0; i < n; i++) {
            String title = safeStr(jp.getString("result[" + i + "].title"));
            String url   = safeStr(jp.getString("result[" + i + "].url"));
            String kind  = safeStr(jp.getString("result[" + i + "].kind"));
            sb.append("\n  ").append(i + 1).append(". [").append(kind).append("] ")
              .append(title.isEmpty() ? "(başlıksız)" : title)
              .append("\n     ").append(url);
        }
        return sb.toString();
    }
}
