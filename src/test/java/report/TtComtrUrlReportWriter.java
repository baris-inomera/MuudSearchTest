package report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * TTComtr Detail URL geçerlilik testinin sonuçlarını Excel'e yazar.
 *
 * Sütunlar: Index | Tip | Detail Url | Tam URL | HTTP Status | Sonuç
 */
public class TtComtrUrlReportWriter {

    public record UrlResult(
            String indexName,   // İçerik türü: "Cihazlar", "Tarife", "Blog", "Yardım/Destek"
            String contentType, // Sayfa başlığı (title)
            String detailUrl,   // Kullanılmıyor (eski DDP API uyumluluğu için bırakıldı)
            String fullUrl,     // "https://bireysel.turktelekom.com.tr/..."
            int    httpStatus,  // 200, 404, -1 (bağlantı hatası) vb.
            String result       // "PASS" | "FAIL" | "SKIP"
    ) {}

    public void write(List<UrlResult> rows, String outputPath) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Detail URL Kontrolü");

            // ── stiller ──────────────────────────────────────────────────────
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle passStyle   = createResultStyle(wb, new XSSFColor(new byte[]{(byte)0xC6, (byte)0xEF, (byte)0xCE}, null)); // yeşil
            CellStyle failStyle   = createResultStyle(wb, new XSSFColor(new byte[]{(byte)0xFF, (byte)0xC7, (byte)0xCE}, null)); // kırmızı
            CellStyle skipStyle   = createResultStyle(wb, new XSSFColor(new byte[]{(byte)0xFF, (byte)0xEB, (byte)0x9C}, null)); // sarı
            CellStyle normalStyle = createNormalStyle(wb);

            // ── başlık satırı ─────────────────────────────────────────────────
            String[] headers = {"Tür", "Başlık", "-", "URL", "HTTP Status", "Sonuç"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // ── veri satırları ────────────────────────────────────────────────
            int rowNum = 1;
            for (UrlResult r : rows) {
                Row row = sheet.createRow(rowNum++);

                CellStyle resultStyle = switch (r.result()) {
                    case "PASS" -> passStyle;
                    case "FAIL" -> failStyle;
                    default     -> skipStyle;
                };

                setCell(row, 0, r.indexName(),   normalStyle);
                setCell(row, 1, r.contentType(),  normalStyle);
                setCell(row, 2, r.detailUrl(),    normalStyle);
                setCell(row, 3, r.fullUrl(),      normalStyle);
                setCell(row, 4, r.httpStatus() == -1 ? "HATA" : String.valueOf(r.httpStatus()), normalStyle);
                setCell(row, 5, r.result(),       resultStyle);
            }

            // ── özet satırı ───────────────────────────────────────────────────
            long pass = rows.stream().filter(r -> "PASS".equals(r.result())).count();
            long fail = rows.stream().filter(r -> "FAIL".equals(r.result())).count();
            long skip = rows.stream().filter(r -> "SKIP".equals(r.result())).count();

            rowNum++; // boş satır
            Row summaryRow = sheet.createRow(rowNum);
            setCell(summaryRow, 0, "TOPLAM", headerStyle);
            setCell(summaryRow, 1, rows.size() + " URL", normalStyle);
            setCell(summaryRow, 3, "PASS: " + pass + "   FAIL: " + fail + "   SKIP: " + skip, normalStyle);

            // ── kolon genişlikleri ────────────────────────────────────────────
            sheet.setColumnWidth(0, 18 * 256);  // Tür
            sheet.setColumnWidth(1, 45 * 256);  // Başlık
            sheet.setColumnWidth(2,  5 * 256);  // - (kullanılmıyor)
            sheet.setColumnWidth(3, 80 * 256);  // URL
            sheet.setColumnWidth(4, 14 * 256);  // HTTP Status
            sheet.setColumnWidth(5, 10 * 256);  // Sonuç

            sheet.createFreezePane(0, 1);

            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                wb.write(fos);
            }
        }

        System.out.println("[TtComtrUrlReport] Yazıldı: " + outputPath);
    }

    // ── stil yardımcıları ──────────────────────────────────────────────────────

    private CellStyle createHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0x1F, (byte)0x4E, (byte)0x79}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createResultStyle(XSSFWorkbook wb, XSSFColor color) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(color);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createNormalStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(false);
        return style;
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }
}
