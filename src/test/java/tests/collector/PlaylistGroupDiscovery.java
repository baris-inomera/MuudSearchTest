package tests.collector;

import client.MuudSearchApi;
import config.TestConfig;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import util.MuudSearchUtils;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * PLAYLIST GROUP DISCOVERY — Geniş tarama
 *
 * 120+ terimi hem /indices/4 (TEK) hem /active-indices (AKT/Tümü) ile tarar.
 * TOP_N=20 — PlaylistGroupTest ile aynı.
 *
 * Çıktı: reports/playlist-group/Discovery_<tarih>_<ortam>.xlsx
 *   Sheet 1 — AKT-A Adayları : TEK:OK + AKT:OK  → grup Tümü'nde de çıkıyor, assert case adayı
 *   Sheet 2 — TEK Adayları   : TEK:OK + AKT:NOK → sadece Listeler sekmesinde çıkıyor
 *   Sheet 3 — Tüm Tarama     : tüm terimler, sıra bilgisiyle
 *
 * PROD'da her şey NOK gelir (eski schema) — UAT'ta çalıştırın.
 * muud_playlist_all_v2 deployment sonrası PROD'da da çalıştırılması önerilir:
 * hangi keyword'lerin production'da grup döndürdüğünü teyit eder ve
 * PlaylistGroupTest case listesini güncellemek için girdi sağlar.
 *
 * Çalıştır:
 *   mvn test -Dtest=PlaylistGroupDiscovery
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PlaylistGroupDiscovery extends TestConfig {

    private static final int    TOP_N           = 20;
    private static final String PLAYLIST_INDEX  = "4";
    private static final Locale TR              = Locale.forLanguageTag("tr");
    private static final DateTimeFormatter TS   = DateTimeFormatter.ofPattern("dd.MM.yyyy_HH.mm");

    private record Result(String term,
                          String tekStatus, String tekGroup, int tekPos,
                          String aktStatus, String aktGroup, int aktPos) {}
    private static final List<Result> RESULTS = new ArrayList<>();
    private static MuudSearchApi api;

    @BeforeAll
    static void init() {
        api = new MuudSearchApi();
        System.out.println("▶ PlaylistGroupDiscovery başladı — " + TOP_N + " sonuç taranıyor.");
    }

    @AfterAll
    static void writeReport() throws Exception {
        String ortam = MuudSearchApi.ortam();
        String fname = "Discovery_" + LocalDateTime.now().format(TS) + "_" + ortam + ".xlsx";
        Path dir = Path.of("reports/playlist-group");
        Files.createDirectories(dir);

        long tekOkAktOk  = RESULTS.stream().filter(r -> "OK".equals(r.tekStatus()) && "OK".equals(r.aktStatus())).count();
        long tekOkAktNok = RESULTS.stream().filter(r -> "OK".equals(r.tekStatus()) && "NOK".equals(r.aktStatus())).count();
        long tekOk       = RESULTS.stream().filter(r -> "OK".equals(r.tekStatus())).count();

        System.out.printf("%n✅ Teklide playlistGroup bulunan : %d / %d%n", tekOk, RESULTS.size());
        System.out.printf("🎯 TEK:OK + AKT:OK  (AKT-A adayı): %d%n", tekOkAktOk);
        System.out.printf("📋 TEK:OK + AKT:NOK (TEK adayı)  : %d%n%n", tekOkAktNok);

        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(dir.resolve(fname).toFile())) {

            CellStyle hdr    = headerStyle(wb, IndexedColors.DARK_BLUE);
            CellStyle hdrGrn = headerStyle(wb, IndexedColors.DARK_GREEN);
            CellStyle hdrOra = headerStyle(wb, IndexedColors.ORANGE);
            CellStyle ok     = dataStyle(wb, IndexedColors.LIGHT_GREEN);
            CellStyle nok    = dataStyle(wb, IndexedColors.ROSE);
            CellStyle white  = dataStyle(wb, IndexedColors.WHITE);

            // ── Sheet 1: AKT-A Adayları — TEK:OK + AKT:OK ────────────────
            Sheet s1 = wb.createSheet("🎯 AKT-A Adayları");
            Row t1 = s1.createRow(0); t1.setHeightInPoints(26);
            cell(t1, 0, "TEK:OK + AKT:OK — Tümü sekmesinde de grup çıkıyor → assert case adayı", hdrGrn);
            s1.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

            Row h1 = s1.createRow(1);
            cell(h1, 0, "No",           hdr); cell(h1, 1, "Arama Terimi", hdr);
            cell(h1, 2, "TEK Grup",     hdr); cell(h1, 3, "TEK Sıra",     hdr);
            cell(h1, 4, "TEK",          hdr); cell(h1, 5, "AKT Grup",     hdr);
            cell(h1, 6, "AKT Sıra",     hdr); cell(h1, 7, "AKT",          hdr);

            // AKT sırasına göre sırala (en üste çıkan önce)
            List<Result> aktOkList = RESULTS.stream()
                    .filter(r -> "OK".equals(r.tekStatus()) && "OK".equals(r.aktStatus()))
                    .sorted(java.util.Comparator.comparingInt(Result::aktPos))
                    .collect(java.util.stream.Collectors.toList());

            int r1 = 2, no1 = 1;
            for (Result res : aktOkList) {
                Row row = s1.createRow(r1++);
                cell(row, 0, String.valueOf(no1++),       white);
                cell(row, 1, res.term(),                  white);
                cell(row, 2, res.tekGroup(),              white);
                cell(row, 3, res.tekPos() > 0 ? res.tekPos() + ". sıra" : "-", white);
                cell(row, 4, "✅ OK",                     ok);
                cell(row, 5, res.aktGroup(),              white);
                cell(row, 6, res.aktPos() > 0 ? res.aktPos() + ". sıra" : "-", white);
                cell(row, 7, "✅ OK",                     ok);
            }

            int[] w1 = {6, 22, 30, 10, 8, 30, 10, 8};
            for (int c = 0; c < w1.length; c++) s1.setColumnWidth(c, w1[c] * 256);

            // ── Sheet 2: TEK Adayları — TEK:OK + AKT:NOK ─────────────────
            Sheet s2 = wb.createSheet("📋 TEK Adayları");
            Row t2 = s2.createRow(0); t2.setHeightInPoints(26);
            cell(t2, 0, "TEK:OK + AKT:NOK — Sadece Listeler sekmesinde çıkıyor → TEK case adayı", hdrOra);
            s2.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

            Row h2 = s2.createRow(1);
            cell(h2, 0, "No",           hdr); cell(h2, 1, "Arama Terimi", hdr);
            cell(h2, 2, "TEK Grup",     hdr); cell(h2, 3, "TEK Sıra",     hdr);
            cell(h2, 4, "TEK",          hdr); cell(h2, 5, "AKT",          hdr);

            int r2 = 2, no2 = 1;
            for (Result res : RESULTS) {
                if (!"OK".equals(res.tekStatus()) || !"NOK".equals(res.aktStatus())) continue;
                Row row = s2.createRow(r2++);
                cell(row, 0, String.valueOf(no2++),       white);
                cell(row, 1, res.term(),                  white);
                cell(row, 2, res.tekGroup(),              white);
                cell(row, 3, res.tekPos() > 0 ? res.tekPos() + ". sıra" : "-", white);
                cell(row, 4, "✅ OK",                     ok);
                cell(row, 5, "❌ NOK",                    nok);
            }

            int[] w2 = {6, 22, 30, 10, 8, 8};
            for (int c = 0; c < w2.length; c++) s2.setColumnWidth(c, w2[c] * 256);

            // ── Sheet 3: Tüm Tarama ───────────────────────────────────────
            Sheet s3 = wb.createSheet("Tüm Tarama");
            Row t3 = s3.createRow(0); t3.setHeightInPoints(26);
            cell(t3, 0, "TÜM TARAMA — " + RESULTS.size() + " terim | topN=" + TOP_N
                    + " | AKT-A=" + tekOkAktOk + " | TEK=" + tekOkAktNok, hdr);
            s3.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

            Row h3 = s3.createRow(1);
            cell(h3, 0, "No",           hdr); cell(h3, 1, "Arama Terimi", hdr);
            cell(h3, 2, "TEK Sonuç",    hdr); cell(h3, 3, "TEK Grup",     hdr);
            cell(h3, 4, "TEK Sıra",     hdr);
            cell(h3, 5, "AKT Sonuç",    hdr); cell(h3, 6, "AKT Grup",     hdr);
            cell(h3, 7, "AKT Sıra",     hdr);

            int r3 = 2, no3 = 1;
            for (Result res : RESULTS) {
                Row row = s3.createRow(r3++);
                CellStyle tekSt = "OK".equals(res.tekStatus()) ? ok : nok;
                CellStyle aktSt = "OK".equals(res.aktStatus()) ? ok : nok;
                cell(row, 0, String.valueOf(no3++),       white);
                cell(row, 1, res.term(),                  white);
                cell(row, 2, "OK".equals(res.tekStatus()) ? "✅ OK" : "❌ NOK", tekSt);
                cell(row, 3, res.tekGroup(),              white);
                cell(row, 4, res.tekPos() > 0 ? res.tekPos() + ". sıra" : "-", white);
                cell(row, 5, "OK".equals(res.aktStatus()) ? "✅ OK" : "❌ NOK", aktSt);
                cell(row, 6, res.aktGroup(),              white);
                cell(row, 7, res.aktPos() > 0 ? res.aktPos() + ". sıra" : "-", white);
            }

            int[] w3 = {6, 22, 12, 30, 10, 12, 30, 10};
            for (int c = 0; c < w3.length; c++) s3.setColumnWidth(c, w3[c] * 256);

            wb.write(fos);
            System.out.println("📄 Rapor: reports/playlist-group/" + fname);
        }
    }

    // ── 120 arama terimi ─────────────────────────────────────────────────────
    static Stream<String> terms() {
        return Stream.of(
            // Spor & Aktivite
            "spor", "pilates", "yoga", "dans", "koşu", "fitness", "bisiklet",
            "yürüyüş", "egzersiz", "antrenman", "jimnastik", "aerobik", "zumba",

            // Ruh Hali & Atmosfer
            "meditasyon", "rahatlatıcı", "romantik", "hüzünlü", "mutlu", "enerjik",
            "sakin", "nostaljik", "özlem", "neşeli", "melankolik",

            // Tür
            "arabesk", "klasik", "rock", "pop", "türkü", "hiphop", "caz", "metal",
            "elektronik", "indie", "folk", "r&b", "soul", "reggae", "blues",

            // Zaman & Ortam
            "sabah", "gece", "akşam", "öğle", "hafta sonu", "yaz", "kış",
            "seyahat", "tatil", "yol", "araba", "plaj",

            // Aktivite
            "çalışma", "uyku", "ders", "konsantrasyon", "odaklanma",
            "parti", "düğün", "kutlama", "doğum günü",

            // Yemek & Ortam
            "yemek", "kahvaltı", "akşam yemeği", "kafede", "restoran",

            // Kültürel & Coğrafi
            "karadeniz", "ege", "akdeniz", "istanbul", "anadolu",
            "halay", "zeybek", "horon", "bar",

            // Dönem
            "90lar", "80ler", "2000ler", "retro",

            // Tema
            "sinema", "film müzikleri", "dizi", "anime",
            "çocuk", "ninni", "masal",
            "dini", "ilahi", "ramazan",

            // Yabancı & K-pop
            "yabancı", "ingilizce", "kpop", "latin",

            // Türk Pop Sanatçı temaları
            "tarkan", "sezen", "hadise", "mabel",

            // Diğer
            "akustik", "remix", "cover", "enstrümantal", "piano",
            "doğa sesleri", "beyaz gürültü", "fırtına", "yağmur",
            "güçlendirici", "motivasyon", "başarı",
            "aşk", "sevgi", "kalp kırıklığı", "ayrılık",
            "türkçe", "türk müziği", "pop rock", "türkçe rock",
            "slow", "fast", "upbeat",

            // Ek Spor & Aktivite
            "yüzme", "futbol", "basketbol",
            "dövüş sanatları", "boks", "güç antrenmanı", "kardio",

            // Ek Ruh Hali
            "huzur", "dinginlik", "coşku", "hüzün", "umut",
            "sevinç", "özgürlük", "nostalji",

            // Ek Türler
            "türk halk müziği", "tasavvuf", "sufi", "marş",
            "funk", "disco", "ambient", "lofi",

            // Ek Zaman & Ortam
            "gece yarısı", "gün batımı", "öğleden sonra",
            "sonbahar", "bahar",
            "kamp", "doğa", "orman", "deniz",

            // Ek Etkinlik
            "bebek", "çocuk şarkıları", "uyku zamanı",
            "okul", "mezuniyet", "yılbaşı", "bayram",
            "spor müzikleri", "kazanmak",

            // Ek Coğrafi
            "balkan", "orta doğu", "kürtçe",
            "ankara", "izmir",

            // Ek Sanatçı Temalı
            "sertab", "ajda",
            "türkiye", "müzik",

            // Ek Özel
            "şiir", "söz", "hikaye",
            "gitar", "keman", "ud", "saz",
            "konser", "canlı performans",
            "podcast müzik", "radyo"
        );
    }

    // ── Test metodu ───────────────────────────────────────────────────────────
    @DisplayName("PlaylistGroup Discovery")
    @ParameterizedTest(name = "\"{0}\"")
    @MethodSource("terms")
    void discover(String term) {
        Object[] tek = findGroupData(api.search(term, PLAYLIST_INDEX,    TOP_N, false), "content");
        Object[] akt = findGroupData(api.search(term, "active-indices",  TOP_N, false), "topHits");

        String tekGroup = (String) tek[0];
        int    tekPos   = (int)    tek[1];
        String aktGroup = (String) akt[0];
        int    aktPos   = (int)    akt[1];

        String tekStatus = tekGroup != null ? "OK" : "NOK";
        String aktStatus = aktGroup != null ? "OK" : "NOK";

        System.out.printf("%-22s | TEK:%-3s %-30s sıra=%-2d | AKT:%-3s %-30s sıra=%d%n",
                term, tekStatus, tekGroup != null ? tekGroup : "-", tekPos,
                aktStatus, aktGroup != null ? aktGroup : "-", aktPos);

        RESULTS.add(new Result(term,
                tekStatus, tekGroup != null ? tekGroup : "", tekPos,
                aktStatus, aktGroup != null ? aktGroup : "", aktPos));

        // Discovery test her zaman geçer — exception fırlatılmadığı sürece fail olmaz
    }

    // ── Yardımcılar ──────────────────────────────────────────────────────────

    /** base path içinde ilk playlistGroup'u bulur. Dizi: [String name, int pos (1-based, 0=yok)]. */
    private static Object[] findGroupData(Response res, String base) {
        if (res.statusCode() != 200) return new Object[]{null, 0};
        JsonPath jp = res.jsonPath();
        for (int i = 0; i < TOP_N; i++) {
            String itemKind = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.itemKind"));
            if ("playlistGroup".equals(itemKind)) {
                String name = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.itemName"));
                return new Object[]{name.isEmpty() ? "(adsız)" : name, i + 1};
            }
        }
        return new Object[]{null, 0};
    }

    private static void cell(Row row, int col, String val, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(val != null ? val : "");
        c.setCellStyle(style);
    }

    private static CellStyle headerStyle(Workbook wb, IndexedColors bg) {
        Font f = wb.createFont(); f.setBold(true); f.setFontHeightInPoints((short) 10);
        f.setColor(IndexedColors.WHITE.getIndex()); f.setFontName("Arial");
        CellStyle s = wb.createCellStyle(); s.setFont(f);
        s.setFillForegroundColor(bg.getIndex()); s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER); s.setVerticalAlignment(VerticalAlignment.CENTER);
        border(s); return s;
    }

    private static CellStyle dataStyle(Workbook wb, IndexedColors bg) {
        Font f = wb.createFont(); f.setFontHeightInPoints((short) 9); f.setFontName("Arial");
        CellStyle s = wb.createCellStyle(); s.setFont(f);
        s.setFillForegroundColor(bg.getIndex()); s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        border(s); return s;
    }

    private static void border(CellStyle s) {
        s.setBorderBottom(BorderStyle.THIN); s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);  s.setBorderRight(BorderStyle.THIN);
    }
}
