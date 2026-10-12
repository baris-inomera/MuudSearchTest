package tests.collector;
import tests.arama.YazimToleransiTest;

import client.MuudSearchApi;
import config.TestConfig;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import util.MuudSearchUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * DdpYazimSnapshot — YazimToleransiTest'teki TÜM case'leri API'ye sorar,
 * ham response'ları (pos, corrected, suggestedText, top-5) Excel'e döker.
 *
 * Amacı: Index ayarı değiştiğinde hangi case'ler düzeldi, hangisi hâlâ NOK?
 * Her test koşusunda anlık snapshot alınır.
 *
 * mvn test -Dtest=DdpYazimSnapshot
 * Çıktı: reports/yazim-snapshot/DdpYazimSnapshot_<zaman>_<ortam>.xlsx
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DdpYazimSnapshot extends TestConfig {

    // ─────────────────────────────────────────────────────────────────────────
    // Case listesi: doğrudan YazimToleransiTest'ten alınır
    // ─────────────────────────────────────────────────────────────────────────
    static Stream<YazimToleransiTest.DdpCase> cases() {
        return YazimToleransiTest.cases();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sonuç satırı (Excel'e yazılacak)
    // ─────────────────────────────────────────────────────────────────────────
    record SnapRow(
            String caseId,
            String section,
            String term,
            boolean correction,
            int topN,
            String expArtist,
            String expTrack,
            String variant,
            int foundPos,       // 1-based; -1 = top-50'de yok
            String status,      // "OK" / "NOK"
            boolean corrected,
            String suggestedText,
            String top5         // "1. [kind] Track — Artist | pop=X | score=Y\n2. ..."
    ) {}

    private static final List<SnapRow> ROWS = new ArrayList<>();
    private static MuudSearchApi api;

    // ─────────────────────────────────────────────────────────────────────────
    @BeforeAll
    static void init() {
        api = new MuudSearchApi();
        System.out.println("📸 DdpYazimSnapshot başlatıldı → " + MuudSearchApi.baseUrl());
        System.out.println("   Tüm YazimToleransiTest case'leri anlık snapshot alınıyor...\n");
    }

    @AfterAll
    static void writeReport() throws IOException {
        long okCount  = ROWS.stream().filter(r -> "OK".equals(r.status())).count();
        long nokCount = ROWS.stream().filter(r -> "NOK".equals(r.status())).count();
        System.out.printf("%n📋 Snapshot tamamlandı: %d case | OK=%d | NOK=%d%n",
                ROWS.size(), okCount, nokCount);
        writeExcel(ROWS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SNAPSHOT RUNNER — her case için API'ye sorar, ham sonucu kaydeder
    // ─────────────────────────────────────────────────────────────────────────
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("cases")
    @Order(1)
    void snapshot(YazimToleransiTest.DdpCase dc) {
        int     foundPos     = -1;
        boolean corrected    = false;
        String  suggested    = "";
        String  top5         = "";
        String  status       = "NOK";

        try {
            Response res = api.search(dc.term(), "active-indices", 50, dc.correction());
            JsonPath jp  = res.jsonPath();

            // corrected / suggestedText (root level)
            Object corrObj = jp.get("corrected");
            corrected  = corrObj != null && Boolean.parseBoolean(corrObj.toString());
            suggested  = safeStr(jp.getString("suggestedText"));

            // Top-5 önizleme
            List<Object> list = MuudSearchUtils.resultsList(jp);
            String base = MuudSearchUtils.getBasePath(jp);
            StringBuilder sb = new StringBuilder();
            int preview = Math.min(5, list.size());
            for (int i = 0; i < preview; i++) {
                String kind   = safeStr(jp.getString(base + "[" + i + "].data.kind"));
                String track  = safeStr(jp.getString(base + "[" + i + "].data.songName"));
                if (track.isEmpty()) track = safeStr(jp.getString(base + "[" + i + "].data.albumName"));
                if (track.isEmpty()) track = safeStr(jp.getString(base + "[" + i + "].data.playlistName"));
                String artist = MuudSearchUtils.getPerformerName(jp, base + "[" + i + "].data");
                Object popObj   = jp.get(base + "[" + i + "].data.popularity");
                Object scoreObj = jp.get(base + "[" + i + "].score");
                String popStr   = popObj   != null ? popObj.toString().trim()   : "";
                String scoreStr = scoreObj != null ? scoreObj.toString().trim() : "";
                String pop   = !popStr.isEmpty()   ? String.format("%.2f", Double.parseDouble(popStr))   : "?";
                String score = !scoreStr.isEmpty() ? String.format("%.2f", Double.parseDouble(scoreStr)) : "?";
                sb.append(i + 1).append(". [").append(kind).append("] ")
                  .append(track.isEmpty() ? artist : track)
                  .append(" — ").append(artist)
                  .append(" | pop=").append(pop)
                  .append(" | score=").append(score)
                  .append("\n");
            }
            if (list.isEmpty()) sb.append("(API boş döndü)");
            top5 = sb.toString().trim();

            // Pozisyon bul (aynı mantık: expTrack varsa findArtistAndTrackIndex, yoksa findArtistIndex)
            String ea = safeStr(dc.expArtist());
            String et = safeStr(dc.expTrack());

            if (!ea.isEmpty() || !et.isEmpty()) {
                int idx = !et.isEmpty()
                        ? MuudSearchUtils.findArtistAndTrackIndex(jp, 50, ea, et)
                        : MuudSearchUtils.findArtistIndex(jp, 50, ea);
                foundPos = idx >= 0 ? idx + 1 : -1;

                // OK/NOK: topN içinde mi?
                if (foundPos > 0 && foundPos <= dc.topN()) {
                    status = "OK";
                } else if (foundPos > 0) {
                    status = "NOK"; // bulundu ama topN dışında
                } else {
                    status = "NOK"; // hiç bulunamadı
                }
            } else {
                // DDP-367 komparatif veya DDP-368 etiket — basit varlık kontrolü
                status = list.isEmpty() ? "NOK" : "OK";
                foundPos = list.isEmpty() ? -1 : 1;
            }

        } catch (Exception e) {
            top5   = "API HATA: " + e.getMessage();
            status = "NOK";
        }

        String posStr = foundPos > 0 ? "pos " + foundPos + "/50" : "TOP-50'DE YOK";
        System.out.printf("[%s] %-40s corr=%-5s | %-16s | sug='%s' | %s%n",
                dc.caseId(),
                "\"" + dc.term() + "\"",
                dc.correction(),
                posStr,
                suggested,
                status);

        ROWS.add(new SnapRow(
                dc.caseId(), dc.section(), dc.term(),
                dc.correction(), dc.topN(),
                safeStr(dc.expArtist()), safeStr(dc.expTrack()),
                dc.variant(),
                foundPos, status,
                corrected, suggested, top5
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXCEL YAZICI
    // ─────────────────────────────────────────────────────────────────────────
    private static void writeExcel(List<SnapRow> rows) throws IOException {
        Path dir = Path.of("reports/yazim-snapshot");
        Files.createDirectories(dir);
        String ts       = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy_HH.mm"));
        String fileName = dir + "/DdpYazimSnapshot_" + ts + "_" + MuudSearchApi.ortam() + ".xlsx";

        XSSFWorkbook wb = new XSSFWorkbook();

        // ── Stiller ──────────────────────────────────────────────────────────
        CellStyle hdrStyle = makeHeaderStyle(wb);
        CellStyle okStyle  = makeColorStyle(wb, IndexedColors.LIGHT_GREEN);
        CellStyle nokStyle = makeColorStyle(wb, IndexedColors.ROSE);
        CellStyle wrapStyle = wb.createCellStyle();
        wrapStyle.setWrapText(true);
        wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);

        // ── Sheet 1: Tüm Sonuçlar ────────────────────────────────────────────
        XSSFSheet ws = wb.createSheet("Snapshot — Tüm Case'ler");

        String[] headers = {
            "No", "Case ID", "Section", "Arama Terimi", "Correction", "TopN",
            "Beklenen Sanatçı", "Beklenen Şarkı", "Varyant",
            "Konum (top-50)", "Durum",
            "Corrected?", "SuggestedText",
            "Top 5 Sonuç"
        };
        Row hRow = ws.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            Cell cell = hRow.createCell(c);
            cell.setCellValue(headers[c]);
            cell.setCellStyle(hdrStyle);
        }

        int rowNum = 1;
        for (SnapRow r : rows) {
            Row row = ws.createRow(rowNum);
            row.setHeightInPoints(80);

            cell(row, 0).setCellValue(rowNum);
            cell(row, 1).setCellValue(r.caseId());
            cell(row, 2).setCellValue(r.section());
            cell(row, 3).setCellValue(r.term());
            cell(row, 4).setCellValue(r.correction() ? "true" : "false");
            cell(row, 5).setCellValue(r.topN());
            cell(row, 6).setCellValue(r.expArtist());
            cell(row, 7).setCellValue(r.expTrack());
            cell(row, 8).setCellValue(r.variant());

            // Konum
            String posStr = r.foundPos() > 0 ? "pos " + r.foundPos() + "/50" : "TOP-50'DE YOK";
            cell(row, 9).setCellValue(posStr);

            // Durum (renkli)
            Cell statusCell = row.createCell(10);
            statusCell.setCellValue(r.status());
            statusCell.setCellStyle("OK".equals(r.status()) ? okStyle : nokStyle);

            cell(row, 11).setCellValue(r.corrected() ? "✓ evet" : "hayır");
            cell(row, 12).setCellValue(r.suggestedText());

            // Top-5 (wrap)
            Cell top5Cell = row.createCell(13);
            top5Cell.setCellValue(r.top5());
            top5Cell.setCellStyle(wrapStyle);

            rowNum++;
        }

        // Sütun genişlikleri
        int[] widths = {5, 14, 28, 28, 10, 6, 20, 20, 24, 16, 8, 10, 22, 70};
        for (int i = 0; i < widths.length; i++) ws.setColumnWidth(i, widths[i] * 256);
        ws.createFreezePane(0, 1);

        // ── Sheet 2: Sadece NOK'lar ──────────────────────────────────────────
        XSSFSheet wsNok = wb.createSheet("NOK Case'ler");
        Row hNok = wsNok.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            Cell cell = hNok.createCell(c);
            cell.setCellValue(headers[c]);
            cell.setCellStyle(hdrStyle);
        }
        int nokRow = 1;
        for (SnapRow r : rows) {
            if (!"NOK".equals(r.status())) continue;
            Row row = wsNok.createRow(nokRow++);
            row.setHeightInPoints(80);

            cell(row, 0).setCellValue(nokRow - 1);
            cell(row, 1).setCellValue(r.caseId());
            cell(row, 2).setCellValue(r.section());
            cell(row, 3).setCellValue(r.term());
            cell(row, 4).setCellValue(r.correction() ? "true" : "false");
            cell(row, 5).setCellValue(r.topN());
            cell(row, 6).setCellValue(r.expArtist());
            cell(row, 7).setCellValue(r.expTrack());
            cell(row, 8).setCellValue(r.variant());
            String posStr = r.foundPos() > 0 ? "pos " + r.foundPos() + "/50" : "TOP-50'DE YOK";
            cell(row, 9).setCellValue(posStr);
            Cell sc = row.createCell(10); sc.setCellValue("NOK"); sc.setCellStyle(nokStyle);
            cell(row, 11).setCellValue(r.corrected() ? "✓ evet" : "hayır");
            cell(row, 12).setCellValue(r.suggestedText());
            Cell t5 = row.createCell(13); t5.setCellValue(r.top5()); t5.setCellStyle(wrapStyle);
        }
        for (int i = 0; i < widths.length; i++) wsNok.setColumnWidth(i, widths[i] * 256);
        wsNok.createFreezePane(0, 1);

        // ── Sheet 3: Özet ────────────────────────────────────────────────────
        XSSFSheet wsSummary = wb.createSheet("Bölüm Özeti");
        Row sh = wsSummary.createRow(0);
        for (String h : new String[]{"Section","Toplam","OK","NOK","Oran"}) {
            Cell c = sh.createCell(Arrays.asList("Section","Toplam","OK","NOK","Oran").indexOf(h));
            c.setCellValue(h); c.setCellStyle(hdrStyle);
        }
        Map<String, int[]> sectionMap = new LinkedHashMap<>();
        for (SnapRow r : rows) {
            sectionMap.computeIfAbsent(r.section(), k -> new int[2]);
            int[] v = sectionMap.get(r.section());
            if ("OK".equals(r.status())) v[0]++; else v[1]++;
        }
        int si = 1;
        for (Map.Entry<String, int[]> e : sectionMap.entrySet()) {
            Row sr = wsSummary.createRow(si++);
            int ok = e.getValue()[0], nok = e.getValue()[1], tot = ok + nok;
            cell(sr, 0).setCellValue(e.getKey());
            cell(sr, 1).setCellValue(tot);
            Cell oc = sr.createCell(2); oc.setCellValue(ok);  oc.setCellStyle(okStyle);
            Cell nc = sr.createCell(3); nc.setCellValue(nok); nc.setCellStyle(nok > 0 ? nokStyle : okStyle);
            cell(sr, 4).setCellValue(String.format("%%%d", tot > 0 ? 100 * ok / tot : 0));
        }
        // Genel toplam
        Row total = wsSummary.createRow(si + 1);
        long allOk  = rows.stream().filter(r -> "OK".equals(r.status())).count();
        long allNok = rows.stream().filter(r -> "NOK".equals(r.status())).count();
        cell(total, 0).setCellValue("GENEL TOPLAM");
        cell(total, 1).setCellValue(rows.size());
        Cell tOk = total.createCell(2); tOk.setCellValue((int) allOk);  tOk.setCellStyle(okStyle);
        Cell tNok = total.createCell(3); tNok.setCellValue((int) allNok); tNok.setCellStyle(allNok > 0 ? nokStyle : okStyle);
        cell(total, 4).setCellValue(String.format("%%%d", rows.size() > 0 ? 100 * (int) allOk / rows.size() : 0));
        for (int i = 0; i < 5; i++) wsSummary.setColumnWidth(i, new int[]{40, 10, 8, 8, 8}[i] * 256);

        try (FileOutputStream fos = new FileOutputStream(fileName)) { wb.write(fos); }
        wb.close();
        System.out.println("✅ Snapshot raporu yazıldı: " + fileName);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Yardımcı metodlar
    // ─────────────────────────────────────────────────────────────────────────
    private static Cell cell(Row r, int col) { return r.createCell(col); }

    private static String safeStr(String s) { return s == null ? "" : s; }

    private static CellStyle makeHeaderStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(true); f.setFontHeightInPoints((short) 11);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    private static CellStyle makeColorStyle(XSSFWorkbook wb, IndexedColors color) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(color.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }
}
