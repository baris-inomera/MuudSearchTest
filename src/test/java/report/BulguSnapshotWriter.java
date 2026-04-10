package report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  BulguSnapshotWriter — Top-10 Regresyon Raporu Excel Yazıcı
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  Sayfa 1 — Snapshot Detay:
 *    #  |  Case ID  |  Arama Terimi  |  Beklenen  |  Top-10 Gerçek Sonuç  |  Konum  |  Durum
 *
 *  Sayfa 2 — Özet:
 *    Toplam / İlk 3'te bulunan / Top-10'da bulunan / Hiç bulunamayan
 *
 *  Renk kodlaması (Konum sütunu):
 *    Koyu Yeşil  → 1. sırada (tam doğru)
 *    Yeşil       → 2–3. sırada
 *    Sarı        → 4–10. sırada
 *    Kırmızı     → top-10'da yok
 *    Gri         → beklenen içerik tanımlanmamış (gözlem case'i)
 *
 *  Kullanım:
 *    BulguSnapshotWriter.write(rows);
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class BulguSnapshotWriter {

    // =========================================================================
    // PUBLIC RECORD
    // =========================================================================

    /**
     * @param caseId     Bulgu ID (ör. "BULGU_001")
     * @param term       Arama terimi
     * @param expArtist  Beklenen sanatçı (boş olabilir)
     * @param expTrack   Beklenen şarkı / albüm adı (boş olabilir)
     * @param top10      Gerçek top-10 sonuçları ("Şarkı — Sanatçı" formatı)
     * @param foundAt    1-tabanlı konum; 0 = bulunamadı veya N/A
     * @param section    Bölüm etiketi (ör. "İçerik Yok", "Sıralama")
     */
    public record SnapshotRow(
            String       caseId,
            String       term,
            String       expArtist,
            String       expTrack,
            List<String> top10,
            int          foundAt,
            String       section
    ) {}

    // =========================================================================
    // ANA METOT
    // =========================================================================

    private static final DateTimeFormatter TS    = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int               TOP_N = 10;

    public static void write(List<SnapshotRow> rows) {
        if (rows == null || rows.isEmpty()) {
            System.out.println("⚠️  BulguSnapshotWriter: Yazılacak satır yok.");
            return;
        }
        String fileName = "BulguSnapshot_" + LocalDateTime.now().format(TS) + ".xlsx";
        Path   outPath  = Path.of(System.getProperty("user.dir"), fileName);

        try (Workbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);
            writeDetailSheet(wb, st, rows);
            writeSummarySheet(wb, st, rows);

            try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
                wb.write(fos);
            }
            System.out.println("📊 BulguSnapshot raporu: " + outPath.toAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("BulguSnapshot Excel yazılamadı: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // SAYFA 1: DETAY
    // =========================================================================

    private static void writeDetailSheet(Workbook wb, Styles st, List<SnapshotRow> rows) {
        Sheet sheet = wb.createSheet("Snapshot Detay");

        // ── Başlık ──────────────────────────────────────────────────────────
        Row titleRow = sheet.createRow(0);
        cell(titleRow, 0, "MUUD BULGU SNAPSHOT — Top-10 Regresyon Takipçisi  ("
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + ")", st.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));
        titleRow.setHeightInPoints(24);

        // ── Sütun başlıkları ─────────────────────────────────────────────────
        Row hdr = sheet.createRow(1);
        hdr.setHeightInPoints(20);
        cell(hdr, 0, "#",               st.colHdr);
        cell(hdr, 1, "Case ID",         st.colHdr);
        cell(hdr, 2, "Arama Terimi",    st.colHdr);
        cell(hdr, 3, "Beklenen",        st.colHdr);
        cell(hdr, 4, "Top-10 Sonuç",    st.topHdr);
        cell(hdr, 5, "Konum",           st.colHdr);

        sheet.setAutoFilter(new CellRangeAddress(1, 1, 0, 5));
        sheet.createFreezePane(4, 2);

        // ── Veri satırları ───────────────────────────────────────────────────
        int rowIdx = 2, no = 1;
        for (SnapshotRow sr : rows) {
            Row row   = sheet.createRow(rowIdx++);
            boolean even = (no % 2 == 0);
            boolean hasExpected = !sr.expTrack().isEmpty() || !sr.expArtist().isEmpty();

            cell(row, 0, String.valueOf(no),  st.center(even));
            cell(row, 1, sr.caseId(),          st.bold(even));
            cell(row, 2, sr.term(),            st.bold(even));
            cell(row, 3, buildExpected(sr),    st.data(even));
            cell(row, 4, buildTop10Text(sr),   st.listCell(even));
            cell(row, 5, posLabel(sr),         posStyle(st, sr, hasExpected));

            // Satır yüksekliği: 10 sonuç × 13pt
            row.setHeightInPoints(TOP_N * 13f);
            no++;
        }

        // ── Sütun genişlikleri ───────────────────────────────────────────────
        sheet.setColumnWidth(0,  5 * 256);   // #
        sheet.setColumnWidth(1, 14 * 256);   // Case ID
        sheet.setColumnWidth(2, 24 * 256);   // Arama Terimi
        sheet.setColumnWidth(3, 30 * 256);   // Beklenen
        sheet.setColumnWidth(4, 52 * 256);   // Top-10
        sheet.setColumnWidth(5, 12 * 256);   // Konum
    }

    // =========================================================================
    // SAYFA 2: ÖZET
    // =========================================================================

    private static void writeSummarySheet(Workbook wb, Styles st, List<SnapshotRow> rows) {
        Sheet sheet = wb.createSheet("Özet");

        // ── Başlık ──────────────────────────────────────────────────────────
        Row titleRow = sheet.createRow(0);
        cell(titleRow, 0, "MUUD BULGU SNAPSHOT — Özet  ("
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + ")", st.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));
        titleRow.setHeightInPoints(24);

        int total      = rows.size();
        long hasExp    = rows.stream().filter(r -> !r.expTrack().isEmpty() || !r.expArtist().isEmpty()).count();
        long first1    = rows.stream().filter(r -> r.foundAt() == 1).count();
        long top3      = rows.stream().filter(r -> r.foundAt() >= 1 && r.foundAt() <= 3).count();
        long top10     = rows.stream().filter(r -> r.foundAt() >= 1).count();
        long notFound  = hasExp - top10;

        int curRow = 1;
        curRow = writeSectionTitle(sheet, curRow, "Genel İstatistikler", st.sectionTitle);

        Row h = sheet.createRow(curRow++);
        cell(h, 0, "İstatistik", st.colHdr);
        cell(h, 1, "Değer",      st.colHdr);
        cell(h, 2, "Açıklama",   st.colHdr);

        Object[][] stats = {
                {"Toplam Case",           total,
                        "Koşturulan toplam bulgu sayısı"},
                {"Beklenen İçerikli Case", hasExp,
                        "Sanatçı veya şarkı beklentisi tanımlı case sayısı"},
                {"1. Sırada Bulunan",      first1 + "  (%" + pct((int) first1, (int) hasExp) + ")",
                        "Beklenen içerik tam 1. sırada gelen case sayısı"},
                {"Top-3'te Bulunan",       top3 + "  (%" + pct((int) top3, (int) hasExp) + ")",
                        "İlk 3 sonuçta bulunan case sayısı"},
                {"Top-10'da Bulunan",      top10 + "  (%" + pct((int) top10, (int) hasExp) + ")",
                        "İlk 10 sonuçta bulunan case sayısı"},
                {"Top-10'da Bulunamayan",  notFound + "  (%" + pct((int) notFound, (int) hasExp) + ")",
                        "Beklenen içerik top-10'da hiç gelmeyen case sayısı → aktif bug"},
        };
        curRow = writeDataTable(sheet, curRow, stats, st);
        curRow++;

        // ── Konum Dağılımı ────────────────────────────────────────────────────
        curRow = writeSectionTitle(sheet, curRow, "Konum Dağılımı", st.sectionTitle);
        Row h2 = sheet.createRow(curRow++);
        cell(h2, 0, "Konum Aralığı",  st.colHdr);
        cell(h2, 1, "Case Sayısı",    st.colHdr);
        cell(h2, 2, "Yorum",          st.colHdr);

        long[] dist = {
                rows.stream().filter(r -> r.foundAt() == 1).count(),
                rows.stream().filter(r -> r.foundAt() == 2 || r.foundAt() == 3).count(),
                rows.stream().filter(r -> r.foundAt() >= 4 && r.foundAt() <= 10).count(),
                notFound,
                rows.stream().filter(r -> r.foundAt() == 0
                        && r.expTrack().isEmpty() && r.expArtist().isEmpty()).count(),
        };
        String[][] labels = {
                {"1. sıra",      "Tam istenen yerde — mükemmel"},
                {"2–3. sıra",    "Üst sıralarda — kabul edilebilir"},
                {"4–10. sıra",   "Mevcut ama geride — sıralama iyileştirilebilir"},
                {"Top-10'da YOK","İçerik eksik veya çok geride → aktif bug"},
                {"N/A (Gözlem)", "Beklenen içerik tanımlanmamış — sadece top-10 gözlemleniyor"},
        };
        CellStyle[] distStyles = {
                st.posFirst, st.posTop3, st.posTop10, st.posNotFound, st.posNA
        };

        for (int i = 0; i < dist.length; i++) {
            Row row = sheet.createRow(curRow++);
            cell(row, 0, labels[i][0],                       distStyles[i]);
            Cell vc = row.createCell(1); vc.setCellValue(dist[i]); vc.setCellStyle(distStyles[i]);
            cell(row, 2, labels[i][1],                       st.data(i % 2 == 0));
        }
        curRow++;

        // ── Aktif Bug Listesi (top-10'da bulunamayanlar) ─────────────────────
        List<SnapshotRow> bugList = rows.stream()
                .filter(r -> r.foundAt() == 0
                        && (!r.expTrack().isEmpty() || !r.expArtist().isEmpty()))
                .toList();

        if (!bugList.isEmpty()) {
            curRow = writeSectionTitle(sheet, curRow,
                    "Aktif Bug Listesi — Top-10'da Bulunamayanlar (" + bugList.size() + ")", st.sectionTitle);
            Row h3 = sheet.createRow(curRow++);
            cell(h3, 0, "Case ID",     st.colHdr);
            cell(h3, 1, "Arama Terimi", st.colHdr);
            cell(h3, 2, "Beklenen",    st.colHdr);

            for (int i = 0; i < bugList.size(); i++) {
                SnapshotRow r = bugList.get(i);
                Row row = sheet.createRow(curRow++);
                boolean even = (i % 2 == 0);
                cell(row, 0, r.caseId(),        st.posNotFound);
                cell(row, 1, r.term(),           st.data(even));
                cell(row, 2, buildExpected(r),   st.data(even));
            }
        }

        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 16 * 256);
        sheet.setColumnWidth(2, 52 * 256);
    }

    // =========================================================================
    // YARDIMCI METOTLAR
    // =========================================================================

    /** Beklenen alanı "Artist — Track" veya sadece biri olarak gösterir. */
    private static String buildExpected(SnapshotRow sr) {
        String a = sr.expArtist().trim();
        String t = sr.expTrack().trim();
        if (a.isEmpty() && t.isEmpty()) return "(gözlem)";
        if (a.isEmpty()) return t;
        if (t.isEmpty()) return "[Sanatçı] " + a;
        return t + " — " + a;
    }

    /**
     * Top-10 listesini numaralı, eşleşen satırı ★ ile işaretlenmiş tek string'e çevirir.
     * ★ konumu doğrudan foundAt değerinden alınır (karakter encoding sorunlarını önler).
     * Hücre içinde \n ile alt alta görünür (wrapText=true).
     */
    private static String buildTop10Text(SnapshotRow sr) {
        StringBuilder sb = new StringBuilder();
        // foundAt 1-tabanlı; 0-tabanlı indekse çevir. Bulunamadıysa -1.
        int matchIdx = sr.foundAt() > 0 ? sr.foundAt() - 1 : -1;

        for (int i = 0; i < TOP_N; i++) {
            if (i > 0) sb.append('\n');
            String name = i < sr.top10().size() ? sr.top10().get(i) : "—";
            String mark = (i == matchIdx) ? "★ " : "  ";
            sb.append(String.format("%2d. %s%s", i + 1, mark, name));
        }
        return sb.toString();
    }

    private static String posLabel(SnapshotRow sr) {
        boolean hasExp = !sr.expTrack().isEmpty() || !sr.expArtist().isEmpty();
        if (!hasExp) return "N/A";
        if (sr.foundAt() == 0) return "YOK";
        return "#" + sr.foundAt();
    }

    private static CellStyle posStyle(Styles st, SnapshotRow sr, boolean hasExpected) {
        if (!hasExpected) return st.posNA;
        int p = sr.foundAt();
        if (p == 0)          return st.posNotFound;
        if (p == 1)          return st.posFirst;
        if (p <= 3)          return st.posTop3;
        return st.posTop10;
    }

    private static int writeSectionTitle(Sheet sheet, int rowIdx, String title, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        row.setHeightInPoints(18);
        Cell c = row.createCell(0);
        c.setCellValue(title);
        c.setCellStyle(style);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, 2));
        return rowIdx + 1;
    }

    private static int writeDataTable(Sheet sheet, int curRow, Object[][] data, Styles st) {
        for (int i = 0; i < data.length; i++) {
            Row row   = sheet.createRow(curRow++);
            boolean even = (i % 2 == 0);
            cell(row, 0, data[i][0].toString(), st.data(even));
            Cell vc = row.createCell(1); vc.setCellValue(data[i][1].toString()); vc.setCellStyle(st.center(even));
            cell(row, 2, data[i][2].toString(), st.data(even));
        }
        return curRow;
    }

    private static void cell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? "" : value);
        c.setCellStyle(style);
    }

    private static int pct(int part, int total) {
        return total == 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }

    // =========================================================================
    // STİL SINIFI
    // =========================================================================

    private static class Styles {
        final CellStyle title, sectionTitle, colHdr, topHdr;
        final CellStyle posFirst, posTop3, posTop10, posNotFound, posNA;

        private final CellStyle dataEven, dataOdd;
        private final CellStyle centerEven, centerOdd;
        private final CellStyle boldEven, boldOdd;
        private final CellStyle listEven, listOdd;

        Styles(Workbook wb) {
            Font boldWh  = f(wb, true,  12, IndexedColors.WHITE.getIndex());
            Font bold    = f(wb, true,  11, IndexedColors.BLACK.getIndex());
            Font boldDk  = f(wb, true,  10, IndexedColors.BLACK.getIndex());
            Font reg     = f(wb, false, 10, IndexedColors.BLACK.getIndex());
            Font small   = f(wb, false,  9, IndexedColors.BLACK.getIndex());
            Font boldRed = f(wb, true,  10, IndexedColors.DARK_RED.getIndex());

            title        = cs(wb, boldWh, IndexedColors.DARK_BLUE.getIndex(),        true,  true, false);
            sectionTitle = cs(wb, bold,   IndexedColors.PALE_BLUE.getIndex(),         false, true, false);
            colHdr       = cs(wb, bold,   IndexedColors.PALE_BLUE.getIndex(),         true,  true, false);
            topHdr       = cs(wb, bold,   IndexedColors.LIGHT_ORANGE.getIndex(),      true,  true, false);

            // Konum renkleri
            posFirst    = cs(wb, boldDk, IndexedColors.GREEN.getIndex(),              true, true, false);
            posTop3     = cs(wb, boldDk, IndexedColors.LIGHT_GREEN.getIndex(),        true, true, false);
            posTop10    = cs(wb, boldDk, IndexedColors.LIGHT_YELLOW.getIndex(),       true, true, false);
            posNotFound = cs(wb, boldRed,IndexedColors.ROSE.getIndex(),               true, true, false);
            posNA       = cs(wb, reg,    IndexedColors.GREY_25_PERCENT.getIndex(),    true, true, false);

            dataEven   = cs(wb, reg,   IndexedColors.GREY_25_PERCENT.getIndex(), false, true, false);
            dataOdd    = cs(wb, reg,   IndexedColors.WHITE.getIndex(),           false, true, false);
            centerEven = cs(wb, reg,   IndexedColors.GREY_25_PERCENT.getIndex(), true,  true, false);
            centerOdd  = cs(wb, reg,   IndexedColors.WHITE.getIndex(),           true,  true, false);
            boldEven   = cs(wb, bold,  IndexedColors.GREY_25_PERCENT.getIndex(), false, true, false);
            boldOdd    = cs(wb, bold,  IndexedColors.WHITE.getIndex(),           false, true, false);
            listEven   = cs(wb, small, IndexedColors.GREY_25_PERCENT.getIndex(), false, true, true);
            listOdd    = cs(wb, small, IndexedColors.WHITE.getIndex(),           false, true, true);
        }

        CellStyle data(boolean even)     { return even ? dataEven   : dataOdd;   }
        CellStyle center(boolean even)   { return even ? centerEven : centerOdd; }
        CellStyle bold(boolean even)     { return even ? boldEven   : boldOdd;   }
        CellStyle listCell(boolean even) { return even ? listEven   : listOdd;   }

        private static Font f(Workbook wb, boolean bold, int size, short color) {
            Font font = wb.createFont();
            font.setBold(bold);
            font.setFontHeightInPoints((short) size);
            font.setColor(color);
            return font;
        }

        private static CellStyle cs(Workbook wb, Font font, short bgColor,
                                    boolean centered, boolean borders, boolean wrap) {
            CellStyle cs = wb.createCellStyle();
            cs.setFont(font);
            cs.setFillForegroundColor(bgColor);
            cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cs.setWrapText(wrap);
            cs.setVerticalAlignment(VerticalAlignment.TOP);
            if (centered) {
                cs.setAlignment(HorizontalAlignment.CENTER);
                cs.setVerticalAlignment(VerticalAlignment.CENTER);
            }
            if (borders) {
                cs.setBorderTop(BorderStyle.THIN);
                cs.setBorderBottom(BorderStyle.THIN);
                cs.setBorderLeft(BorderStyle.THIN);
                cs.setBorderRight(BorderStyle.THIN);
            }
            return cs;
        }
    }
}