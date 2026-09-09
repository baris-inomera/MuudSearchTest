package analyzer;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Yazilmis bir TestReport_*.xlsx dosyasini okur ve analiz sutunlarini ekler.
 *
 * ExcelTestReportWriter'a HIC dokunulmaz: analyzer, yazici calistiktan sonra
 * dosyayi acip sutun ekler. Boylece mevcut rapor mantigi bozulmaz ve ayni analyzer
 * eski raporlara da uygulanabilir.
 */
public final class ExcelReport {

    private static final DataFormatter FMT = new DataFormatter(new Locale("tr", "TR"));

    public final Path path;
    public final Workbook wb;
    public final Sheet sheet;
    public final int headerRow;
    public final Map<String, Integer> col = new LinkedHashMap<>();
    public final List<ReportRow> rows = new ArrayList<>();

    private ExcelReport(Path path, Workbook wb, Sheet sheet, int headerRow) {
        this.path = path;
        this.wb = wb;
        this.sheet = sheet;
        this.headerRow = headerRow;
    }

    public static ExcelReport open(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes));
        Sheet sh = wb.getSheetAt(0);

        int hdr = findHeaderRow(sh);
        if (hdr < 0) throw new IllegalStateException("Baslik satiri bulunamadi: " + path);

        ExcelReport r = new ExcelReport(path, wb, sh, hdr);
        Row h = sh.getRow(hdr);
        for (int c = 0; c < h.getLastCellNum(); c++) {
            String v = text(h.getCell(c));
            if (!v.isBlank()) r.col.put(norm(v), c);
        }

        int cCase = r.colOf("case id");
        int cName = r.colOf("test name");
        int cExp  = r.colOf("expected result");
        int cType = r.colOf("type");
        int cStat = r.colOf("uat status");
        int cSon  = r.colOf("sonuc");

        for (int i = hdr + 1; i <= sh.getLastRowNum(); i++) {
            Row row = sh.getRow(i);
            if (row == null) continue;
            String caseId = cCase < 0 ? "" : text(row.getCell(cCase));
            if (caseId.isBlank()) continue;
            r.rows.add(new ReportRow(i,
                    caseId,
                    cName < 0 ? "" : text(row.getCell(cName)),
                    cExp  < 0 ? "" : text(row.getCell(cExp)),
                    cType < 0 ? "" : text(row.getCell(cType)),
                    cStat < 0 ? "" : text(row.getCell(cStat)),
                    cSon  < 0 ? "" : text(row.getCell(cSon))));
        }
        return r;
    }

    public int colOf(String normalizedHeader) {
        Integer i = col.get(normalizedHeader);
        return i == null ? -1 : i;
    }

    /** Sadece caseId -> status haritasi (regresyon karsilastirmasi icin, hafif okuma). */
    public static Map<String, String> statusMap(Path path) {
        Map<String, String> out = new LinkedHashMap<>();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(Files.readAllBytes(path)))) {
            Sheet sh = wb.getSheetAt(0);
            int hdr = findHeaderRow(sh);
            if (hdr < 0) return out;
            Row h = sh.getRow(hdr);
            int cCase = -1, cStat = -1;
            for (int c = 0; c < h.getLastCellNum(); c++) {
                String v = norm(text(h.getCell(c)));
                if (v.equals("case id")) cCase = c;
                if (v.equals("uat status")) cStat = c;
            }
            if (cCase < 0 || cStat < 0) return out;
            for (int i = hdr + 1; i <= sh.getLastRowNum(); i++) {
                Row row = sh.getRow(i);
                if (row == null) continue;
                String id = text(row.getCell(cCase));
                if (!id.isBlank()) out.put(id, text(row.getCell(cStat)).trim().toUpperCase());
            }
        } catch (Exception ignored) {
            // onceki rapor okunamadi — regresyon karsilastirmasi atlanir
        }
        return out;
    }

    public void save() throws Exception {
        try (OutputStream out = Files.newOutputStream(path)) {
            wb.write(out);
        }
    }

    public void close() {
        try { wb.close(); } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------

    private static int findHeaderRow(Sheet sh) {
        int limit = Math.min(sh.getLastRowNum(), 60);
        for (int i = 0; i <= limit; i++) {
            Row r = sh.getRow(i);
            if (r == null) continue;
            String a = norm(text(r.getCell(0)));
            String b = norm(text(r.getCell(1)));
            if (a.equals("no") && b.equals("case id")) return i;
        }
        return -1;
    }

    static String text(Cell c) {
        if (c == null) return "";
        try {
            if (c.getCellType() == CellType.FORMULA) {
                return FMT.formatCellValue(c).trim();
            }
            return FMT.formatCellValue(c).trim();
        } catch (Exception e) {
            return "";
        }
    }

    /** Baslik eslemesi icin: kucuk harf, Turkce karakterleri sadelestir, bosluklari tekille. */
    static String norm(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(new Locale("tr", "TR")).trim();
        t = t.replace('ç', 'c').replace('ğ', 'g').replace('ı', 'i')
             .replace('ö', 'o').replace('ş', 's').replace('ü', 'u').replace('î', 'i');
        return t.replaceAll("\\s+", " ");
    }
}
