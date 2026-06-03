package report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import validation.DiscoveryCoverage;
import validation.DiscoveryFieldSummary;
import validation.DiscoveryViolation;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MAPPING DISCOVERY RAPORU
 *
 * Discovery testi bittiğinde 4 sheet'lik bir Excel oluşturur:
 *
 *   1) Field Summary  → her benzersiz (index, field, expected→actual) bir satır
 *                       affected doc count'a göre azalan sırada
 *   2) All Violations → ham (tüm) FAIL kayıtları
 *   3) Coverage       → index başına tarama istatistiği
 *   4) Terms          → kullanılan arama term'leri
 */
public final class MappingDiscoveryReportWriter {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private MappingDiscoveryReportWriter() {}

    public static void write(List<DiscoveryFieldSummary> fieldSummaries,
                             List<DiscoveryViolation> allViolations,
                             List<DiscoveryCoverage> coverage,
                             List<String> terms) {
        String fileName = "MappingDiscoveryReport_" + LocalDateTime.now().format(TS) + ".xlsx";
        Path outPath = Path.of(System.getProperty("user.dir"), fileName);

        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(outPath.toFile())) {

            Styles s = buildStyles(wb);

            writeFieldSummarySheet(wb, s, fieldSummaries);
            writeAllViolationsSheet(wb, s, allViolations);
            writeCoverageSheet(wb, s, coverage);
            writeTermsSheet(wb, s, terms);

            wb.write(fos);
            System.out.println("📊 Discovery Raporu oluşturuldu: " + outPath.getFileName());
        } catch (Exception e) {
            throw new RuntimeException("Discovery raporu yazılamadı: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // SHEET 1 — FIELD SUMMARY
    // ------------------------------------------------------------------
    private static void writeFieldSummarySheet(Workbook wb, Styles s,
                                               List<DiscoveryFieldSummary> summaries) {
        Sheet sheet = wb.createSheet("Field Summary");
        int r = 0;

        // Title
        Row titleRow = sheet.createRow(r++);
        titleRow.setHeightInPoints(22f);
        Cell title = titleRow.createCell(0);
        title.setCellValue("MUUD SEARCH — MAPPING TİP UYUŞMAZLIK ÖZETİ (Discovery)");
        title.setCellStyle(s.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

        Row descRow = sheet.createRow(r++);
        descRow.createCell(0).setCellValue(
                "Her satır benzersiz bir (index, alan, beklenen→gerçek tip) uyuşmazlığıdır. "
                + "'Etkilenen doc' sütunu bu bug'ın kaç farklı doc'ta görüldüğünü gösterir.");
        sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, 7));
        r++;

        // Toplam satırı
        long totalAffectedDocs = summaries.stream()
                .flatMap(fs -> fs.affectedDocs().stream())
                .distinct()
                .count();
        Row grand = sheet.createRow(r++);
        grand.setHeightInPoints(20f);
        createCell(grand, 0, "TOPLAM", s.grandTotal);
        createCell(grand, 1, summaries.size() + " benzersiz alan-bug", s.grandTotal);
        createCell(grand, 2, "Etkilenen doc: " + totalAffectedDocs, s.grandTotal);
        r++;

        // Header
        String[] headers = {
                "No", "Index", "ES Index", "Field Path",
                "Beklenen Tip", "Gerçek Tip", "Etkilenen Doc Sayısı", "Örnek Değerler"
        };
        int headerRowIndex = r;
        Row hr = sheet.createRow(r++);
        hr.setHeightInPoints(18f);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hr.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(s.tableHeader);
        }

        // Sort: doc sayısı azalan
        List<DiscoveryFieldSummary> sorted = summaries.stream()
                .sorted(Comparator.comparingInt((DiscoveryFieldSummary fs) -> fs.affectedDocs().size()).reversed())
                .toList();

        int no = 1;
        for (DiscoveryFieldSummary fs : sorted) {
            Row row = sheet.createRow(r++);
            CellStyle st = fs.affectedDocs().size() >= 10 ? s.nokDark
                         : fs.affectedDocs().size() >= 3  ? s.warn
                         : s.info;
            createCell(row, 0, no++, st);
            createCell(row, 1, fs.indexLabel(), st);
            createCell(row, 2, fs.esIndex(), st);
            createCell(row, 3, fs.fieldPath(), st);
            createCell(row, 4, fs.expectedType(), st);
            createCell(row, 5, fs.actualType(), st);
            createCell(row, 6, fs.affectedDocs().size(), st);
            createCell(row, 7,
                    fs.sampleValues().stream().limit(5).collect(Collectors.joining(", ")),
                    st);
        }

        if (r > headerRowIndex + 1) {
            sheet.setAutoFilter(new CellRangeAddress(headerRowIndex, r - 1, 0, headers.length - 1));
        }

        sheet.setColumnWidth(0, 6 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        sheet.setColumnWidth(2, 26 * 256);
        sheet.setColumnWidth(3, 40 * 256);
        sheet.setColumnWidth(4, 16 * 256);
        sheet.setColumnWidth(5, 18 * 256);
        sheet.setColumnWidth(6, 18 * 256);
        sheet.setColumnWidth(7, 60 * 256);
        sheet.createFreezePane(0, headerRowIndex + 1);
    }

    // ------------------------------------------------------------------
    // SHEET 2 — ALL VIOLATIONS
    // ------------------------------------------------------------------
    private static void writeAllViolationsSheet(Workbook wb, Styles s,
                                                List<DiscoveryViolation> violations) {
        Sheet sheet = wb.createSheet("All Violations");
        int r = 0;

        Row titleRow = sheet.createRow(r++);
        titleRow.setHeightInPoints(20f);
        Cell title = titleRow.createCell(0);
        title.setCellValue("TÜM TİP UYUŞMAZLIKLARI (HAM)");
        title.setCellStyle(s.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 9));
        r++;

        String[] headers = {
                "No", "Index", "ES Index", "Doc ID", "Field Path",
                "Beklenen", "Gerçek", "Değer", "Term (kaynak)", "Not"
        };
        int headerRowIndex = r;
        Row hr = sheet.createRow(r++);
        hr.setHeightInPoints(18f);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hr.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(s.tableHeader);
        }

        int no = 1;
        for (DiscoveryViolation v : violations) {
            Row row = sheet.createRow(r++);
            createCell(row, 0, no++, s.info);
            createCell(row, 1, v.indexLabel(), s.info);
            createCell(row, 2, v.esIndex(), s.info);
            createCell(row, 3, v.docId(), s.info);
            createCell(row, 4, v.fieldPath(), s.info);
            createCell(row, 5, v.expectedType(), s.info);
            createCell(row, 6, v.actualType(), s.info);
            createCell(row, 7, v.actualValue(), s.info);
            createCell(row, 8, v.term(), s.info);
            createCell(row, 9, v.note() == null ? "" : v.note(), s.info);
        }

        if (r > headerRowIndex + 1) {
            sheet.setAutoFilter(new CellRangeAddress(headerRowIndex, r - 1, 0, headers.length - 1));
        }

        sheet.setColumnWidth(0,  6 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        sheet.setColumnWidth(2, 26 * 256);
        sheet.setColumnWidth(3, 14 * 256);
        sheet.setColumnWidth(4, 40 * 256);
        sheet.setColumnWidth(5, 14 * 256);
        sheet.setColumnWidth(6, 18 * 256);
        sheet.setColumnWidth(7, 24 * 256);
        sheet.setColumnWidth(8, 14 * 256);
        sheet.setColumnWidth(9, 30 * 256);
        sheet.createFreezePane(0, headerRowIndex + 1);
    }

    // ------------------------------------------------------------------
    // SHEET 3 — COVERAGE
    // ------------------------------------------------------------------
    private static void writeCoverageSheet(Workbook wb, Styles s,
                                           List<DiscoveryCoverage> coverage) {
        Sheet sheet = wb.createSheet("Coverage");
        int r = 0;

        Row titleRow = sheet.createRow(r++);
        titleRow.setHeightInPoints(20f);
        Cell title = titleRow.createCell(0);
        title.setCellValue("TARAMA KAPSAMI");
        title.setCellStyle(s.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));
        r++;

        String[] headers = {
                "Index", "ES Index", "Index ID",
                "Toplam Search", "Başarılı Search",
                "Doc Evaluations", "Benzersiz Doc", "FAIL Sayısı"
        };
        int headerRowIndex = r;
        Row hr = sheet.createRow(r++);
        hr.setHeightInPoints(18f);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hr.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(s.tableHeader);
        }

        for (DiscoveryCoverage c : coverage) {
            Row row = sheet.createRow(r++);
            CellStyle st = c.violationCount() > 0 ? s.warn : s.ok;
            createCell(row, 0, c.indexLabel(), st);
            createCell(row, 1, c.esIndex(), st);
            createCell(row, 2, c.indexId(), st);
            createCell(row, 3, c.searchCount(), st);
            createCell(row, 4, c.passedSearches(), st);
            createCell(row, 5, c.docEvaluations(), st);
            createCell(row, 6, c.uniqueDocs(), st);
            createCell(row, 7, c.violationCount(), st);
        }

        sheet.setColumnWidth(0, 14 * 256);
        sheet.setColumnWidth(1, 26 * 256);
        sheet.setColumnWidth(2, 10 * 256);
        sheet.setColumnWidth(3, 16 * 256);
        sheet.setColumnWidth(4, 18 * 256);
        sheet.setColumnWidth(5, 18 * 256);
        sheet.setColumnWidth(6, 16 * 256);
        sheet.setColumnWidth(7, 14 * 256);
        sheet.createFreezePane(0, headerRowIndex + 1);
    }

    // ------------------------------------------------------------------
    // SHEET 4 — TERMS
    // ------------------------------------------------------------------
    private static void writeTermsSheet(Workbook wb, Styles s, List<String> terms) {
        Sheet sheet = wb.createSheet("Terms");
        int r = 0;

        Row titleRow = sheet.createRow(r++);
        titleRow.setHeightInPoints(20f);
        Cell title = titleRow.createCell(0);
        title.setCellValue("KULLANILAN ARAMA TERM'LERİ");
        title.setCellStyle(s.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
        r++;

        Row hr = sheet.createRow(r++);
        Cell c1 = hr.createCell(0); c1.setCellValue("No"); c1.setCellStyle(s.tableHeader);
        Cell c2 = hr.createCell(1); c2.setCellValue("Term"); c2.setCellStyle(s.tableHeader);

        int no = 1;
        for (String term : terms) {
            Row row = sheet.createRow(r++);
            createCell(row, 0, no++, s.info);
            createCell(row, 1, term, s.info);
        }

        sheet.setColumnWidth(0, 6 * 256);
        sheet.setColumnWidth(1, 30 * 256);
    }

    // ------------------------------------------------------------------
    // STYLES
    // ------------------------------------------------------------------
    private record Styles(
            CellStyle title, CellStyle tableHeader, CellStyle grandTotal,
            CellStyle ok, CellStyle warn, CellStyle info, CellStyle nokDark
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

        CellStyle ok   = colorRow(wb, IndexedColors.LIGHT_GREEN);
        CellStyle warn = colorRow(wb, IndexedColors.LIGHT_YELLOW);
        CellStyle info = colorRow(wb, IndexedColors.LIGHT_TURQUOISE);

        CellStyle nokDark = wb.createCellStyle();
        nokDark.setFont(boldWhite);
        nokDark.setAlignment(HorizontalAlignment.CENTER);
        nokDark.setVerticalAlignment(VerticalAlignment.CENTER);
        nokDark.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
        nokDark.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(nokDark);

        return new Styles(title, tableHeader, grandTotal, ok, warn, info, nokDark);
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
        else if (value instanceof Long l) cell.setCellValue(l);
        else if (value instanceof Number n) cell.setCellValue(n.doubleValue());
        else cell.setCellValue(value == null ? "" : value.toString());
        cell.setCellStyle(style);
    }
}
