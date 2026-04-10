package report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Excel raporu üretici.
 *
 * TekliTest / AktifIndexTest → write(rows)
 *   - "Tüm Testler"      → tüm caseler, tip bazlı özet
 *   - "Sanatçı Aramaları" → G1–G4
 *   - "Şarkı Aramaları"   → G8–G10
 *   - "Albüm & Playlist"  → G5–G7
 *
 * KapsayiciTest → writeKapsayici(rows)
 *   - "Tüm Testler"        → tüm 203 case, G1–G20 grup bazlı özet
 *   - "Sanatçı Aramaları"  → G1–G4 + G14 + G15 + G18, kıyas (general vs performer)
 *   - "Kategori & Playlist"→ G5–G7, kıyas (general vs playlist)
 *   - "Albüm"              → G12, kıyas (general vs album)
 *   - "Şarkı & Kombine"    → G8–G10 + G13, kıyas (general vs songs)
 *   - "Kenar Durumlar"     → G11 + G16 + G17 + G19 + G20, normal mod
 */
public class ExcelTestReportWriter {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // ── Sütun başlıkları ────────────────────────────────────────────────────────
    private static final String[] HEADERS = {
            "No", "Case ID", "Test Name", "Description", "Expected Result",
            "Type", "Aranan Index", "UAT Status", "Sonuç"
    };
    private static final String[] HEADERS_CMP = {
            "No", "Case ID", "Test Name", "Description", "Expected Result",
            "Gen. Tip", "Gen. İndeks", "Gen. Status", "Gen. Sonuç",
            "Tekli Tip", "Tekli İndeks", "Tekli Status", "Tekli Sonuç"
    };
    private static final String[] SUMMARY_HEADERS = {
            "Kategori", "Toplam", "Başarılı", "Hata", "Başarı Oranı"
    };

    // ── G1–G20 grup tanımları: [prefix, görünen ad] ─────────────────────────────
    private static final String[][] KAPSAYICI_GROUPS = {
            {"G1_",  "G1 — Sanatçı: Tam/Tek Kelime"},
            {"G2_",  "G2 — Sanatçı: Kısmi Ad"},
            {"G3_",  "G3 — Sanatçı: Alias & Rumuz"},
            {"G4_",  "G4 — Sanatçı: Fuzzy Match"},
            {"G5_",  "G5 — Tür & Kültürel"},
            {"G6_",  "G6 — Dönem Araması"},
            {"G7_",  "G7 — Atmosfer & Aktivite"},
            {"G8_",  "G8 — Şarkı Adı"},
            {"G9_",  "G9 — Şarkı Sözü"},
            {"G10_", "G10 — Yazım Hatalı Şarkı"},
            {"G11_", "G11 — Güvenlik & Sınır"},
            {"G12_", "G12 — Albüm Adı  [YENİ]"},
            {"G13_", "G13 — Sanatçı+Şarkı Kombine  [YENİ]"},
            {"G14_", "G14 — Büyük/Küçük Harf  [YENİ]"},
            {"G15_", "G15 — ASCII Alternatif  [YENİ]"},
            {"G16_", "G16 — Kısa Sorgu  [YENİ]"},
            {"G17_", "G17 — Performans  [YENİ]"},
            {"G18_", "G18 — Extra Karakter  [YENİ]"},
            {"G19_", "G19 — Emoji  [YENİ]"},
            {"G20_", "G20 — Anlamsız Sorgu  [YENİ]"},
    };

    // ── caseId routing — mevcut (TekliTest / AktifIndexTest) ─────────────────────
    private static boolean isPerformerCase(String id) {
        return id != null && (id.startsWith("G1_") || id.startsWith("G2_")
                || id.startsWith("G3_") || id.startsWith("G4_"));
    }
    private static boolean isAlbumCase(String id) {
        return id != null && (id.startsWith("G5_") || id.startsWith("G6_") || id.startsWith("G7_"));
    }
    private static boolean isSongCase(String id) {
        return id != null && (id.startsWith("G8_") || id.startsWith("G9_") || id.startsWith("G10_"));
    }

    // ── caseId routing — KapsayiciTest ────────────────────────────────────────────
    /** G1-G4 + G14 (büyük/küçük) + G15 (ASCII alternatif) + G18 (extra karakter) */
    private static boolean isPerformerKapsayici(String id) {
        return id != null && (id.startsWith("G1_") || id.startsWith("G2_")
                || id.startsWith("G3_") || id.startsWith("G4_")
                || id.startsWith("G14_") || id.startsWith("G15_") || id.startsWith("G18_"));
    }
    /** G5-G7 */
    private static boolean isPlaylistKapsayici(String id) {
        return id != null && (id.startsWith("G5_") || id.startsWith("G6_") || id.startsWith("G7_"));
    }
    /** G12 */
    private static boolean isAlbumKapsayici(String id) {
        return id != null && id.startsWith("G12_");
    }
    /** G8-G10 + G13 (kombine arama) */
    private static boolean isSongKapsayici(String id) {
        return id != null && (id.startsWith("G8_") || id.startsWith("G9_")
                || id.startsWith("G10_") || id.startsWith("G13_"));
    }
    /** G11 + G16 + G17 + G19 + G20 */
    private static boolean isEdgeCaseKapsayici(String id) {
        return id != null && (id.startsWith("G11_") || id.startsWith("G16_")
                || id.startsWith("G17_") || id.startsWith("G19_") || id.startsWith("G20_"));
    }

    // ── Karşılaştırma satırı var mı? ─────────────────────────────────────────────
    private static boolean hasComparison(List<TestResultRow> rows) {
        return rows.stream().anyMatch(r -> r.specificStatus() != null);
    }

    // =========================================================================
    // ENTRY POINT 1 — TekliTest / AktifIndexTest (mevcut davranış, DEĞİŞMEDİ)
    // =========================================================================
    public static void write(List<TestResultRow> rows) {
        String fileName = "TestReport_" + LocalDateTime.now().format(TS) + ".xlsx";
        Path outPath = Path.of(System.getProperty("user.dir"), fileName);

        try (Workbook wb = new XSSFWorkbook()) {
            StyleBundle styles = buildStyles(wb);

            // Sheet 1: Tüm Testler (normal mod)
            writeSheet(wb, "Tüm Testler", rows, styles, false);

            // Sheet 2: Sanatçı Aramaları (G1–G4 veya specificType="performer")
            List<TestResultRow> performerRows = rows.stream()
                    .filter(r -> isPerformerCase(r.caseId()) || "performer".equals(r.specificType()))
                    .collect(Collectors.toList());
            writeSheet(wb, "Sanatçı Aramaları", performerRows, styles, hasComparison(performerRows));

            // Sheet 3: Şarkı Aramaları (G8–G10 veya specificType="songs")
            List<TestResultRow> songRows = rows.stream()
                    .filter(r -> isSongCase(r.caseId()) || "songs".equals(r.specificType()))
                    .collect(Collectors.toList());
            writeSheet(wb, "Şarkı Aramaları", songRows, styles, hasComparison(songRows));

            // Sheet 4: Playlist Aramaları (G5–G7 veya specificType="playlist")
            List<TestResultRow> playlistRows = rows.stream()
                    .filter(r -> isAlbumCase(r.caseId()) || "playlist".equals(r.specificType()))
                    .collect(Collectors.toList());
            writeSheet(wb, "Playlist Aramaları", playlistRows, styles, hasComparison(playlistRows));

            try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
                wb.write(fos);
            }
            System.out.println("📊 Rapor oluşturuldu: " + outPath.getFileName());

        } catch (Exception e) {
            throw new RuntimeException("Excel raporu yazılamadı: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // ENTRY POINT 2 — KapsayiciTest (yeni sheet yapısı, G1–G20 grup bazlı)
    // =========================================================================
    public static void writeKapsayici(List<TestResultRow> rows) {
        String fileName = "TestReport_Kapsayici_" + LocalDateTime.now().format(TS) + ".xlsx";
        Path outPath = Path.of(System.getProperty("user.dir"), fileName);

        try (Workbook wb = new XSSFWorkbook()) {
            StyleBundle styles = buildStyles(wb);

            // ── Sheet 1: Tüm Testler — G1–G20 grup bazlı özet ─────────────────
            writeKapsayiciAllSheet(wb, rows, styles);

            // ── Sheet 2: Sanatçı Aramaları — G1–G4 + G14 + G15 + G18 ──────────
            List<TestResultRow> performerRows = rows.stream()
                    .filter(r -> isPerformerKapsayici(r.caseId()))
                    .collect(Collectors.toList());
            writeSheet(wb, "Sanatçı Aramaları", performerRows, styles, hasComparison(performerRows));

            // ── Sheet 3: Kategori & Playlist — G5–G7 ────────────────────────────
            List<TestResultRow> playlistRows = rows.stream()
                    .filter(r -> isPlaylistKapsayici(r.caseId()))
                    .collect(Collectors.toList());
            writeSheet(wb, "Kategori & Playlist", playlistRows, styles, hasComparison(playlistRows));

            // ── Sheet 4: Albüm — G12 ────────────────────────────────────────────
            List<TestResultRow> albumRows = rows.stream()
                    .filter(r -> isAlbumKapsayici(r.caseId()))
                    .collect(Collectors.toList());
            writeSheet(wb, "Albüm", albumRows, styles, hasComparison(albumRows));

            // ── Sheet 5: Şarkı & Kombine — G8–G10 + G13 ────────────────────────
            List<TestResultRow> songRows = rows.stream()
                    .filter(r -> isSongKapsayici(r.caseId()))
                    .collect(Collectors.toList());
            writeSheet(wb, "Şarkı & Kombine", songRows, styles, hasComparison(songRows));

            // ── Sheet 6: Kenar Durumlar — G11 + G16 + G17 + G19 + G20 ──────────
            List<TestResultRow> edgeRows = rows.stream()
                    .filter(r -> isEdgeCaseKapsayici(r.caseId()))
                    .collect(Collectors.toList());
            writeSheet(wb, "Kenar Durumlar", edgeRows, styles, false);

            try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
                wb.write(fos);
            }
            System.out.println("📊 Kapsayıcı Rapor oluşturuldu: " + outPath.getFileName());

        } catch (Exception e) {
            throw new RuntimeException("Excel raporu yazılamadı: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // KAPSAYICI "TÜM TESTLER" SHEET — G1–G20 grup bazlı özet + tüm caseler
    // =========================================================================
    private static void writeKapsayiciAllSheet(Workbook wb, List<TestResultRow> rows,
                                               StyleBundle s) {
        Sheet sheet = wb.createSheet("Tüm Testler");
        int currentRow = 0;

        // ── Başlık ─────────────────────────────────────────────────────────────
        Row titleRow = sheet.createRow(currentRow++);
        titleRow.setHeightInPoints(22f);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("MUUD TEST SONUÇ ÖZETİ — TÜM TESTLER (G1–G20)");
        titleCell.setCellStyle(s.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

        // ── Özet tablo başlığı ─────────────────────────────────────────────────
        Row sumHeadRow = sheet.createRow(currentRow++);
        sumHeadRow.setHeightInPoints(18f);
        for (int i = 0; i < SUMMARY_HEADERS.length; i++) {
            Cell c = sumHeadRow.createCell(i);
            c.setCellValue(SUMMARY_HEADERS[i]);
            c.setCellStyle(s.tableHeader);
        }

        // ── G1–G20 grup özet satırları ─────────────────────────────────────────
        int grandTotal = 0, grandPass = 0, summaryCounter = 1;
        for (String[] grp : KAPSAYICI_GROUPS) {
            String prefix = grp[0];
            String label  = grp[1];
            List<TestResultRow> grpRows = rows.stream()
                    .filter(r -> r.caseId() != null && r.caseId().startsWith(prefix))
                    .collect(Collectors.toList());
            if (grpRows.isEmpty()) continue;
            int total = grpRows.size();
            int pass  = (int) grpRows.stream()
                    .filter(r -> "OK".equalsIgnoreCase(r.uatStatus())).count();
            grandTotal += total;
            grandPass  += pass;
            writeSummaryRow(sheet, currentRow++, summaryCounter++, label, total, pass, s);
        }

        // ── Grand total ────────────────────────────────────────────────────────
        int grandFail = grandTotal - grandPass;
        int grandRate = grandTotal == 0 ? 0 : (int) Math.round((grandPass * 100.0) / grandTotal);
        Row grandRow = sheet.createRow(currentRow++);
        grandRow.setHeightInPoints(18f);
        createCell(grandRow, 0, "GENEL TOPLAM",  s.grandTotal);
        createCell(grandRow, 1, grandTotal,       s.grandTotal);
        createCell(grandRow, 2, grandPass,         s.grandTotal);
        createCell(grandRow, 3, grandFail,         s.grandTotal);
        createCell(grandRow, 4, "%" + grandRate,   s.grandTotal);

        currentRow++; // boş satır

        // ── Detay tablo başlığı ────────────────────────────────────────────────
        int listHeaderRowIndex = currentRow;
        Row listHeaderRow = sheet.createRow(currentRow++);
        listHeaderRow.setHeightInPoints(18f);
        for (int c = 0; c < HEADERS.length; c++) {
            Cell cell = listHeaderRow.createCell(c);
            cell.setCellValue(HEADERS[c]);
            cell.setCellStyle(s.tableHeader);
        }

        // ── Detay satırları ────────────────────────────────────────────────────
        int caseNo = 1;
        for (TestResultRow r : rows) {
            Row row = sheet.createRow(currentRow++);
            row.setHeightInPoints(45f);  // max ~3 satır yüksekliği
            boolean isEven = (caseNo % 2 == 0);
            CellStyle base     = isEven ? s.grey     : s.white;
            CellStyle centered = isEven ? s.greyCentered : s.whiteCentered;

            createCell(row, 0, caseNo++,                   centered);
            createCell(row, 1, nullSafe(r.caseId()),       centered);
            createCell(row, 2, nullSafe(r.testName()),     base);
            createCell(row, 3, nullSafe(r.description()),  base);
            createCell(row, 4, nullSafe(r.expectedResult()), base);
            createCell(row, 5, nullSafe(r.type()),         base);
            createCell(row, 6, nullSafe(r.indexName()),    base);
            Cell cUat = row.createCell(7);
            cUat.setCellValue(nullSafe(r.uatStatus()));
            cUat.setCellStyle(statusStyleFor(r.uatStatus(), s.ok, s.nok, centered));
            createCell(row, 8, nullSafe(r.sonuc()),        base);
        }

        // ── Auto filter ────────────────────────────────────────────────────────
        if (currentRow > listHeaderRowIndex + 1) {
            sheet.setAutoFilter(new CellRangeAddress(
                    listHeaderRowIndex, currentRow - 1, 0, HEADERS.length - 1));
        }

        // ── Sütun genişlikleri ─────────────────────────────────────────────────
        sheet.autoSizeColumn(0);            // A sütunu: içeriğe göre otomatik genişlik
        sheet.setColumnWidth(1, 12 * 256);
        sheet.setColumnWidth(2, 30 * 256);
        sheet.setColumnWidth(3, 45 * 256);
        sheet.setColumnWidth(4, 45 * 256);
        sheet.setColumnWidth(5, 15 * 256);
        sheet.setColumnWidth(6, 20 * 256);
        sheet.setColumnWidth(7, 12 * 256);
        sheet.setColumnWidth(8, 70 * 256);
    }

    // =========================================================================
    // TEK SHEET YAZICI — normal veya karşılaştırma modunda (AktifIndexTest + alt sheetler)
    // =========================================================================
    private static void writeSheet(Workbook wb, String sheetName,
                                   List<TestResultRow> rows, StyleBundle s,
                                   boolean cmpMode) {
        Sheet sheet = wb.createSheet(sheetName);
        int currentRow = 0;

        // ── Başlık ─────────────────────────────────────────────────────────────
        int totalCols = cmpMode ? HEADERS_CMP.length : HEADERS.length;
        Row titleRow = sheet.createRow(currentRow++);
        titleRow.setHeightInPoints(22f);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("MUUD TEST SONUÇ ÖZETİ — " + sheetName.toUpperCase());
        titleCell.setCellStyle(s.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

        // ── Özet tablo başlığı ─────────────────────────────────────────────────
        Row sumHeadRow = sheet.createRow(currentRow++);
        sumHeadRow.setHeightInPoints(18f);
        for (int i = 0; i < SUMMARY_HEADERS.length; i++) {
            Cell c = sumHeadRow.createCell(i);
            c.setCellValue(SUMMARY_HEADERS[i]);
            c.setCellStyle(s.tableHeader);
        }

        // ── Özet satırları ─────────────────────────────────────────────────────
        int grandTotal = 0, grandPass = 0, summaryCounter = 1;

        if (cmpMode) {
            // Karşılaştırma modunda: general satırı + spesifik tip satırı
            int genTotal = rows.size();
            int genPass  = (int) rows.stream().filter(r -> "OK".equalsIgnoreCase(r.uatStatus())).count();
            String specificLabel = rows.stream()
                    .map(TestResultRow::specificType)
                    .filter(t -> t != null && !t.isBlank())
                    .findFirst().orElse("tekli");
            int speTotal = rows.size();
            int spePass  = (int) rows.stream().filter(r -> "OK".equalsIgnoreCase(r.specificStatus())).count();

            writeSummaryRow(sheet, currentRow++, summaryCounter++, "general",     genTotal, genPass, s);
            writeSummaryRow(sheet, currentRow++, summaryCounter,   specificLabel, speTotal, spePass, s);

            grandTotal = genTotal;
            grandPass  = genPass;
        } else {
            Map<String, List<TestResultRow>> byType = rows.stream()
                    .collect(Collectors.groupingBy(r -> r.type() == null ? "Diğer" : r.type()));
            for (String type : byType.keySet().stream().sorted().toList()) {
                List<TestResultRow> typeRows = byType.get(type);
                int total = typeRows.size();
                int pass  = (int) typeRows.stream().filter(r -> "OK".equalsIgnoreCase(r.uatStatus())).count();
                grandTotal += total;
                grandPass  += pass;
                writeSummaryRow(sheet, currentRow++, summaryCounter++, type, total, pass, s);
            }
        }

        // ── Grand total ────────────────────────────────────────────────────────
        int grandFail = grandTotal - grandPass;
        int grandRate = grandTotal == 0 ? 0 : (int) Math.round((grandPass * 100.0) / grandTotal);
        Row grandRow = sheet.createRow(currentRow++);
        grandRow.setHeightInPoints(18f);
        createCell(grandRow, 0, "GENEL TOPLAM", s.grandTotal);
        createCell(grandRow, 1, grandTotal,      s.grandTotal);
        createCell(grandRow, 2, grandPass,        s.grandTotal);
        createCell(grandRow, 3, grandFail,        s.grandTotal);
        createCell(grandRow, 4, "%" + grandRate,  s.grandTotal);

        currentRow++; // boş satır

        // ── Detay tablo başlığı ────────────────────────────────────────────────
        int listHeaderRowIndex = currentRow;
        Row listHeaderRow = sheet.createRow(currentRow++);
        listHeaderRow.setHeightInPoints(18f);
        String[] headers = cmpMode ? HEADERS_CMP : HEADERS;
        for (int c = 0; c < headers.length; c++) {
            Cell cell = listHeaderRow.createCell(c);
            cell.setCellValue(headers[c]);
            cell.setCellStyle(cmpMode && c >= 9 ? s.specificHeader : s.tableHeader);
        }

        // ── Detay satırları ────────────────────────────────────────────────────
        int caseNo = 1;
        for (TestResultRow r : rows) {
            Row row = sheet.createRow(currentRow++);
            row.setHeightInPoints(45f);  // max ~3 satır yüksekliği
            boolean isEven = (caseNo % 2 == 0);
            CellStyle base     = isEven ? s.grey     : s.white;
            CellStyle centered = isEven ? s.greyCentered : s.whiteCentered;

            int col = 0;
            createCell(row, col++, caseNo++,                   centered);
            createCell(row, col++, nullSafe(r.caseId()),       centered);
            createCell(row, col++, nullSafe(r.testName()),     base);
            createCell(row, col++, nullSafe(r.description()),  base);
            createCell(row, col++, nullSafe(r.expectedResult()), base);

            if (cmpMode) {
                createCell(row, col++, nullSafe(r.type()),          base);
                createCell(row, col++, nullSafe(r.indexName()),     base);
                Cell cGen = row.createCell(col++);
                cGen.setCellValue(nullSafe(r.uatStatus()));
                cGen.setCellStyle(statusStyleFor(r.uatStatus(), s.ok, s.nok, centered));
                createCell(row, col++, nullSafe(r.sonuc()),         base);
                createCell(row, col++, nullSafe(r.specificType()),      base);
                createCell(row, col++, nullSafe(r.specificIndexName()), base);
                Cell cSpe = row.createCell(col++);
                cSpe.setCellValue(nullSafe(r.specificStatus()));
                cSpe.setCellStyle(statusStyleFor(r.specificStatus(), s.ok, s.nok, centered));
                createCell(row, col++, nullSafe(r.specificSonuc()), base);
            } else {
                createCell(row, col++, nullSafe(r.type()),      base);
                createCell(row, col++, nullSafe(r.indexName()), base);
                Cell cUat = row.createCell(col++);
                cUat.setCellValue(nullSafe(r.uatStatus()));
                cUat.setCellStyle(statusStyleFor(r.uatStatus(), s.ok, s.nok, centered));
                createCell(row, col++, nullSafe(r.sonuc()),     base);
            }
        }

        // ── Auto filter ────────────────────────────────────────────────────────
        if (currentRow > listHeaderRowIndex + 1) {
            sheet.setAutoFilter(new CellRangeAddress(
                    listHeaderRowIndex, currentRow - 1, 0, totalCols - 1));
        }

        // ── Sütun genişlikleri ─────────────────────────────────────────────────
        sheet.autoSizeColumn(0);            // A sütunu: içeriğe göre otomatik genişlik
        sheet.setColumnWidth(1, 12 * 256);
        sheet.setColumnWidth(2, 30 * 256);
        sheet.setColumnWidth(3, 45 * 256);
        sheet.setColumnWidth(4, 45 * 256);
        if (cmpMode) {
            sheet.setColumnWidth(5,  14 * 256);
            sheet.setColumnWidth(6,  20 * 256);
            sheet.setColumnWidth(7,  13 * 256);
            sheet.setColumnWidth(8,  55 * 256);
            sheet.setColumnWidth(9,  14 * 256);
            sheet.setColumnWidth(10, 14 * 256);
            sheet.setColumnWidth(11, 13 * 256);
            sheet.setColumnWidth(12, 55 * 256);
        } else {
            sheet.setColumnWidth(5,  15 * 256);
            sheet.setColumnWidth(6,  20 * 256);
            sheet.setColumnWidth(7,  12 * 256);
            sheet.setColumnWidth(8,  70 * 256);
        }
    }

    // =========================================================================
    // STİL OLUŞTURUCU — her iki entry point paylaşır
    // =========================================================================
    private static StyleBundle buildStyles(Workbook wb) {
        Font blackFont = wb.createFont();
        blackFont.setColor(IndexedColors.BLACK.getIndex());

        Font boldBlackFont = wb.createFont();
        boldBlackFont.setBold(true);
        boldBlackFont.setColor(IndexedColors.BLACK.getIndex());

        CellStyle tableHeaderStyle = wb.createCellStyle();
        tableHeaderStyle.setFont(boldBlackFont);
        tableHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
        tableHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        tableHeaderStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        tableHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(tableHeaderStyle);

        Font boldWhiteFont = wb.createFont();
        boldWhiteFont.setBold(true);
        boldWhiteFont.setColor(IndexedColors.WHITE.getIndex());
        CellStyle specificHeaderStyle = wb.createCellStyle();
        specificHeaderStyle.setFont(boldWhiteFont);
        specificHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
        specificHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        specificHeaderStyle.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        specificHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(specificHeaderStyle);

        Font titleFont = wb.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleFont.setColor(IndexedColors.BLACK.getIndex());
        CellStyle titleStyle = wb.createCellStyle();
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle whiteRowStyle = createWrapStyle(wb, IndexedColors.WHITE, blackFont);
        CellStyle greyRowStyle  = createWrapStyle(wb, IndexedColors.GREY_25_PERCENT, blackFont);

        CellStyle whiteCenteredRowStyle = wb.createCellStyle();
        whiteCenteredRowStyle.cloneStyleFrom(whiteRowStyle);
        whiteCenteredRowStyle.setAlignment(HorizontalAlignment.CENTER);
        whiteCenteredRowStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle greyCenteredRowStyle = wb.createCellStyle();
        greyCenteredRowStyle.cloneStyleFrom(greyRowStyle);
        greyCenteredRowStyle.setAlignment(HorizontalAlignment.CENTER);
        greyCenteredRowStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle okStyle  = createStatusStyle(wb, IndexedColors.LIGHT_GREEN, blackFont);
        CellStyle nokStyle = createStatusStyle(wb, IndexedColors.ROSE,        blackFont);

        CellStyle grandTotalStyle = wb.createCellStyle();
        grandTotalStyle.setFont(boldBlackFont);
        grandTotalStyle.setAlignment(HorizontalAlignment.CENTER);
        grandTotalStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        grandTotalStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        grandTotalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(grandTotalStyle);

        return new StyleBundle(
                titleStyle, tableHeaderStyle, specificHeaderStyle,
                whiteRowStyle, greyRowStyle,
                whiteCenteredRowStyle, greyCenteredRowStyle,
                okStyle, nokStyle, grandTotalStyle
        );
    }

    // =========================================================================
    // YARDIMCI — özet satırı yazar
    // =========================================================================
    private static void writeSummaryRow(Sheet sheet, int rowIndex, int counter,
                                        String label, int total, int pass, StyleBundle s) {
        int fail = total - pass;
        int rate = total == 0 ? 0 : (int) Math.round((pass * 100.0) / total);
        Row row  = sheet.createRow(rowIndex);
        row.setHeightInPoints(18f);  // tek satır yüksekliği, alta uzamasın
        boolean isEven = (counter % 2 == 0);
        CellStyle base = wb(sheet).createCellStyle();
        base.cloneStyleFrom(isEven ? s.grey : s.white);
        base.setAlignment(HorizontalAlignment.CENTER);
        base.setVerticalAlignment(VerticalAlignment.CENTER);
        base.setWrapText(false);  // sarma yok, tek satırda kalsın
        createCell(row, 0, label,      base);
        createCell(row, 1, total,      base);
        createCell(row, 2, pass,       base);
        createCell(row, 3, fail,       base);
        createCell(row, 4, "%" + rate, base);
    }

    private static Workbook wb(Sheet sheet) { return sheet.getWorkbook(); }

    // =========================================================================
    // YARDIMCI SINIF
    // =========================================================================
    private record StyleBundle(
            CellStyle title,
            CellStyle tableHeader,
            CellStyle specificHeader,
            CellStyle white,
            CellStyle grey,
            CellStyle whiteCentered,
            CellStyle greyCentered,
            CellStyle ok,
            CellStyle nok,
            CellStyle grandTotal
    ) {}

    // =========================================================================
    // YARDIMCI METOTLAR
    // =========================================================================
    private static void createCell(Row row, int index, Object value, CellStyle style) {
        Cell cell = row.createCell(index);
        if (value instanceof Integer) cell.setCellValue((Integer) value);
        else cell.setCellValue(value.toString());
        cell.setCellStyle(style);
    }

    private static CellStyle createWrapStyle(Workbook wb, IndexedColors bgColor, Font font) {
        CellStyle st = wb.createCellStyle();
        st.setWrapText(true);
        st.setVerticalAlignment(VerticalAlignment.TOP);
        st.setFont(font);
        st.setFillForegroundColor(bgColor.getIndex());
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(st);
        return st;
    }

    private static CellStyle createStatusStyle(Workbook wb, IndexedColors bgColor, Font font) {
        CellStyle st = wb.createCellStyle();
        st.setWrapText(true);
        st.setAlignment(HorizontalAlignment.CENTER);
        st.setVerticalAlignment(VerticalAlignment.CENTER);
        st.setFont(font);
        st.setFillForegroundColor(bgColor.getIndex());
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

    private static CellStyle statusStyleFor(String status, CellStyle ok, CellStyle nok, CellStyle fallback) {
        if (status == null) return fallback;
        if ("OK".equalsIgnoreCase(status))  return ok;
        if ("NOK".equalsIgnoreCase(status)) return nok;
        return fallback;
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }
}