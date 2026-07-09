package tests;

import client.MuudSearchApi;
import config.TestConfig;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import report.ExcelTestReportWriter;
import report.TestResultRow;
import util.MuudSearchUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Yazım Toleransı Test Cases
 * Excel dosyasından (yazım_toleransı_test_cases.xlsx) case'leri okur ve koşturur.
 * MainCases'e dokunulmaz — bu class tamamen bağımsızdır.
 *
 * Kullanım:
 *   mvn test -Dtest=YazimTolansiCases
 *   Çıktı: proje kök dizininde TestReport_YYYYMMDD_HHmmss.xlsx
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class YazimTolansiCases extends TestConfig {

    private static final int    TOP_N = 10;
    private static final Locale TR    = Locale.forLanguageTag("tr");

    private static final List<TestResultRow> ROWS = new ArrayList<>();
    private static MuudSearchApi api;

    // ── Excel dosyasının adı — proje kök dizininde olmalı ───────────────────
    private static final String EXCEL_FILE = "yazım_toleransı_test_cases.xlsx";

    // =========================================================================
    // SETUP / TEARDOWN
    // =========================================================================

    @BeforeAll
    static void init() {
        api = new MuudSearchApi();
        System.out.println("✅ YazimTolansiCases başlatıldı — top-" + TOP_N + " değerlendirilecek.");
    }

    @AfterAll
    static void writeReport() {
        System.out.printf("%n📋 Toplam %d case işlendi.%n", ROWS.size());
        ExcelTestReportWriter.writeBulgu(ROWS);
    }

    // =========================================================================
    // CASE TANIMI
    // =========================================================================

    record BulguCase(String caseId, String term, String expArtist, String expTrack,
                     String section, int topN) {
        BulguCase(String caseId, String term, String expArtist, String expTrack, String section) {
            this(caseId, term, expArtist, expTrack, section, 10);
        }
    }

    // =========================================================================
    // EXCEL'DEN CASE OKUMA
    // =========================================================================

    static Stream<BulguCase> cases() {
        List<BulguCase> list = new ArrayList<>();
        File excelFile = new File(System.getProperty("user.dir"), EXCEL_FILE);

        if (!excelFile.exists()) {
            System.err.println("⚠ Excel dosyası bulunamadı: " + excelFile.getAbsolutePath());
            return Stream.empty();
        }

        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);

            for (Row row : sheet) {
                // İlk 3 satırı atla (başlık ve açıklama)
                if (row.getRowNum() < 3) continue;

                Cell noCell       = row.getCell(0);
                Cell stepCell     = row.getCell(1);
                Cell expectedCell = row.getCell(2);
                Cell typeCell     = row.getCell(3);

                if (noCell == null || stepCell == null || expectedCell == null) continue;

                String noStr       = cellStr(noCell);
                String step        = cellStr(stepCell);
                String expectedStr = cellStr(expectedCell);
                String typeStr     = typeCell != null ? cellStr(typeCell) : "";

                if (noStr.isBlank() || step.isBlank()) continue;

                // Sorguyu çıkar: "simarik" araması yapılır → simarik
                String term = extractQuery(step);
                if (term.isBlank()) continue;

                // topN çıkar
                int topN = extractTopN(expectedStr);

                // expArtist ve expTrack çıkar
                String[] artistTrack = extractArtistTrack(expectedStr);
                String expArtist = artistTrack[0];
                String expTrack  = artistTrack[1];

                // Section
                String section = mapSection(typeStr);

                String caseId = "YAZIM_" + String.format("%03d", (int) Double.parseDouble(noStr));

                list.add(new BulguCase(caseId, term, expArtist, expTrack, section, topN));
            }

        } catch (Exception e) {
            System.err.println("⚠ Excel okuma hatası: " + e.getMessage());
            e.printStackTrace();
        }

        // ── Hardcoded ek case'ler ─────────────────────────────────────────────
        list.add(new BulguCase("sena_1", "iyiki doğdun deniz",        "", "iyi ki doğdun deniz",            "Şarkı · Yazım Toleransı", 3));
        list.add(new BulguCase("sena_2", "alara doğum günün",         "", "doğum günün kutlu olsun alara",  "Şarkı · Yazım Toleransı", 3));
        list.add(new BulguCase("sena_3", "kürtçe",                    "", "kürtçe",                         "Şarkı · Yazım Toleransı", 3));
        list.add(new BulguCase("sena_4", "taki seni görene kadar",    "", "ta ki seni görene kadar",         "Şarkı · Yazım Toleransı", 3));
        list.add(new BulguCase("sena_5", "can dostym",                "", "can dostum",                     "Şarkı · Yazım Toleransı", 3));
        list.add(new BulguCase("sena_6", "manisa",                    "", "manisa",                         "Şarkı · Yazım Toleransı", 3));
        list.add(new BulguCase("sena_7", "saygı1",                    "", "saygı 1",                        "Şarkı · Yazım Toleransı", 3));
        list.add(new BulguCase("sena_8", "mebrure",                   "", "mebrure",                        "Şarkı · Yazım Toleransı", 3));
        list.add(new BulguCase("sena_9", "köksal",                    "", "köksal",                         "Şarkı · Yazım Toleransı", 3));

        System.out.println("📂 Toplam " + list.size() + " case yüklendi (Excel + hardcoded).");
        return list.stream();
    }

    // ─── Yardımcılar ─────────────────────────────────────────────────────────

    private static String cellStr(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default      -> "";
        };
    }

    /** "simarik" araması yapılır  →  simarik */
    private static String extractQuery(String step) {
        Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(step);
        return m.find() ? m.group(1).trim() : "";
    }

    /**
     * Top-N / İlk N / 1. sırada çıkar
     * "Top-1 içinde ..."   → 1
     * "Top-3 içinde ..."   → 3
     * "1. sırada ..."      → 1
     * "İlk 3 içinde ..."   → 3
     */
    private static int extractTopN(String expected) {
        Matcher m1 = Pattern.compile("Top-(\\d+)").matcher(expected);
        if (m1.find()) return Integer.parseInt(m1.group(1));
        Matcher m2 = Pattern.compile("(\\d+)\\. sırada").matcher(expected);
        if (m2.find()) return Integer.parseInt(m2.group(1));
        Matcher m3 = Pattern.compile("İlk (\\d+)").matcher(expected);
        if (m3.find()) return Integer.parseInt(m3.group(1));
        return 1;
    }

    /**
     * expArtist ve expTrack çıkar.
     *
     * "Top-1 içinde Tarkan – Şımarık eşleşmesi bulunmalı."
     *    → artist="Tarkan", track="Şımarık"
     *
     * "1. sırada Müslüm Gürses sanatçısı gelmeli."
     *    → artist="Müslüm Gürses", track=""
     *
     * "İlk 3 içinde Sibel Can sanatçısı gelmeli."
     *    → artist="Sibel Can", track=""
     */
    private static String[] extractArtistTrack(String expected) {
        // Şarkı + sanatçı: "... Tarkan – Şımarık eşleşmesi ..."
        Matcher mBoth = Pattern.compile("içinde (.+?)\\s*[–-]\\s*(.+?)\\s*eşleşmesi").matcher(expected);
        if (mBoth.find()) {
            return new String[]{ mBoth.group(1).trim(), mBoth.group(2).trim() };
        }

        // Sadece sanatçı: "... Müslüm Gürses sanatçısı gelmeli."
        Matcher mArtist = Pattern.compile("(?:sırada|içinde)\\s+(.+?)\\s+sanatçısı").matcher(expected);
        if (mArtist.find()) {
            return new String[]{ mArtist.group(1).trim(), "" };
        }

        return new String[]{ "", "" };
    }

    /** Test Tipi → bölüm sabiti */
    private static String mapSection(String typeStr) {
        if (typeStr.contains("Şarkı"))   return "Şarkı · Yazım Toleransı";
        if (typeStr.contains("Sanatçı")) return "Sanatçı · Yazım Toleransı";
        return typeStr;
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
    // KURAL DEĞERLENDİRME — MainCases ile aynı mantık
    // =========================================================================

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
            metrics = "numPlays=" + numPlays
                    + " | performerPopularity=" + perfPop
                    + " | popularity=" + popularity
                    + " | score=" + score;
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
