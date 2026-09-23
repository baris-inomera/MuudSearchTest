package report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import tests.GurultuAnalizi;
import tests.GurultuAnalizi.GurultuRow;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.*;

/**
 * GurultuReportWriter — Gürültü Analizi Excel raporu üretici.
 *
 * Sheet 1 — Özet:        Case başına gürültü sayısı + genel istatistikler
 * Sheet 2 — Tüm Sonuçlar: Her case × top-15; TEMİZ/LYRİCS?/GÜRÜLTÜ renk kodlu
 * Sheet 3 — Gürültüler:   Sadece TEMİZ olmayan satırlar
 *
 * Renk:
 *   TEMİZ   → beyaz
 *   LYRİCS? → sarı  (tartışmalı, manuel kontrol)
 *   GÜRÜLTÜ → kırmızı (kesin sorun)
 */
public class GurultuReportWriter {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final String[] DETAIL_HEADERS = {
            "Case ID", "Arama Terimi", "Bölüm",
            "Pos", "Result Tipi", "Başlık", "Sanatçı",
            "Popularity", "Score", "ID",
            "Durum", "Gürültü Tipi", "Açıklama"
    };

    // ─────────────────────────────────────────────────────────────────────────
    public static void write(List<GurultuRow> rows, String outDir, String ortam) {
        try {
            Files.createDirectories(Path.of(outDir));
            String fname = outDir + "/GurultuAnalizi_"
                    + LocalDateTime.now().format(TS) + "_" + ortam + ".xlsx";

            try (Workbook wb = new XSSFWorkbook();
                 FileOutputStream fos = new FileOutputStream(fname)) {

                Styles s = new Styles(wb);
                writeOzet(wb, s, rows);
                writeTumSonuclar(wb, s, rows);
                writeGurultular(wb, s, rows);

                wb.write(fos);
                System.out.println("✅ Gürültü raporu yazıldı: " + fname);
            }
        } catch (Exception e) {
            System.err.println("❌ GurultuReportWriter hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================================
    // SHEET 1 — ÖZET
    // =========================================================================
    private static void writeOzet(Workbook wb, Styles s, List<GurultuRow> rows) {
        Sheet sheet = wb.createSheet("Özet");
        int r = 0;

        // Başlık
        Row title = sheet.createRow(r++);
        title.setHeightInPoints(30);
        cell(title, 0, "GÜRÜLTÜ ANALİZİ — ÖZET  (top-15 koşumu)", s.titleNavy);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));
        r++;

        // İstatistikler
        long total   = rows.size();
        long temiz   = rows.stream().filter(x -> GurultuAnalizi.TEMIZ.equals(x.noiseLabel())).count();
        long lyrics  = rows.stream().filter(x -> GurultuAnalizi.LYRICS.equals(x.noiseLabel())).count();
        long gurultu = rows.stream().filter(x -> GurultuAnalizi.GURULTU.equals(x.noiseLabel())).count();
        long cases   = rows.stream().map(GurultuRow::caseId).distinct().count();

        statRow(sheet, s, r++, "Toplam Result Satırı", total);
        statRow(sheet, s, r++, "Toplam Case",          cases);
        statRow(sheet, s, r++, "TEMİZ",                temiz);
        statRow(sheet, s, r++, "LYRİCS? (tartışmalı)", lyrics);
        statRow(sheet, s, r++, "GÜRÜLTÜ (kesin)",      gurultu);
        r++;

        // Case bazlı tablo başlığı
        Row hRow = sheet.createRow(r++);
        hRow.setHeightInPoints(22);
        String[] hdrs = {"Case ID", "Arama Terimi", "Bölüm", "Toplam", "TEMİZ", "LYRİCS?", "GÜRÜLTÜ"};
        for (int ci = 0; ci < hdrs.length; ci++) cell(hRow, ci, hdrs[ci], s.colHdrNavy);

        // Case satırları
        Map<String, List<GurultuRow>> byCase = rows.stream()
                .collect(Collectors.groupingBy(GurultuRow::caseId, LinkedHashMap::new, Collectors.toList()));

        for (var e : byCase.entrySet()) {
            List<GurultuRow> cr = e.getValue();
            GurultuRow first = cr.get(0);
            long ct = cr.stream().filter(x -> GurultuAnalizi.TEMIZ.equals(x.noiseLabel())).count();
            long cl = cr.stream().filter(x -> GurultuAnalizi.LYRICS.equals(x.noiseLabel())).count();
            long cg = cr.stream().filter(x -> GurultuAnalizi.GURULTU.equals(x.noiseLabel())).count();

            Row row = sheet.createRow(r++);
            CellStyle rowStyle = cg > 0 ? s.redCell : (cl > 0 ? s.yelCell : s.whiteCell);
            cell(row, 0, first.caseId(),    rowStyle);
            cell(row, 1, first.searchTerm(),rowStyle);
            cell(row, 2, first.section(),   rowStyle);
            numCell(row, 3, ct + cl + cg,   rowStyle);
            numCell(row, 4, ct,             rowStyle);
            numCell(row, 5, cl,             rowStyle);
            numCell(row, 6, cg,             rowStyle);
        }

        int[] widths = {20, 30, 28, 10, 10, 10, 10};
        for (int ci = 0; ci < widths.length; ci++) sheet.setColumnWidth(ci, widths[ci] * 256);
        sheet.createFreezePane(0, 1);
    }

    // =========================================================================
    // SHEET 2 — TÜM SONUÇLAR
    // =========================================================================
    private static void writeTumSonuclar(Workbook wb, Styles s, List<GurultuRow> rows) {
        Sheet sheet = wb.createSheet("Tüm Sonuçlar");
        Row title = sheet.createRow(0);
        title.setHeightInPoints(28);
        cell(title, 0,
                "TÜM SONUÇLAR — Her Case × Top-15   [ beyaz=TEMİZ | sarı=LYRİCS? | kırmızı=GÜRÜLTÜ ]",
                s.titleNavy);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, DETAIL_HEADERS.length - 1));
        writeDetailRows(sheet, s, rows, true);
    }

    // =========================================================================
    // SHEET 3 — SADECE GÜRÜLTÜLER
    // =========================================================================
    private static void writeGurultular(Workbook wb, Styles s, List<GurultuRow> rows) {
        Sheet sheet = wb.createSheet("Gürültüler");
        Row title = sheet.createRow(0);
        title.setHeightInPoints(28);
        cell(title, 0, "GÜRÜLTÜ SONUÇLAR — LYRİCS? ve GÜRÜLTÜ satırları", s.titleTeal);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, DETAIL_HEADERS.length - 1));
        List<GurultuRow> filtered = rows.stream()
                .filter(r -> !GurultuAnalizi.TEMIZ.equals(r.noiseLabel()))
                .toList();
        writeDetailRows(sheet, s, filtered, false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    private static void writeDetailRows(Sheet sheet, Styles s,
                                         List<GurultuRow> rows, boolean separators) {
        // Header (satır 1)
        Row hRow = sheet.createRow(1);
        hRow.setHeightInPoints(22);
        for (int ci = 0; ci < DETAIL_HEADERS.length; ci++)
            cell(hRow, ci, DETAIL_HEADERS[ci], s.colHdrNavy);

        int r = 2;
        String prevCase = null;

        for (GurultuRow row : rows) {
            if (separators && !row.caseId().equals(prevCase) && prevCase != null) {
                // İnce ayırıcı satır
                Row sep = sheet.createRow(r++);
                sep.setHeightInPoints(3);
                for (int ci = 0; ci < DETAIL_HEADERS.length; ci++)
                    cell(sep, ci, "", s.colHdrNavy);
            }
            prevCase = row.caseId();

            CellStyle rowStyle = switch (row.noiseLabel()) {
                case "GÜRÜLTÜ" -> s.redCell;
                case "LYRİCS?" -> s.yelCell;
                default        -> s.whiteCell;
            };
            CellStyle rowWrap = switch (row.noiseLabel()) {
                case "GÜRÜLTÜ" -> s.redWrap;
                case "LYRİCS?" -> s.yelWrap;
                default        -> s.whiteWrap;
            };
            CellStyle statusStyle = switch (row.noiseLabel()) {
                case "GÜRÜLTÜ" -> s.statusRed;
                case "LYRİCS?" -> s.statusYel;
                default        -> s.statusGrn;
            };

            Row dataRow = sheet.createRow(r++);
            cell   (dataRow,  0, row.caseId(),       rowStyle);
            cell   (dataRow,  1, row.searchTerm(),   rowStyle);
            cell   (dataRow,  2, row.section(),      rowStyle);
            numCell(dataRow,  3, row.position(),     rowStyle);
            cell   (dataRow,  4, row.resultKind(),   rowStyle);
            cell   (dataRow,  5, row.title(),        rowStyle);
            cell   (dataRow,  6, row.artist(),       rowStyle);
            numCell(dataRow,  7, row.popularity(),   rowStyle);
            numCell(dataRow,  8, row.score(),        rowStyle);
            cell   (dataRow,  9, row.id(),           rowStyle);
            cell   (dataRow, 10, row.noiseLabel(),   statusStyle);
            cell   (dataRow, 11, row.noiseType(),    rowStyle);
            cell   (dataRow, 12, row.noiseReason(),  rowWrap);
        }

        int[] widths = {18, 25, 25, 6, 12, 35, 25, 12, 12, 14, 12, 22, 45};
        for (int ci = 0; ci < widths.length; ci++)
            sheet.setColumnWidth(ci, widths[ci] * 256);
        sheet.createFreezePane(0, 2);
        if (r > 2)
            sheet.setAutoFilter(new CellRangeAddress(1, r - 1, 0, DETAIL_HEADERS.length - 1));
    }

    // =========================================================================
    // STİL NESNESI — IndexedColors ile (XSSFColor gerektirmez)
    // =========================================================================
    static class Styles {
        final CellStyle titleNavy, titleTeal;
        final CellStyle colHdrNavy;
        final CellStyle whiteCell, yelCell, redCell;
        final CellStyle whiteWrap, yelWrap, redWrap;
        final CellStyle statusGrn, statusYel, statusRed;
        final CellStyle statLabel;

        Styles(Workbook wb) {
            Font boldWhLg  = font(wb, true,  12, IndexedColors.WHITE);
            Font boldWhSm  = font(wb, true,  10, IndexedColors.WHITE);
            Font normal    = font(wb, false,  9, IndexedColors.BLACK);
            Font boldRed   = font(wb, true,   9, IndexedColors.DARK_RED);
            Font boldYel   = font(wb, true,   9, IndexedColors.DARK_RED);  // DARK_YELLOW yok, sarı bg üstünde DARK_RED okunur
            Font boldGrn   = font(wb, true,   9, IndexedColors.DARK_GREEN);

            titleNavy  = fill(wb, boldWhLg, IndexedColors.DARK_BLUE,   true, false);
            titleTeal  = fill(wb, boldWhLg, IndexedColors.DARK_TEAL,   true, false);
            colHdrNavy = fill(wb, boldWhSm, IndexedColors.DARK_BLUE,   true, false);

            whiteCell  = fill(wb, normal,   IndexedColors.WHITE,       false, false);
            yelCell    = fill(wb, normal,   IndexedColors.LIGHT_YELLOW, false, false);
            redCell    = fill(wb, normal,   IndexedColors.ROSE,         false, false);

            whiteWrap  = fill(wb, normal,   IndexedColors.WHITE,        false, true);
            yelWrap    = fill(wb, normal,   IndexedColors.LIGHT_YELLOW, false, true);
            redWrap    = fill(wb, normal,   IndexedColors.ROSE,         false, true);

            statusGrn  = status(wb, boldGrn, IndexedColors.LIGHT_GREEN);
            statusYel  = status(wb, boldYel, IndexedColors.LIGHT_YELLOW);
            statusRed  = status(wb, boldRed, IndexedColors.ROSE);

            statLabel  = fill(wb, normal,   IndexedColors.PALE_BLUE,   false, false);
        }

        private static Font font(Workbook wb, boolean bold, int size, IndexedColors color) {
            Font f = wb.createFont();
            f.setBold(bold);
            f.setFontHeightInPoints((short) size);
            f.setColor(color.getIndex());
            f.setFontName("Arial");
            return f;
        }

        private static CellStyle fill(Workbook wb, Font font, IndexedColors bg,
                                       boolean center, boolean wrap) {
            CellStyle s = wb.createCellStyle();
            s.setFont(font);
            s.setFillForegroundColor(bg.getIndex());
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            s.setAlignment(center ? HorizontalAlignment.CENTER : HorizontalAlignment.LEFT);
            s.setWrapText(wrap);
            border(s);
            return s;
        }

        private static CellStyle status(Workbook wb, Font font, IndexedColors bg) {
            CellStyle s = fill(wb, font, bg, true, false);
            return s;
        }

        private static void border(CellStyle s) {
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    private static void cell(Row row, int col, String val, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(val != null ? val : "");
        c.setCellStyle(style);
    }

    private static void numCell(Row row, int col, double val, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(val);
        c.setCellStyle(style);
    }

    private static void numCell(Row row, int col, long val, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(val);
        c.setCellStyle(style);
    }

    private static void statRow(Sheet sheet, Styles s, int r, String label, long val) {
        Row row = sheet.createRow(r);
        cell   (row, 0, label, s.statLabel);
        numCell(row, 1, val,   s.whiteCell);
    }
}
