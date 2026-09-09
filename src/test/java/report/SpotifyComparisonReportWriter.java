package report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MUUD vs Spotify karşılaştırma Excel raporu.
 *
 * Sütun yapısı:
 *   A  → Keyword
 *   B  → MUUD Top 10  (karma, kullanıcının gördüğü sıra)
 *   C  → Spotify Top 9 (orijinal sıra, interleave)
 *   D  → Spotify Şarkılar Top 3
 *   E  → Spotify Sanatçılar Top 3
 *   F  → Spotify Albümler Top 3
 *   G  → Top1 %
 *   H  → Top2 %
 *   I  → Top3 %
 */
public class SpotifyComparisonReportWriter {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    public  static final int TOP = 10;

    private static final int TOTAL_COLS = 9;  // A-I

    private static final String NOTE =
        "Not: Spotify arama sonuclarini sirasiyla 3 tipte dondurur: sarkilar, sanatcilar ve albumler. " +
        "MUUD ise tum sonuclari tek bir karma listede skor sirasina gore getirir. Kiyaslamada MUUD'un her sonucu turune gore ayristirilarak " +
        "Spotify'in ayni kategorisindeki sirasiyla eslestiriliyor: sarkilar sarkilarla, " +
        "sanatcilar sanatcilarla, albumler albumlerle. Sagdaki 3 sutun Spotify'in kategori bazli " +
        "halini gostermektedir. " +
        "Playlist sonuclari her iki tarafta da mevcut olmakla birlikte kiyasa dahil edilmemistir; " +
        "playlist isimleri platforma ozgu ve oznel oldugu icin eslestirme yapilamamaktadir.";

    // ── Veri modeli ───────────────────────────────────────────────────────────
    public record ComparisonRow(
            String       keyword,
            List<String> muudResults,
            List<String> spotifyResults,
            int          simTop1,
            int          simTop2,
            int          simTop3,
            Set<String>  matchedMuudTop3   // hangi MUUD öğelerinin Top3'te eşleştiği
    ) {
        /** Geriye dönük uyumluluk — matchedMuudTop3 olmadan oluşturmak için. */
        public ComparisonRow(String keyword, List<String> muudResults, List<String> spotifyResults,
                             int simTop1, int simTop2, int simTop3) {
            this(keyword, muudResults, spotifyResults, simTop1, simTop2, simTop3, Collections.emptySet());
        }
    }

    // ── Ana yazıcı ────────────────────────────────────────────────────────────
    public static void write(List<ComparisonRow> rows) {
        String fileName = "SpotifyComparison_" + LocalDateTime.now().format(TS) + ".xlsx";
        Path out = Path.of(System.getProperty("user.dir"), fileName);

        try (Workbook wb = new XSSFWorkbook()) {
            Styles s = buildStyles(wb);
            writeSheet(wb, rows, s);

            try (FileOutputStream fos = new FileOutputStream(out.toFile())) {
                wb.write(fos);
            }
            System.out.println("📊 Spotify karşılaştırma raporu: " + out.getFileName());
        } catch (Exception e) {
            throw new RuntimeException("Excel yazılamadı: " + e.getMessage(), e);
        }
    }

    // ── Sheet ─────────────────────────────────────────────────────────────────
    private static void writeSheet(Workbook wb, List<ComparisonRow> rows, Styles s) {
        Sheet sheet = wb.createSheet("MUUD vs Spotify");

        int curRow = 0;

        // ── Başlık ────────────────────────────────────────────────────────────
        Row titleRow = sheet.createRow(curRow++);
        titleRow.setHeightInPoints(24f);
        Cell tc = titleRow.createCell(0);
        tc.setCellValue("MUUD vs Spotify — Arama Sonuçları Karşılaştırması");
        tc.setCellStyle(s.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));

        // ── Özet tablo ────────────────────────────────────────────────────────
        if (!rows.isEmpty()) {
            // Toplam keyword
            Row countRow = sheet.createRow(curRow++);
            countRow.setHeightInPoints(20f);
            Cell cc = countRow.createCell(0);
            cc.setCellValue("Toplam keyword: " + rows.size());
            cc.setCellStyle(s.summary);
            sheet.addMergedRegion(new CellRangeAddress(curRow - 1, curRow - 1, 0, 1));

            // OK (yeşil, A) | NOK (pembe, B)
            long okCount  = rows.stream().filter(r -> r.simTop1() == 100).count();
            long nokCount = rows.stream().filter(r -> r.simTop1() == 0).count();
            Row okRow = sheet.createRow(curRow++);
            okRow.setHeightInPoints(22f);
            cell(okRow, 0, "Top1 OK:  " + okCount + " / " + rows.size(), s.ok);
            cell(okRow, 1, "Top1 NOK: " + nokCount + " / " + rows.size(), s.nok);

            // Ortalama tablo
            Row sh = sheet.createRow(curRow++);
            sh.setHeightInPoints(18f);
            cell(sh, 0, "Benzerlik Düzeyi", s.tableHeader);
            cell(sh, 1, "Ortalama %",       s.tableHeader);

            curRow = writeLevelRow(sheet, curRow, "Top 1", rows, ComparisonRow::simTop1, s, true);
            curRow = writeLevelRow(sheet, curRow, "Top 2", rows, ComparisonRow::simTop2, s, false);
            curRow = writeLevelRow(sheet, curRow, "Top 3", rows, ComparisonRow::simTop3, s, true);

            // Metodoloji notu (özet tablonun altında)
            Row noteRow = sheet.createRow(curRow++);
            noteRow.setHeightInPoints(52f);
            Cell nc = noteRow.createCell(0);
            nc.setCellValue(NOTE);
            nc.setCellStyle(s.note);
            sheet.addMergedRegion(new CellRangeAddress(curRow - 1, curRow - 1, 0, TOTAL_COLS - 1));

            curRow++; // boş satır
        }

        // ── Detay tablo başlık satırı ─────────────────────────────────────────
        int headerIdx = curRow;
        Row hRow = sheet.createRow(curRow++);
        hRow.setHeightInPoints(32f);
        cell(hRow, 0, "Keyword",                         s.headerBlue);
        cell(hRow, 1, "MUUD Top 3\n(karma sıra)",              s.headerBlue);
        cell(hRow, 2, "Spotify Top 9\n(orijinal sıra)",        s.headerGrey);
        cell(hRow, 3, "Spotify Şarkılar Top3\n(songs)",        s.headerGreen);
        cell(hRow, 4, "Spotify Sanatçılar Top3\n(performers)", s.headerOrange);
        cell(hRow, 5, "Spotify Albümler Top3\n(albums)",       s.headerPurple);
        cell(hRow, 6, "Top1 %",                                s.headerSim);
        cell(hRow, 7, "Top2 %",                                s.headerSim);
        cell(hRow, 8, "Top3 %",                                s.headerSim);

        // ── Veri satırları ────────────────────────────────────────────────────
        int rowIdx = curRow;
        for (int r = 0; r < rows.size(); r++) {
            ComparisonRow cr = rows.get(r);

            List<String> muudTop3    = deduplicateMuud(cr.muudResults(), 3);
            List<String> spotSongs   = filterByKind(cr.spotifyResults(), "songs",      3);
            List<String> spotPerf    = filterByKind(cr.spotifyResults(), "performers", 3);
            List<String> spotAlbums  = filterByKind(cr.spotifyResults(), "albums",     3);
            List<String> spotOriginal = interleaveTop3(spotSongs, spotPerf, spotAlbums);

            int lineCount = Math.max(muudTop3.size(), spotOriginal.size());

            Row row = sheet.createRow(rowIdx++);
            row.setHeightInPoints(Math.max(lineCount * 17f, 24f));

            CellStyle base = (r % 2 == 0) ? s.white : s.grey;

            cell(row, 0, cr.keyword(),                                       s.keyword);
            richMuudCell(row, 1, muudTop3, cr.matchedMuudTop3(), base, s.muudBoldFont);
            cell(row, 2, joinResults(spotOriginal),  base);
            cell(row, 3, joinResults(spotSongs),     base);
            cell(row, 4, joinResults(spotPerf),      base);
            cell(row, 5, joinResults(spotAlbums),    base);
            simCell(row, 6, cr.simTop1(), s);
            simCell(row, 7, cr.simTop2(), s);
            simCell(row, 8, cr.simTop3(), s);
        }

        // ── Auto filter ───────────────────────────────────────────────────────
        if (rowIdx > headerIdx + 1)
            sheet.setAutoFilter(new CellRangeAddress(headerIdx, rowIdx - 1, 0, TOTAL_COLS - 1));

        // ── Sütun genişlikleri ────────────────────────────────────────────────
        sheet.setColumnWidth(0, 20 * 256);   // Keyword
        sheet.setColumnWidth(1, 45 * 256);   // MUUD Top3
        sheet.setColumnWidth(2, 45 * 256);   // Spotify Orijinal Top9
        sheet.setColumnWidth(3, 35 * 256);   // Songs
        sheet.setColumnWidth(4, 32 * 256);   // Performers
        sheet.setColumnWidth(5, 32 * 256);   // Albums
        sheet.setColumnWidth(6,  9 * 256);   // Top1
        sheet.setColumnWidth(7,  9 * 256);   // Top2
        sheet.setColumnWidth(8,  9 * 256);   // Top3
    }

    // ── MUUD listesinden ilk N unique öğeyi döndürür (duplicate atlar, listede ilerler)
    private static List<String> deduplicateMuud(List<String> items, int limit) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String item : items) {
            // [kind] prefix'i atarak title—artist çiftiyle dedup key oluştur
            String key = item.replaceFirst("^\\[[^\\]]+\\]\\s*", "").trim().toLowerCase();
            if (!key.isBlank() && seen.add(key)) {
                result.add(item);
                if (result.size() == limit) break;
            }
        }
        return result;
    }

    // ── Spotify top3'lerini interleave eder: song1 perf1 alb1 song2 perf2 alb2 song3 perf3 alb3
    private static List<String> interleaveTop3(List<String> songs, List<String> perfs, List<String> albums) {
        List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            if (i < songs.size())  out.add(songs.get(i));
            if (i < perfs.size())  out.add(perfs.get(i));
            if (i < albums.size()) out.add(albums.get(i));
        }
        return out;
    }

    // ── Spotify sonuçlarını kind'e göre filtrele ──────────────────────────────
    private static List<String> filterByKind(List<String> items, String kind, int limit) {
        return items.stream()
                .filter(item -> item.startsWith("[" + kind + "]"))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private static int writeLevelRow(Sheet sheet, int rowIdx, String label,
            List<ComparisonRow> rows,
            java.util.function.ToIntFunction<ComparisonRow> getter,
            Styles s, boolean even) {
        int avg = (int) Math.round(rows.stream().mapToInt(getter).average().orElse(0));
        Row lr = sheet.createRow(rowIdx);
        lr.setHeightInPoints(18f);
        CellStyle base = even ? s.white : s.grey;
        cell(lr, 0, label, base);
        simCell(lr, 1, avg, s);
        return rowIdx + 1;
    }

    private static void simCell(Row row, int col, int pct, Styles s) {
        CellStyle style = pct >= 60 ? s.ok : (pct >= 30 ? s.mid : s.nok);
        Cell c = row.createCell(col);
        c.setCellValue("%" + pct);
        c.setCellStyle(style);
    }

    /**
     * MUUD Top3 hücresini yazar: eşleşen satırlar bold+yeşil, eşleşmeyenler normal.
     * XSSFRichTextString ile satır bazında font uygulanır.
     */
    private static void richMuudCell(Row row, int col, List<String> items,
                                     Set<String> matched, CellStyle style, XSSFFont boldFont) {
        StringBuilder sb = new StringBuilder();
        List<int[]> boldRanges = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append("\n");
            int start = sb.length();
            sb.append(i + 1).append(". ").append(items.get(i));
            if (matched != null && matched.contains(items.get(i)))
                boldRanges.add(new int[]{start, sb.length()});
        }

        XSSFRichTextString rts = new XSSFRichTextString(sb.toString());
        for (int[] r : boldRanges)
            rts.applyFont(r[0], r[1], boldFont);

        Cell c = row.createCell(col);
        c.setCellValue(rts);
        c.setCellStyle(style);
    }

    private static String joinResults(List<String> results) {
        if (results == null || results.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(i + 1).append(". ").append(results.get(i));
        }
        return sb.toString();
    }

    // ── Stiller ───────────────────────────────────────────────────────────────
    private record Styles(
            CellStyle title, CellStyle note, CellStyle summary,
            CellStyle headerBlue, CellStyle headerGreen, CellStyle headerOrange,
            CellStyle headerPurple, CellStyle headerGrey, CellStyle headerSim,
            CellStyle tableHeader,
            CellStyle keyword, CellStyle white, CellStyle grey,
            CellStyle ok, CellStyle mid, CellStyle nok,
            XSSFFont  muudBoldFont   // eşleşen MUUD satırları için bold font
    ) {}

    private static Styles buildStyles(Workbook wb) {
        Font plain   = wb.createFont();
        Font bold    = wb.createFont(); bold.setBold(true);
        Font boldLg  = wb.createFont(); boldLg.setBold(true); boldLg.setFontHeightInPoints((short) 13);
        Font boldKw  = wb.createFont(); boldKw.setBold(true); boldKw.setColor(IndexedColors.DARK_BLUE.getIndex());
        Font noteFont = wb.createFont(); noteFont.setItalic(true); noteFont.setFontHeightInPoints((short) 9);
        // Eşleşen MUUD satırları için bold + koyu yeşil
        XSSFFont muudBoldFont = (XSSFFont) wb.createFont();
        muudBoldFont.setBold(true);
        muudBoldFont.setColor(IndexedColors.DARK_GREEN.getIndex());

        CellStyle title = wb.createCellStyle();
        title.setFont(boldLg);
        title.setAlignment(HorizontalAlignment.CENTER);
        title.setVerticalAlignment(VerticalAlignment.CENTER);
        title.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        borders(title);

        CellStyle note = wb.createCellStyle();
        note.setFont(noteFont);
        note.setWrapText(true);
        note.setVerticalAlignment(VerticalAlignment.CENTER);
        note.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
        note.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        borders(note);

        CellStyle summary = wb.createCellStyle();
        summary.setFont(bold);
        summary.setAlignment(HorizontalAlignment.CENTER);
        summary.setVerticalAlignment(VerticalAlignment.CENTER);
        summary.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        summary.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        borders(summary);

        CellStyle hBlue   = headerStyle(wb, bold, IndexedColors.PALE_BLUE);
        CellStyle hGreen  = headerStyle(wb, bold, IndexedColors.LIGHT_GREEN);
        CellStyle hOrange = headerStyle(wb, bold, IndexedColors.LIGHT_ORANGE);
        CellStyle hPurple = headerStyle(wb, bold, IndexedColors.LAVENDER);
        CellStyle hGrey   = headerStyle(wb, bold, IndexedColors.GREY_25_PERCENT);
        CellStyle hSim    = headerStyle(wb, bold, IndexedColors.GREY_25_PERCENT);
        CellStyle tHeader = headerStyle(wb, bold, IndexedColors.GREY_50_PERCENT);

        CellStyle keyword = wb.createCellStyle();
        keyword.setFont(boldKw);
        keyword.setWrapText(false);
        keyword.setVerticalAlignment(VerticalAlignment.CENTER);
        keyword.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
        keyword.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        borders(keyword);

        CellStyle white = dataStyle(wb, plain, IndexedColors.WHITE);
        CellStyle grey  = dataStyle(wb, plain, IndexedColors.GREY_25_PERCENT);
        CellStyle ok    = statusStyle(wb, bold, IndexedColors.LIGHT_GREEN);
        CellStyle mid   = statusStyle(wb, bold, IndexedColors.LIGHT_YELLOW);
        CellStyle nok   = statusStyle(wb, bold, IndexedColors.ROSE);

        return new Styles(title, note, summary,
                hBlue, hGreen, hOrange, hPurple, hGrey, hSim, tHeader,
                keyword, white, grey, ok, mid, nok, muudBoldFont);
    }

    private static CellStyle headerStyle(Workbook wb, Font font, IndexedColors bg) {
        CellStyle st = wb.createCellStyle();
        st.setFont(font);
        st.setAlignment(HorizontalAlignment.CENTER);
        st.setVerticalAlignment(VerticalAlignment.CENTER);
        st.setWrapText(true);
        st.setFillForegroundColor(bg.getIndex());
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        borders(st);
        return st;
    }

    private static CellStyle dataStyle(Workbook wb, Font font, IndexedColors bg) {
        CellStyle st = wb.createCellStyle();
        if (font != null) st.setFont(font);
        st.setWrapText(true);
        st.setVerticalAlignment(VerticalAlignment.TOP);
        st.setFillForegroundColor(bg.getIndex());
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        borders(st);
        return st;
    }

    private static CellStyle statusStyle(Workbook wb, Font font, IndexedColors bg) {
        CellStyle st = wb.createCellStyle();
        st.setFont(font);
        st.setAlignment(HorizontalAlignment.CENTER);
        st.setVerticalAlignment(VerticalAlignment.CENTER);
        st.setFillForegroundColor(bg.getIndex());
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        borders(st);
        return st;
    }

    private static void borders(CellStyle st) {
        st.setBorderTop(BorderStyle.THIN);    st.setBorderBottom(BorderStyle.THIN);
        st.setBorderLeft(BorderStyle.THIN);   st.setBorderRight(BorderStyle.THIN);
    }

    private static void cell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? "" : value);
        c.setCellStyle(style);
    }
}
