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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

/**
 * Barkın DDP-301 — Yazım Toleransı Test Suite (110 case)
 *
 * queries.txt + expected_results.txt dosyalarından case'leri okur.
 * MuudSearchApi + MuudSearchUtils kullanır (MainCases / YazimTolansiCases ile aynı yaklaşım).
 *
 * Kullanım:
 *   mvn test -Dtest=MuudTypoTest
 *   Çıktı: proje kök dizininde TestReport_YYYYMMDD_HHmmss.xlsx
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MuudTypoTest extends TestConfig {

    private static final int TOP_N = 10;
    private static final List<TestResultRow> ROWS = new ArrayList<>();
    private static MuudSearchApi api;

    // =========================================================================
    // CASE TANIMI
    // =========================================================================

    record TypoCase(
            int    no,
            String query,
            String testType,       // "Sanatçı · Yazım Toleransı" gibi
            String logicCategory,  // "Harf Eksikliği" gibi — Excel gruplamada kullanılır
            String kind,           // "artist" | "song" | "artist_or_playlist"
            int    rank,           // beklenen sıra limiti (1 veya 3)
            String field1,         // artist için: artist adı; song için: artist adı
            String field2          // song için: şarkı adı; artist için: ""
    ) {}

    // =========================================================================
    // SETUP / TEARDOWN
    // =========================================================================

    @BeforeAll
    static void init() {
        api = new MuudSearchApi();
        System.out.println("✅ MuudTypoTest başlatıldı — DDP-301 Yazım Toleransı — top-" + TOP_N);
    }

    @AfterAll
    static void writeReport() {
        System.out.printf("%n📋 Toplam %d case işlendi.%n", ROWS.size());
        ExcelTestReportWriter.writeBulgu(ROWS, "reports/muud-typo");
        analyzer.BulguAnalyzer.annotateLatest("reports/muud-typo");   // NOK satirlarini otomatik analiz eder
    }

    // =========================================================================
    // CASE YÜKLEME — queries.txt + expected_results.txt
    // =========================================================================

    static Stream<TypoCase> cases() {
        // expected_results.txt → Map<no, String[]> {kind, rank, field1, field2}
        Map<Integer, String[]> expected = loadExpected();

        List<TypoCase> list = new ArrayList<>();

        try (InputStream is = MuudTypoTest.class.getClassLoader()
                .getResourceAsStream("typo/queries.txt");
             BufferedReader br = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; } // başlık satırını atla
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\|", -1);
                if (parts.length < 4) continue;

                int    no            = Integer.parseInt(parts[0].trim());
                String query         = parts[1].trim();
                String testType      = parts[2].trim();
                String logicCategory = parts[3].trim();

                String[] exp = expected.get(no);
                if (exp == null) {
                    System.err.println("⚠ Beklenen sonuç yok: no=" + no);
                    continue;
                }

                String kind   = exp[0];
                int    rank   = Integer.parseInt(exp[1]);
                String field1 = exp[2];
                String field2 = exp.length > 3 ? exp[3] : "";

                list.add(new TypoCase(no, query, testType, logicCategory, kind, rank, field1, field2));
            }

        } catch (Exception e) {
            System.err.println("⚠ queries.txt okuma hatası: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("📂 Toplam " + list.size() + " typo case yüklendi.");
        return list.stream();
    }

    private static Map<Integer, String[]> loadExpected() {
        Map<Integer, String[]> map = new LinkedHashMap<>();

        try (InputStream is = MuudTypoTest.class.getClassLoader()
                .getResourceAsStream("typo/expected_results.txt");
             BufferedReader br = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; }
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\|", -1);
                if (parts.length < 4) continue;

                int    no     = Integer.parseInt(parts[0].trim());
                String kind   = parts[1].trim();
                String rank   = parts[2].trim();
                String field1 = parts[3].trim();
                String field2 = parts.length > 4 ? parts[4].trim() : "";

                map.put(no, new String[]{kind, rank, field1, field2});
            }

        } catch (Exception e) {
            System.err.println("⚠ expected_results.txt okuma hatası: " + e.getMessage());
            e.printStackTrace();
        }

        return map;
    }

    // =========================================================================
    // TEST — ASLA FAIL ETMEZ, tüm sonuçlar Excel'e yazılır
    // =========================================================================

    @ParameterizedTest(name = "[{0}] \"{1}\"")
    @MethodSource("cases")
    @Order(1)
    void run(TypoCase tc) {
        String[] result = new String[]{"(beklenen tanımlanmamış)", "NOK", "API hatası"};

        try {
            Response  res = api.search(tc.query(), "active-indices", TOP_N);
            JsonPath  jp  = res.jsonPath();
            result = evaluate(tc, jp);
        } catch (Exception e) {
            System.err.printf("⚠️  API hatası [%d '%s']: %s%n",
                    tc.no(), tc.query(), e.getMessage());
            result[2] = "API hatası: " + e.getMessage();
        }

        System.out.printf("[%s] %3d | %-25s | %-35s | %s%n",
                result[1], tc.no(), tc.logicCategory(),
                "\"" + tc.query() + "\"", result[2].replace("\n", " "));

        ROWS.add(new TestResultRow(
                String.format("TYPO_%03d", tc.no()),
                "\"" + tc.query() + "\" araması yapılır",
                tc.testType() + " — " + tc.logicCategory(),
                result[0],
                tc.logicCategory(),   // Excel'de kategori bazlı gruplamak için
                "active-indices",
                result[1],
                result[2]
        ));
    }

    // =========================================================================
    // DEĞERLENDİRME — kind bazlı
    // =========================================================================

    private String[] evaluate(TypoCase tc, JsonPath jp) {
        List<Object> list = MuudSearchUtils.resultsList(jp);
        String       base = MuudSearchUtils.getBasePath(jp);

        if (list.isEmpty()) {
            return new String[]{
                    buildExpected(tc),
                    "NOK",
                    "API boş sonuç döndürdü."};
        }

        return switch (tc.kind()) {
            case "artist"             -> evalArtist(tc, jp, base);
            case "song"               -> evalSong(tc, jp, base);
            case "artist_or_playlist" -> evalArtistOrPlaylist(tc, jp, base);
            default -> new String[]{buildExpected(tc), "NOK", "Bilinmeyen kind: " + tc.kind()};
        };
    }

    /** Performer kind — ilk `rank` içinde artist adı eşleşmeli */
    private String[] evalArtist(TypoCase tc, JsonPath jp, String base) {
        int    n        = tc.rank();
        String expected = n == 1
                ? "1. sırada '" + tc.field1() + "' sanatçısı gelmeli."
                : "İlk " + n + " içinde '" + tc.field1() + "' sanatçısı gelmeli.";

        int pos = MuudSearchUtils.findArtistIndex(jp, n, tc.field1());

        if (pos != -1) {
            String found = MuudSearchUtils.getPerformerName(jp, base + "[" + pos + "].data");
            return new String[]{expected, "OK",
                    "Başarılı — " + (pos + 1) + ". sırada '" + found + "' geldi.\n"
                    + top5Desc(jp, base)};
        }

        int    fullPos = MuudSearchUtils.findArtistIndex(jp, TOP_N, tc.field1());
        String where   = fullPos == -1
                ? "top-" + TOP_N + "'da da bulunamadı"
                : (fullPos + 1) + ". sırada bulundu";
        return new String[]{expected, "NOK",
                "İlk " + n + "'da yok — " + tc.field1() + ": " + where + ".\n"
                        + top5Desc(jp, base)};
    }

    /** Song kind — ilk `rank` içinde artist+şarkı eşleşmeli */
    private String[] evalSong(TypoCase tc, JsonPath jp, String base) {
        int    n        = tc.rank();
        String expStr   = tc.field1().isEmpty()
                ? "'" + tc.field2() + "'"
                : "'" + tc.field1() + "' – '" + tc.field2() + "'";
        String expected = "Top-" + n + " içinde " + expStr + " eşleşmesi bulunmalı.";

        int idx = MuudSearchUtils.findArtistAndTrackIndex(jp, n, tc.field1(), tc.field2());

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

        int    fullIdx = MuudSearchUtils.findArtistAndTrackIndex(jp, TOP_N, tc.field1(), tc.field2());
        String where   = fullIdx == -1
                ? "top-" + TOP_N + "'da da bulunamadı"
                : (fullIdx + 1) + ". sırada bulundu";
        return new String[]{expected, "NOK",
                "Top-" + n + "'da yok — " + expStr + ": " + where + ".\n"
                        + top5Desc(jp, base)};
    }

    /**
     * artist_or_playlist kind — ilk `rank` içinde ya performer adı ya da playlist adı
     * field1 (artist adı) içermeli.
     */
    private String[] evalArtistOrPlaylist(TypoCase tc, JsonPath jp, String base) {
        int    n        = tc.rank();
        String artist   = tc.field1();
        String expected = "Top-" + n + " içinde '" + artist + "' sanatçısı veya playlist'i bulunmalı.";

        // Performer kontrolü
        int artistPos = MuudSearchUtils.findArtistIndex(jp, n, artist);
        if (artistPos != -1) {
            String found = MuudSearchUtils.getPerformerName(jp, base + "[" + artistPos + "].data");
            return new String[]{expected, "OK",
                    "Başarılı — " + (artistPos + 1) + ". sırada sanatçı: '" + found + "'.\n"
                    + top5Desc(jp, base)};
        }

        // Playlist kontrolü — playlist adı artist ismini içeriyor mu?
        for (int i = 0; i < n; i++) {
            String pl = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.playlistName"));
            if (!pl.isEmpty() && MuudSearchUtils.containsTRInsensitive(pl, artist)) {
                return new String[]{expected, "OK",
                        "Başarılı — " + (i + 1) + ". sırada playlist: '" + pl + "'.\n"
                        + top5Desc(jp, base)};
            }
        }

        // Top-N'de de kontrol et (detay için)
        int artistFull   = MuudSearchUtils.findArtistIndex(jp, TOP_N, artist);
        int playlistFull = -1;
        for (int i = 0; i < TOP_N; i++) {
            String pl = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.playlistName"));
            if (!pl.isEmpty() && MuudSearchUtils.containsTRInsensitive(pl, artist)) {
                playlistFull = i;
                break;
            }
        }

        String whereArtist   = artistFull   == -1 ? "yok" : (artistFull   + 1) + ". sırada";
        String wherePlaylist = playlistFull  == -1 ? "yok" : (playlistFull + 1) + ". sırada";
        return new String[]{expected, "NOK",
                "Top-" + n + "'da yok — Sanatçı: " + whereArtist
                + " | Playlist: " + wherePlaylist + ".\n"
                + top5Desc(jp, base)};
    }

    private String buildExpected(TypoCase tc) {
        return switch (tc.kind()) {
            case "artist" -> tc.rank() == 1
                    ? "1. sırada '" + tc.field1() + "' sanatçısı gelmeli."
                    : "İlk " + tc.rank() + " içinde '" + tc.field1() + "' sanatçısı gelmeli.";
            case "song" -> "Top-" + tc.rank() + " içinde '"
                    + tc.field1() + "' – '" + tc.field2() + "' eşleşmesi bulunmalı.";
            case "artist_or_playlist" -> "Top-" + tc.rank() + " içinde '"
                    + tc.field1() + "' sanatçısı veya playlist'i bulunmalı.";
            default -> "(tanımlanmamış)";
        };
    }

    // =========================================================================
    // LOG YARDIMCILARI
    // =========================================================================

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

        return kindPrefix + label + " | score=" + score;
    }

    private String top5Desc(JsonPath jp, String base) {
        StringBuilder sb = new StringBuilder("İlk 5 sonuç:");
        for (int i = 0; i < 5; i++) {
            sb.append("\n  ").append(i + 1).append(". ").append(itemDesc(jp, base, i));
        }
        return sb.toString();
    }
}
