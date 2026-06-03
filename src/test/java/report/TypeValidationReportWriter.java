package report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import validation.CaseResult;
import validation.TypeMismatch;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TypeValidation sonuçlarını Excel'e yazar.
 *
 *  İki kullanım modu:
 *    1) writeStandalone(rows)           → kendine ait yeni rapor (TypeValidationReport_*.xlsx)
 *    2) appendToWorkbook(wb, rows, ...) → mevcut bir workbook'a sheet olarak ekler
 *
 *  ExcelTestReportWriter ile aynı style çizgisini koruyor (PALE_BLUE header,
 *  alternating row colors, LIGHT_GREEN OK, ROSE NOK).
 */
public final class TypeValidationReportWriter {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final String[] HEADERS = {
            "No", "Doc ID", "Field Path", "Expected ES Type",
            "Actual JSON Type", "Actual Value", "Status", "Note"
    };

    private static final String[] SUMMARY_HEADERS = {
            "Durum", "Toplam", "Yüzde"
    };

    private TypeValidationReportWriter() {}

    // =========================================================================
    // BAĞIMSIZ RAPOR — kendi başına yeni dosya oluşturur
    // =========================================================================
    public static void writeStandalone(List<TypeMismatch> rows) {
        String fileName = "TypeValidationReport_" + LocalDateTime.now().format(TS) + ".xlsx";
        Path outPath = Path.of(System.getProperty("user.dir"), fileName);
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
            appendToWorkbook(wb, rows, "TypeValidation");
            wb.write(fos);
            System.out.println("📊 Mapping Doğrulama Raporu oluşturuldu: " + outPath.getFileName());
        } catch (Exception e) {
            throw new RuntimeException("TypeValidation rapor yazılamadı: " + e.getMessage(), e);
        }
    }

    /**
     * Case bazında özet + detay raporu üretir.
     *
     * İki sheet üretir:
     *   1) "Case Summary" — her test case için tek satır, ekibe iletmeye uygun.
     *   2) "All Details"  — bütün uyuşmazlıkların satır satır detayı.
     */
    public static void writeStandaloneWithCaseSummary(List<CaseResult> caseResults) {
        String fileName = "TypeValidationReport_" + LocalDateTime.now().format(TS) + ".xlsx";
        Path outPath = Path.of(System.getProperty("user.dir"), fileName);

        // Tüm detayları topla
        List<TypeMismatch> allRows = new ArrayList<>();
        for (CaseResult cr : caseResults) allRows.addAll(cr.mismatches());

        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
            // Sheet 1: Field Summary — her alan için tek satır. HATALI alanlar üstte.
            //          "long lazımdı, float geldi" tarzı net bulgu görmek için en kullanışlı sheet.
            writeFieldSummarySheet(wb, allRows, "Field Summary");
            // Sheet 2: Case bazında özet
            writeCaseSummarySheet(wb, caseResults, "Case Summary");
            // Sheet 3: Tüm detaylar
            appendToWorkbook(wb, allRows, "All Details");
            wb.write(fos);
            System.out.println("📊 Mapping Doğrulama Raporu oluşturuldu: " + outPath.getFileName());
        } catch (Exception e) {
            throw new RuntimeException("TypeValidation rapor yazılamadı: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // FIELD SUMMARY SHEET — her unique alan için tek satır
    // Her field için: kaç PASS, kaç FAIL, beklenen tip, gelen tip örneği.
    // Hatalı alanlar (FAIL>0) en üstte, kırmızı renkli.
    // =========================================================================
    private static void writeFieldSummarySheet(Workbook wb, List<TypeMismatch> rows, String sheetName) {
        Styles s = buildStyles(wb);
        Sheet sheet = wb.createSheet(sheetName);
        int r = 0;

        // ── Başlık ─────────────────────────────────────────────────────────────
        Row titleRow = sheet.createRow(r++);
        titleRow.setHeightInPoints(22f);
        Cell title = titleRow.createCell(0);
        title.setCellValue("MUUD MAPPING TİP DOĞRULAMA — ALAN BAZINDA ÖZET");
        title.setCellStyle(s.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

        // Açıklama satırı
        Row descRow = sheet.createRow(r++);
        Cell desc = descRow.createCell(0);
        desc.setCellValue("Her alan için: beklenen tip, kaç PASS, kaç FAIL, gelen tip örneği, hatalı değer. " +
                "Kırmızı satırlar = ekibe bildirilecek bug'lar.");
        sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, 7));

        r++; // boş satır

        // ── Alan bazında grupla ────────────────────────────────────────────────
        // Her field için aggregate edilen kayıt
        record FieldStat(
                String path, String expected,
                long pass, long fail, long missing, long nullCount,
                String sampleActualType, String sampleValue
        ) {}

        Map<String, List<TypeMismatch>> byField = rows.stream()
                .collect(Collectors.groupingBy(TypeMismatch::fieldPath));

        List<FieldStat> stats = new ArrayList<>();
        for (Map.Entry<String, List<TypeMismatch>> e : byField.entrySet()) {
            List<TypeMismatch> mismatches = e.getValue();
            long pass = mismatches.stream().filter(TypeMismatch::isPass).count();
            long fail = mismatches.stream().filter(TypeMismatch::isFailure).count();
            long missing = mismatches.stream()
                    .filter(m -> m.status() == TypeMismatch.Status.MISSING_IN_MAPPING).count();
            long nullCount = mismatches.stream()
                    .filter(m -> m.status() == TypeMismatch.Status.NULL_VALUE).count();

            // Hata varsa hatalı örnek; yoksa ilk PASS örneği
            TypeMismatch sample = mismatches.stream()
                    .filter(TypeMismatch::isFailure)
                    .findFirst()
                    .orElseGet(() -> mismatches.stream().findFirst().orElse(null));

            String expected = sample != null ? sample.expectedEsType() : "";
            String sampleActual = sample != null ? sample.actualJsonType() : "";
            String sampleValue = sample != null ? sample.actualValue() : "";

            stats.add(new FieldStat(e.getKey(), expected, pass, fail, missing, nullCount,
                    sampleActual, sampleValue));
        }

        // Sırala: FAIL'i olanlar önce, sonra MISSING, sonra alfabetik
        stats.sort((a, b) -> {
            if (a.fail != b.fail) return Long.compare(b.fail, a.fail);
            if (a.missing != b.missing) return Long.compare(b.missing, a.missing);
            return a.path.compareTo(b.path);
        });

        // ── Header ─────────────────────────────────────────────────────────────
        String[] cols = {"No", "Alan (Field)", "Beklenen Tip", "PASS", "FAIL",
                         "MISSING", "Gelen Örnek Tip", "Örnek Değer"};
        int headerRowIndex = r;
        Row headerRow = sheet.createRow(r++);
        headerRow.setHeightInPoints(18f);
        for (int i = 0; i < cols.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(s.tableHeader);
        }

        // ── Satırlar ──────────────────────────────────────────────────────────
        int no = 1;
        for (FieldStat fs : stats) {
            Row row = sheet.createRow(r++);
            row.setHeightInPoints(18f);

            CellStyle rowStyle;
            if (fs.fail > 0) {
                rowStyle = s.nok;       // kırmızı — gerçek bug
            } else if (fs.missing > 0 && fs.pass == 0) {
                rowStyle = s.warn;      // sarı — mapping'de yok
            } else {
                rowStyle = s.ok;        // yeşil — sorunsuz
            }

            createCell(row, 0, no++, rowStyle);
            createCell(row, 1, fs.path, rowStyle);
            createCell(row, 2, fs.expected, rowStyle);
            createCell(row, 3, (int) fs.pass, rowStyle);
            createCell(row, 4, (int) fs.fail, rowStyle);
            createCell(row, 5, (int) fs.missing, rowStyle);
            createCell(row, 6, fs.sampleActualType, rowStyle);
            createCell(row, 7, fs.sampleValue, rowStyle);
        }

        // ── Auto filter ────────────────────────────────────────────────────────
        if (r > headerRowIndex + 1) {
            sheet.setAutoFilter(new CellRangeAddress(
                    headerRowIndex, r - 1, 0, cols.length - 1));
        }

        // ── Sütun genişlikleri ─────────────────────────────────────────────────
        sheet.autoSizeColumn(0);
        sheet.setColumnWidth(1, 38 * 256);
        sheet.setColumnWidth(2, 16 * 256);
        sheet.setColumnWidth(3, 8 * 256);
        sheet.setColumnWidth(4, 8 * 256);
        sheet.setColumnWidth(5, 10 * 256);
        sheet.setColumnWidth(6, 18 * 256);
        sheet.setColumnWidth(7, 30 * 256);

        // Header satırını sabit tut (scroll edince başlık kalır)
        sheet.createFreezePane(0, headerRowIndex + 1);
    }

    // =========================================================================
    // CASE SUMMARY SHEET — her test case bir satır
    // =========================================================================
    private static void writeCaseSummarySheet(Workbook wb, List<CaseResult> cases, String sheetName) {
        Styles s = buildStyles(wb);
        Sheet sheet = wb.createSheet(sheetName);
        int r = 0;

        // ── Başlık ─────────────────────────────────────────────────────────────
        Row titleRow = sheet.createRow(r++);
        titleRow.setHeightInPoints(22f);
        Cell title = titleRow.createCell(0);
        title.setCellValue("MUUD MAPPING TİP DOĞRULAMA — CASE ÖZETİ");
        title.setCellStyle(s.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

        // ── Grand summary (üst kısımda hızlı bakış) ────────────────────────────
        int grandTotal = cases.stream().mapToInt(CaseResult::total).sum();
        long grandPass = cases.stream().mapToLong(CaseResult::passCount).sum();
        long grandFail = cases.stream().mapToLong(CaseResult::failCount).sum();
        long grandMiss = cases.stream().mapToLong(CaseResult::missingCount).sum();
        long grandNull = cases.stream().mapToLong(CaseResult::nullCount).sum();
        long failedCases = cases.stream().filter(c -> c.failCount() > 0).count();
        long skippedCases = cases.stream().filter(c -> "SKIPPED".equals(c.status())).count();

        Row grand = sheet.createRow(r++);
        grand.setHeightInPoints(20f);
        createCell(grand, 0, "GENEL TOPLAM", s.grandTotal);
        createCell(grand, 1, cases.size() + " case", s.grandTotal);
        createCell(grand, 2, grandTotal + " alan", s.grandTotal);
        createCell(grand, 3, "PASS=" + grandPass, s.grandTotal);
        createCell(grand, 4, "FAIL=" + grandFail, s.grandTotal);
        createCell(grand, 5, "MISSING=" + grandMiss, s.grandTotal);
        createCell(grand, 6, "NULL=" + grandNull, s.grandTotal);
        createCell(grand, 7, "Hatalı case=" + failedCases + " | Skip=" + skippedCases, s.grandTotal);

        r++; // boş satır

        // ── Detay tablo başlığı ────────────────────────────────────────────────
        String[] cols = {"No", "Case ID", "Arama", "Index", "Status",
                         "Toplam", "PASS", "FAIL", "MISSING", "NULL"};
        int headerRowIndex = r;
        Row headerRow = sheet.createRow(r++);
        headerRow.setHeightInPoints(18f);
        for (int i = 0; i < cols.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(s.tableHeader);
        }

        // ── Case satırları ─────────────────────────────────────────────────────
        int no = 1;
        for (CaseResult c : cases) {
            Row row = sheet.createRow(r++);
            row.setHeightInPoints(18f);
            boolean even = (no % 2 == 0);
            CellStyle base     = even ? s.grey : s.white;
            CellStyle centered = even ? s.greyCentered : s.whiteCentered;

            createCell(row, 0, no++, centered);
            createCell(row, 1, c.caseId(), centered);
            createCell(row, 2, c.term(), base);
            createCell(row, 3, c.indexId(), centered);

            // Status hücresi renkli
            Cell statusCell = row.createCell(4);
            statusCell.setCellValue(c.status());
            statusCell.setCellStyle(statusStyleForCase(c, s));

            createCell(row, 5, c.total(),               centered);
            createCell(row, 6, (int) c.passCount(),     centered);
            createCell(row, 7, (int) c.failCount(),     centered);
            createCell(row, 8, (int) c.missingCount(),  centered);
            createCell(row, 9, (int) c.nullCount(),     centered);
        }

        // ── Auto filter ────────────────────────────────────────────────────────
        if (r > headerRowIndex + 1) {
            sheet.setAutoFilter(new CellRangeAddress(
                    headerRowIndex, r - 1, 0, cols.length - 1));
        }

        // ── Sütun genişlikleri ─────────────────────────────────────────────────
        sheet.autoSizeColumn(0);
        sheet.setColumnWidth(1, 20 * 256);
        sheet.setColumnWidth(2, 20 * 256);
        sheet.setColumnWidth(3, 18 * 256);
        sheet.setColumnWidth(4, 12 * 256);
        sheet.setColumnWidth(5, 10 * 256);
        sheet.setColumnWidth(6, 10 * 256);
        sheet.setColumnWidth(7, 10 * 256);
        sheet.setColumnWidth(8, 12 * 256);
        sheet.setColumnWidth(9, 10 * 256);
    }

    private static CellStyle statusStyleForCase(CaseResult c, Styles s) {
        if ("SKIPPED".equalsIgnoreCase(c.status())) return s.warn;
        if (c.failCount() > 0) return s.nok;
        return s.ok;
    }

    // =========================================================================
    // MEVCUT WORKBOOK'A SHEET EKLER
    // =========================================================================
    public static void appendToWorkbook(Workbook wb, List<TypeMismatch> rows, String sheetName) {
        Styles s = buildStyles(wb);

        // varolan aynı isimde sheet varsa kaldır
        Sheet existing = wb.getSheet(sheetName);
        if (existing != null) wb.removeSheetAt(wb.getSheetIndex(existing));

        Sheet sheet = wb.createSheet(sheetName);
        int r = 0;

        // ── Başlık ─────────────────────────────────────────────────────────────
        Row titleRow = sheet.createRow(r++);
        titleRow.setHeightInPoints(22f);
        Cell title = titleRow.createCell(0);
        title.setCellValue("MUUD MAPPING TİP DOĞRULAMA — " + sheetName.toUpperCase());
        title.setCellStyle(s.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

        // ── Özet ───────────────────────────────────────────────────────────────
        Row sumHeadRow = sheet.createRow(r++);
        sumHeadRow.setHeightInPoints(18f);
        for (int i = 0; i < SUMMARY_HEADERS.length; i++) {
            Cell c = sumHeadRow.createCell(i);
            c.setCellValue(SUMMARY_HEADERS[i]);
            c.setCellStyle(s.tableHeader);
        }

        Map<TypeMismatch.Status, Long> byStatus = rows.stream()
                .collect(Collectors.groupingBy(TypeMismatch::status, Collectors.counting()));
        int total = rows.size();

        r = addSummary(sheet, r, "PASS",
                byStatus.getOrDefault(TypeMismatch.Status.PASS, 0L), total, s);
        r = addSummary(sheet, r, "FAIL",
                byStatus.getOrDefault(TypeMismatch.Status.FAIL, 0L), total, s);
        r = addSummary(sheet, r, "MISSING_IN_MAPPING",
                byStatus.getOrDefault(TypeMismatch.Status.MISSING_IN_MAPPING, 0L), total, s);
        r = addSummary(sheet, r, "NULL_VALUE",
                byStatus.getOrDefault(TypeMismatch.Status.NULL_VALUE, 0L), total, s);

        // Genel toplam
        Row grandRow = sheet.createRow(r++);
        grandRow.setHeightInPoints(18f);
        createCell(grandRow, 0, "GENEL TOPLAM", s.grandTotal);
        createCell(grandRow, 1, total, s.grandTotal);
        createCell(grandRow, 2, "%100", s.grandTotal);

        r++; // boş satır

        // ── Detay tablo başlığı ────────────────────────────────────────────────
        int listHeaderRowIndex = r;
        Row listHeaderRow = sheet.createRow(r++);
        listHeaderRow.setHeightInPoints(18f);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell c = listHeaderRow.createCell(i);
            c.setCellValue(HEADERS[i]);
            c.setCellStyle(s.tableHeader);
        }

        // ── Detay satırları ────────────────────────────────────────────────────
        int caseNo = 1;
        for (TypeMismatch m : rows) {
            Row row = sheet.createRow(r++);
            row.setHeightInPoints(20f);
            boolean isEven = (caseNo % 2 == 0);
            CellStyle base     = isEven ? s.grey : s.white;
            CellStyle centered = isEven ? s.greyCentered : s.whiteCentered;

            createCell(row, 0, caseNo++, centered);
            createCell(row, 1, nz(m.docId()), centered);
            createCell(row, 2, nz(m.fieldPath()), base);
            createCell(row, 3, nz(m.expectedEsType()), centered);
            createCell(row, 4, nz(m.actualJsonType()), centered);
            createCell(row, 5, nz(m.actualValue()), base);

            Cell statusCell = row.createCell(6);
            statusCell.setCellValue(m.status().name());
            statusCell.setCellStyle(statusStyleFor(m.status(), s));

            createCell(row, 7, nz(m.note()), base);
        }

        // ── Auto filter ────────────────────────────────────────────────────────
        if (r > listHeaderRowIndex + 1) {
            sheet.setAutoFilter(new CellRangeAddress(
                    listHeaderRowIndex, r - 1, 0, HEADERS.length - 1));
        }

        // ── Sütun genişlikleri ─────────────────────────────────────────────────
        sheet.autoSizeColumn(0);
        sheet.setColumnWidth(1, 18 * 256);
        sheet.setColumnWidth(2, 35 * 256);
        sheet.setColumnWidth(3, 16 * 256);
        sheet.setColumnWidth(4, 16 * 256);
        sheet.setColumnWidth(5, 30 * 256);
        sheet.setColumnWidth(6, 14 * 256);
        sheet.setColumnWidth(7, 40 * 256);
    }

    // =========================================================================
    // YARDIMCI
    // =========================================================================
    private static int addSummary(Sheet sheet, int row, String label,
                                  long count, int total, Styles s) {
        int rate = total == 0 ? 0 : (int) Math.round((count * 100.0) / total);
        Row r = sheet.createRow(row);
        r.setHeightInPoints(18f);
        boolean even = (row % 2 == 0);
        CellStyle base = even ? s.greyCentered : s.whiteCentered;
        createCell(r, 0, label, base);
        createCell(r, 1, (int) count, base);
        createCell(r, 2, "%" + rate, base);
        return row + 1;
    }

    private static void createCell(Row row, int idx, Object value, CellStyle style) {
        Cell cell = row.createCell(idx);
        if (value instanceof Integer i) cell.setCellValue(i);
        else cell.setCellValue(value == null ? "" : value.toString());
        cell.setCellStyle(style);
    }

    private static CellStyle statusStyleFor(TypeMismatch.Status st, Styles s) {
        return switch (st) {
            case PASS -> s.ok;
            case FAIL -> s.nok;
            case MISSING_IN_MAPPING -> s.warn;
            case NULL_VALUE -> s.warn;
        };
    }

    private static String nz(String v) { return v == null ? "" : v; }

    // =========================================================================
    // STYLES — ExcelTestReportWriter ile uyumlu görünüm
    // =========================================================================
    private record Styles(
            CellStyle title, CellStyle tableHeader,
            CellStyle white, CellStyle grey,
            CellStyle whiteCentered, CellStyle greyCentered,
            CellStyle ok, CellStyle nok, CellStyle warn,
            CellStyle grandTotal
    ) {}

    private static Styles buildStyles(Workbook wb) {
        Font blackFont = wb.createFont();
        blackFont.setColor(IndexedColors.BLACK.getIndex());

        Font bold = wb.createFont();
        bold.setBold(true);
        bold.setColor(IndexedColors.BLACK.getIndex());

        CellStyle tableHeader = wb.createCellStyle();
        tableHeader.setFont(bold);
        tableHeader.setAlignment(HorizontalAlignment.CENTER);
        tableHeader.setVerticalAlignment(VerticalAlignment.CENTER);
        tableHeader.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        tableHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(tableHeader);

        Font titleFont = wb.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        CellStyle title = wb.createCellStyle();
        title.setFont(titleFont);
        title.setAlignment(HorizontalAlignment.CENTER);
        title.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle white = wrapStyle(wb, IndexedColors.WHITE, blackFont);
        CellStyle grey  = wrapStyle(wb, IndexedColors.GREY_25_PERCENT, blackFont);
        CellStyle whiteCentered = centered(wb, white);
        CellStyle greyCentered  = centered(wb, grey);

        CellStyle ok   = statusStyle(wb, IndexedColors.LIGHT_GREEN, blackFont);
        CellStyle nok  = statusStyle(wb, IndexedColors.ROSE, blackFont);
        CellStyle warn = statusStyle(wb, IndexedColors.LIGHT_YELLOW, blackFont);

        CellStyle grandTotal = wb.createCellStyle();
        grandTotal.setFont(bold);
        grandTotal.setAlignment(HorizontalAlignment.CENTER);
        grandTotal.setVerticalAlignment(VerticalAlignment.CENTER);
        grandTotal.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        grandTotal.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(grandTotal);

        return new Styles(title, tableHeader, white, grey,
                whiteCentered, greyCentered, ok, nok, warn, grandTotal);
    }

    private static CellStyle wrapStyle(Workbook wb, IndexedColors bg, Font font) {
        CellStyle st = wb.createCellStyle();
        st.setWrapText(true);
        st.setVerticalAlignment(VerticalAlignment.TOP);
        st.setFont(font);
        st.setFillForegroundColor(bg.getIndex());
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(st);
        return st;
    }

    private static CellStyle centered(Workbook wb, CellStyle base) {
        CellStyle st = wb.createCellStyle();
        st.cloneStyleFrom(base);
        st.setAlignment(HorizontalAlignment.CENTER);
        st.setVerticalAlignment(VerticalAlignment.CENTER);
        return st;
    }

    private static CellStyle statusStyle(Workbook wb, IndexedColors bg, Font font) {
        CellStyle st = wb.createCellStyle();
        st.setWrapText(true);
        st.setAlignment(HorizontalAlignment.CENTER);
        st.setVerticalAlignment(VerticalAlignment.CENTER);
        st.setFont(font);
        st.setFillForegroundColor(bg.getIndex());
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
}
