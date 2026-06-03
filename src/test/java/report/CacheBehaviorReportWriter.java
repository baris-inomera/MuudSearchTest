package report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import validation.CacheBehaviorResult;
import validation.CacheBehaviorResult.Verdict;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Cache davranış testinin Excel raporu.
 *
 * Tek sheet ("Cache Behavior") — her case için:
 *   - Doküman id, ES index, takip edilen alan
 *   - Original değer (test öncesi)
 *   - Updated değer (ES'de değiştirilen)
 *   - ES update sonrası search'te gelen değer (cache mı, taze mi)
 *   - TTL sonrası search'te gelen değer (taze mi, hala eski mi)
 *   - Hit detected? TTL detected?
 *   - Sonuç (Verdict) renkli
 *   - Açıklama
 */
public final class CacheBehaviorReportWriter {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final String[] HEADERS = {
            "No", "Case ID", "Arama", "Gateway Index", "ES Index", "Doc ID", "Alan",
            "Original Değer", "ES'de Yeni Değer",
            "Update Sonrası Gelen", "TTL Sonrası Gelen",
            "Hit Tespit", "TTL Tespit", "Sonuç", "Açıklama"
    };

    private CacheBehaviorReportWriter() {}

    public static void writeStandalone(List<CacheBehaviorResult> results) {
        String fileName = "CacheBehaviorReport_" + LocalDateTime.now().format(TS) + ".xlsx";
        Path outPath = Path.of(System.getProperty("user.dir"), fileName);
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
            writeSheet(wb, results, "Cache Behavior");
            wb.write(fos);
            System.out.println("📊 Cache Davranış Raporu oluşturuldu: " + outPath.getFileName());
        } catch (Exception e) {
            throw new RuntimeException("Rapor yazılamadı: " + e.getMessage(), e);
        }
    }

    private static void writeSheet(Workbook wb, List<CacheBehaviorResult> results, String sheetName) {
        Styles s = buildStyles(wb);
        Sheet sheet = wb.createSheet(sheetName);
        int r = 0;

        // Başlık
        Row titleRow = sheet.createRow(r++);
        titleRow.setHeightInPoints(22f);
        Cell title = titleRow.createCell(0);
        title.setCellValue("MUUD SEARCH API — CACHE DAVRANIŞ DOĞRULAMA");
        title.setCellStyle(s.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 10));

        Row desc = sheet.createRow(r++);
        desc.createCell(0).setCellValue(
                "Test akışı: 1) Search yap, değeri kaydet  2) ES'de değiştir  " +
                "3) Tekrar search (cache hit kontrolü)  4) TTL bekle  " +
                "5) Tekrar search (TTL kontrolü)  6) ES'de geri al");
        sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, 14));
        r++;

        // Özet satırı
        long working = results.stream().filter(t -> t.verdict() == Verdict.CACHE_WORKING).count();
        long disabled = results.stream().filter(t -> t.verdict() == Verdict.CACHE_DISABLED).count();
        long ttlBad = results.stream().filter(t -> t.verdict() == Verdict.TTL_NOT_EXPIRED).count();
        long partial = results.stream().filter(t -> t.verdict() == Verdict.CACHE_PARTIAL).count();
        long errors = results.stream().filter(t -> t.verdict() == Verdict.ERROR).count();
        long skipped = results.stream().filter(t -> t.verdict() == Verdict.SKIPPED).count();

        Row grand = sheet.createRow(r++);
        grand.setHeightInPoints(20f);
        createCell(grand, 0, "GENEL TOPLAM", s.grandTotal);
        createCell(grand, 1, results.size() + " case", s.grandTotal);
        createCell(grand, 2, "WORKING=" + working, s.grandTotal);
        createCell(grand, 3, "DISABLED=" + disabled, s.grandTotal);
        createCell(grand, 4, "TTL_BAD=" + ttlBad, s.grandTotal);
        createCell(grand, 5, "PARTIAL=" + partial, s.grandTotal);
        createCell(grand, 6, "ERROR=" + errors, s.grandTotal);
        createCell(grand, 7, "SKIPPED=" + skipped, s.grandTotal);

        r++;

        // Header
        int headerRowIndex = r;
        Row headerRow = sheet.createRow(r++);
        headerRow.setHeightInPoints(18f);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(HEADERS[i]);
            c.setCellStyle(s.tableHeader);
        }

        int no = 1;
        for (CacheBehaviorResult t : results) {
            Row row = sheet.createRow(r++);
            row.setHeightInPoints(22f);

            CellStyle rowStyle = switch (t.verdict()) {
                case CACHE_WORKING -> s.ok;
                case CACHE_DISABLED -> s.info;
                case TTL_NOT_EXPIRED, CACHE_PARTIAL -> s.warn;
                case ERROR -> s.nokDark;
                case SKIPPED -> s.gray;
            };

            createCell(row, 0, no++, rowStyle);
            createCell(row, 1, t.caseId(), rowStyle);
            createCell(row, 2, t.term(), rowStyle);
            createCell(row, 3, t.indexId(), rowStyle);
            createCell(row, 4, t.esIndex(), rowStyle);
            createCell(row, 5, t.docId(), rowStyle);
            createCell(row, 6, t.trackField(), rowStyle);
            createCell(row, 7, t.origValueStr(), rowStyle);
            createCell(row, 8, t.updatedValueStr(), rowStyle);
            createCell(row, 9, t.afterUpdateStr(), rowStyle);
            createCell(row, 10, t.afterTtlStr(), rowStyle);
            createCell(row, 11, t.hitDetected() ? "✓" : "✗", rowStyle);
            createCell(row, 12, t.ttlDetected() ? "✓" : "✗", rowStyle);
            createCell(row, 13, t.verdict().name(), rowStyle);
            createCell(row, 14, t.note(), rowStyle);
        }

        if (r > headerRowIndex + 1) {
            sheet.setAutoFilter(new CellRangeAddress(
                    headerRowIndex, r - 1, 0, HEADERS.length - 1));
        }

        // Sütun genişlikleri
        sheet.autoSizeColumn(0);
        sheet.setColumnWidth(1, 14 * 256);
        sheet.setColumnWidth(2, 12 * 256);
        sheet.setColumnWidth(3, 14 * 256);
        sheet.setColumnWidth(4, 22 * 256);
        sheet.setColumnWidth(5, 14 * 256);
        sheet.setColumnWidth(6, 18 * 256);
        sheet.setColumnWidth(7, 14 * 256);
        sheet.setColumnWidth(8, 14 * 256);
        sheet.setColumnWidth(9, 16 * 256);
        sheet.setColumnWidth(10, 16 * 256);
        sheet.setColumnWidth(11, 10 * 256);
        sheet.setColumnWidth(12, 10 * 256);
        sheet.setColumnWidth(13, 18 * 256);
        sheet.setColumnWidth(14, 60 * 256);

        sheet.createFreezePane(0, headerRowIndex + 1);
    }

    private record Styles(
            CellStyle title, CellStyle tableHeader, CellStyle grandTotal,
            CellStyle ok, CellStyle warn, CellStyle info, CellStyle nokDark, CellStyle gray
    ) {}

    private static Styles buildStyles(Workbook wb) {
        Font bold = wb.createFont();
        bold.setBold(true);
        Font boldWhite = wb.createFont();
        boldWhite.setBold(true);
        boldWhite.setColor(IndexedColors.WHITE.getIndex());
        Font titleFont = wb.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);

        CellStyle title = wb.createCellStyle();
        title.setFont(titleFont);
        title.setAlignment(HorizontalAlignment.CENTER);
        title.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle tableHeader = wb.createCellStyle();
        tableHeader.setFont(bold);
        tableHeader.setAlignment(HorizontalAlignment.CENTER);
        tableHeader.setVerticalAlignment(VerticalAlignment.CENTER);
        tableHeader.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        tableHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(tableHeader);

        CellStyle grandTotal = wb.createCellStyle();
        grandTotal.setFont(bold);
        grandTotal.setAlignment(HorizontalAlignment.CENTER);
        grandTotal.setVerticalAlignment(VerticalAlignment.CENTER);
        grandTotal.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        grandTotal.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(grandTotal);

        CellStyle ok = colorRow(wb, IndexedColors.LIGHT_GREEN);
        CellStyle warn = colorRow(wb, IndexedColors.LIGHT_YELLOW);
        CellStyle info = colorRow(wb, IndexedColors.LIGHT_TURQUOISE);
        CellStyle gray = colorRow(wb, IndexedColors.GREY_25_PERCENT);

        CellStyle nokDark = wb.createCellStyle();
        nokDark.setFont(boldWhite);
        nokDark.setAlignment(HorizontalAlignment.CENTER);
        nokDark.setVerticalAlignment(VerticalAlignment.CENTER);
        nokDark.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
        nokDark.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(nokDark);

        return new Styles(title, tableHeader, grandTotal, ok, warn, info, nokDark, gray);
    }

    private static CellStyle colorRow(Workbook wb, IndexedColors color) {
        CellStyle st = wb.createCellStyle();
        st.setAlignment(HorizontalAlignment.CENTER);
        st.setVerticalAlignment(VerticalAlignment.CENTER);
        st.setFillForegroundColor(color.getIndex());
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(st);
        return st;
    }

    private static void setBorders(CellStyle st) {
        st.setBorderTop(BorderStyle.THIN);
        st.setBorderBottom(BorderStyle.THIN);
        st.setBorderLeft(BorderStyle.THIN);
        st.setBorderRight(BorderStyle.THIN);
    }

    private static void createCell(Row row, int idx, Object value, CellStyle style) {
        Cell cell = row.createCell(idx);
        if (value instanceof Integer i) cell.setCellValue(i);
        else cell.setCellValue(value == null ? "" : value.toString());
        cell.setCellStyle(style);
    }
}
