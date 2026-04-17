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
 * ─────────────────────────────────────────────────────────────────────────────
 *  BulguSnapshotWriter — 3-Sheet Regresyon & Analiz Raporu
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  Sayfa 1 — Snapshot Detay:
 *    # | Case ID | Arama Terimi | Beklenen | Top-10 Gerçek Sonuç | Konum
 *
 *  Sayfa 2 — Analiz Detayı:
 *    # | Case ID | Arama Terimi | Bulgu Açıklaması | Beklenen | Top-10 Sonuç | Konum | Otomatik Analiz | Developer Tavsiyesi
 *
 *  Sayfa 3 — Özet & Tavsiyeler:
 *    Genel istatistikler / Konum dağılımı / Aktif bug listesi
 *    Bölüm bazlı analiz / Developer öncelikleri / Yol haritası
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
     * @param caseId          Bulgu ID (ör. "BULGU_001")
     * @param term            Arama terimi
     * @param expArtist       Beklenen sanatçı (boş olabilir)
     * @param expTrack        Beklenen şarkı / albüm adı (boş olabilir)
     * @param top10           Gerçek top-10 sonuçları ("Şarkı — Sanatçı" formatı)
     * @param foundAt         1-tabanlı konum; 0 = bulunamadı veya N/A
     * @param section         Bölüm etiketi (ör. "İçerik Yok", "Sıralama")
     * @param bulgAciklamasi  UAT dokümanından orijinal bulgu açıklaması
     */
    public record SnapshotRow(
            String       caseId,
            String       term,
            String       expArtist,
            String       expTrack,
            List<String> top10,
            int          foundAt,
            String       section,
            String       bulgAciklamasi
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
            writeSuccessSheet(wb, st, rows);
            writeDetailSheet(wb, st, rows);
            writeAnalysisSheet(wb, st, rows);
            writeSummarySheet(wb, st, rows);

            try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
                wb.write(fos);
            }
            System.out.println("📊 BulguSnapshot raporu (4 sayfa): " + outPath.toAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("BulguSnapshot Excel yazılamadı: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // SAYFA 1: BAŞARI ÖZETİ (bağımsız, sabit sheet)
    // =========================================================================

    private static void writeSuccessSheet(Workbook wb, Styles st, List<SnapshotRow> rows) {
        Sheet sheet = wb.createSheet("Başarı Özeti");

        // Başlık
        Row titleRow = sheet.createRow(0);
        cell(titleRow, 0, "MUUD BULGU SNAPSHOT — Başarı Özeti  ("
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + ")", st.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));
        titleRow.setHeightInPoints(24);

        int rowIdx = writeMiniSuccessTable(sheet, st, rows, 1);

        sheet.setColumnWidth(0, 26 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        sheet.setColumnWidth(2, 16 * 256);
        sheet.setColumnWidth(3, 14 * 256);
        sheet.setColumnWidth(4, 14 * 256);
        sheet.setColumnWidth(5, 12 * 256);
    }

    // =========================================================================
    // SAYFA 2: SNAPSHOT DETAY
    // =========================================================================

    private static void writeDetailSheet(Workbook wb, Styles st, List<SnapshotRow> rows) {
        Sheet sheet = wb.createSheet("Snapshot Detay");

        // ── Başlık ────────────────────────────────────────────────────────────
        Row titleRow = sheet.createRow(0);
        cell(titleRow, 0, "MUUD BULGU SNAPSHOT — Top-10 Regresyon Takipçisi  ("
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + ")", st.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));
        titleRow.setHeightInPoints(24);

        // ── Veri başlık satırı ────────────────────────────────────────────────
        Row hdr = sheet.createRow(1);
        hdr.setHeightInPoints(20);
        cell(hdr, 0, "#",            st.colHdr);
        cell(hdr, 1, "Case ID",      st.colHdr);
        cell(hdr, 2, "Arama Terimi", st.colHdr);
        cell(hdr, 3, "Beklenen",     st.colHdr);
        cell(hdr, 4, "Top-10 Sonuç", st.topHdr);
        cell(hdr, 5, "Konum",        st.colHdr);
        sheet.setAutoFilter(new CellRangeAddress(1, 1, 0, 5));
        sheet.createFreezePane(4, 2);

        // ── Veri satırları ────────────────────────────────────────────────────
        int rowIdx = 2, no = 1;
        for (SnapshotRow sr : rows) {
            Row row = sheet.createRow(rowIdx++);
            boolean even = (no % 2 == 0);
            boolean hasExpected = !sr.expTrack().isEmpty() || !sr.expArtist().isEmpty();

            cell(row, 0, String.valueOf(no), st.center(even));
            cell(row, 1, sr.caseId(),        st.bold(even));
            cell(row, 2, sr.term(),          st.bold(even));
            cell(row, 3, buildExpected(sr),  st.data(even));
            cell(row, 4, buildTop10Text(sr), st.listCell(even));
            cell(row, 5, posLabel(sr),       posStyle(st, sr, hasExpected));

            row.setHeightInPoints(TOP_N * 13f);
            no++;
        }

        sheet.setColumnWidth(0,  5 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        sheet.setColumnWidth(2, 24 * 256);
        sheet.setColumnWidth(3, 30 * 256);
        sheet.setColumnWidth(4, 52 * 256);
        sheet.setColumnWidth(5, 12 * 256);
    }

    /**
     * Bölüm bazlı başarı tablosu yazar; son kullanılan satır indeksini döndürür.
     * Kolonlar: Bölüm | Toplam | Top-10 Bulunan | 1. Sırada | Bulunamayan | Başarı %
     */
    private static int writeMiniSuccessTable(Sheet sheet, Styles st,
                                             List<SnapshotRow> rows, int startRow) {
        int rowIdx = startRow;

        // Bölüm başlığı
        Row secTitle = sheet.createRow(rowIdx++);
        secTitle.setHeightInPoints(16);
        cell(secTitle, 0, "Başarı Özeti — Bölüm Bazlı", st.sectionTitle);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 5));

        // Tablo başlığı
        Row hdr = sheet.createRow(rowIdx++);
        hdr.setHeightInPoints(18);
        cell(hdr, 0, "Bölüm",        st.colHdr);
        cell(hdr, 1, "Toplam Case",   st.colHdr);
        cell(hdr, 2, "Top-10 Bulunan", st.colHdr);
        cell(hdr, 3, "1. Sırada",     st.colHdr);
        cell(hdr, 4, "Bulunamayan",   st.colHdr);
        cell(hdr, 5, "Başarı %",      st.colHdr);

        Map<String, List<SnapshotRow>> bySection = rows.stream()
                .collect(Collectors.groupingBy(SnapshotRow::section,
                        LinkedHashMap::new, Collectors.toList()));

        int si = 0;
        int grandTotal = 0, grandTracking = 0, grandFound = 0, grandFirst = 0;

        for (Map.Entry<String, List<SnapshotRow>> entry : bySection.entrySet()) {
            List<SnapshotRow> sec = entry.getValue();
            int total    = sec.size();
            long tracking = sec.stream().filter(r -> !r.expTrack().isEmpty() || !r.expArtist().isEmpty()).count();
            long found   = sec.stream().filter(r -> r.foundAt() >= 1).count();
            long first1  = sec.stream().filter(r -> r.foundAt() == 1).count();
            long missed  = tracking - found;
            int  pct     = pct((int) found, (int) tracking);

            boolean even = (si++ % 2 == 0);
            Row row = sheet.createRow(rowIdx++);
            cell(row, 0, entry.getKey(),     st.bold(even));
            numCell(row, 1, total,           st.center(even));
            numCell(row, 2, (int) found,     found == tracking ? st.posFirst : st.posTop3);
            numCell(row, 3, (int) first1,    first1 == tracking ? st.posFirst : st.posTop10);
            numCell(row, 4, (int) missed,    missed > 0 ? st.posNotFound : st.posFirst);
            if (tracking == 0) {
                cell(row, 5, "N/A", st.posNA);
            } else {
                numCell(row, 5, pct, pct >= 80 ? st.posFirst : (pct >= 50 ? st.posTop10 : st.posNotFound));
            }

            grandTotal    += total;
            grandTracking += (int) tracking;
            grandFound    += (int) found;
            grandFirst    += (int) first1;
        }

        // TOPLAM satırı
        Row totRow = sheet.createRow(rowIdx++);
        totRow.setHeightInPoints(18);
        cell(totRow, 0, "TOPLAM", st.sectionTitle);
        numCell(totRow, 1, grandTotal,   st.sectionTitle);
        numCell(totRow, 2, grandFound,   st.sectionTitle);
        numCell(totRow, 3, grandFirst,   st.sectionTitle);
        numCell(totRow, 4, grandTracking - grandFound, st.sectionTitle);
        numCell(totRow, 5, pct(grandFound, grandTracking), st.sectionTitle);

        return rowIdx;
    }

    // =========================================================================
    // SAYFA 2: ANALİZ DETAYI
    // =========================================================================

    private static void writeAnalysisSheet(Workbook wb, Styles st, List<SnapshotRow> rows) {
        Sheet sheet = wb.createSheet("Analiz Detayı");

        Row titleRow = sheet.createRow(0);
        cell(titleRow, 0, "MUUD BULGU ANALİZ RAPORU — Her Case için Otomatik Yorum & Developer Tavsiyesi  ("
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + ")", st.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));
        titleRow.setHeightInPoints(24);

        Row hdr = sheet.createRow(1);
        hdr.setHeightInPoints(20);
        cell(hdr, 0, "#",                   st.colHdr);
        cell(hdr, 1, "Case ID",             st.colHdr);
        cell(hdr, 2, "Arama Terimi",        st.colHdr);
        cell(hdr, 3, "Bulgu Açıklaması",    st.colHdr);
        cell(hdr, 4, "Beklenen",            st.colHdr);
        cell(hdr, 5, "Top-10 Gerçek Sonuç", st.topHdr);
        cell(hdr, 6, "Konum",               st.colHdr);
        cell(hdr, 7, "Otomatik Analiz",     st.analyzeHdr);
        cell(hdr, 8, "Developer Tavsiyesi", st.adviceHdr);

        sheet.setAutoFilter(new CellRangeAddress(1, 1, 0, 8));
        sheet.createFreezePane(5, 2);

        int rowIdx = 2, no = 1;
        for (SnapshotRow sr : rows) {
            Row row  = sheet.createRow(rowIdx++);
            boolean even = (no % 2 == 0);
            boolean hasExpected = !sr.expTrack().isEmpty() || !sr.expArtist().isEmpty();

            cell(row, 0, String.valueOf(no),       st.center(even));
            cell(row, 1, sr.caseId(),              st.bold(even));
            cell(row, 2, sr.term(),                st.bold(even));
            cell(row, 3, sr.bulgAciklamasi(),      st.wrapData(even));
            cell(row, 4, buildExpected(sr),        st.data(even));
            cell(row, 5, buildTop10Text(sr),       st.listCell(even));
            cell(row, 6, posLabel(sr),             posStyle(st, sr, hasExpected));
            cell(row, 7, autoAnalyze(sr),          st.wrapData(even));
            cell(row, 8, devRecommendation(sr),    st.wrapData(even));

            row.setHeightInPoints(TOP_N * 13f);
            no++;
        }

        sheet.setColumnWidth(0,  5 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        sheet.setColumnWidth(2, 22 * 256);
        sheet.setColumnWidth(3, 42 * 256);
        sheet.setColumnWidth(4, 28 * 256);
        sheet.setColumnWidth(5, 52 * 256);
        sheet.setColumnWidth(6, 12 * 256);
        sheet.setColumnWidth(7, 44 * 256);
        sheet.setColumnWidth(8, 44 * 256);
    }

    // =========================================================================
    // SAYFA 3: ÖZET & TAVSİYELER
    // =========================================================================

    private static void writeSummarySheet(Workbook wb, Styles st, List<SnapshotRow> rows) {
        Sheet sheet = wb.createSheet("Özet & Tavsiyeler");

        Row titleRow = sheet.createRow(0);
        cell(titleRow, 0, "MUUD BULGU ÖZET & GELİŞTİRME TAVSİYELERİ  ("
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + ")", st.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));
        titleRow.setHeightInPoints(24);

        int total     = rows.size();
        long hasExp   = rows.stream().filter(r -> !r.expTrack().isEmpty() || !r.expArtist().isEmpty()).count();
        long first1   = rows.stream().filter(r -> r.foundAt() == 1).count();
        long top3     = rows.stream().filter(r -> r.foundAt() >= 1 && r.foundAt() <= 3).count();
        long top10    = rows.stream().filter(r -> r.foundAt() >= 1).count();
        long notFound = hasExp - top10;

        int curRow = 1;

        // ── Genel İstatistikler ──────────────────────────────────────────────
        curRow = writeSectionTitle(sheet, curRow, "Genel İstatistikler", st.sectionTitle);
        Row h1 = sheet.createRow(curRow++);
        cell(h1, 0, "İstatistik", st.colHdr);
        cell(h1, 1, "Değer",      st.colHdr);
        cell(h1, 2, "Açıklama",   st.colHdr);

        Object[][] stats = {
                {"Toplam Case",            total,
                        "Koşturulan toplam bulgu sayısı"},
                {"Beklenen İçerikli Case",  hasExp,
                        "Sanatçı veya şarkı beklentisi tanımlı case sayısı"},
                {"1. Sırada Bulunan",       first1 + "  (%" + pct((int) first1, (int) hasExp) + ")",
                        "Beklenen içerik tam 1. sırada gelen case sayısı"},
                {"Top-3'te Bulunan",        top3 + "  (%" + pct((int) top3, (int) hasExp) + ")",
                        "İlk 3 sonuçta bulunan case sayısı"},
                {"Top-10'da Bulunan",       top10 + "  (%" + pct((int) top10, (int) hasExp) + ")",
                        "İlk 10 sonuçta bulunan case sayısı"},
                {"Top-10'da Bulunamayan",   notFound + "  (%" + pct((int) notFound, (int) hasExp) + ")",
                        "Beklenen içerik top-10'da hiç gelmeyen → aktif bug"},
        };
        curRow = writeDataTable(sheet, curRow, stats, st);
        curRow++;

        // ── Konum Dağılımı ────────────────────────────────────────────────────
        curRow = writeSectionTitle(sheet, curRow, "Konum Dağılımı", st.sectionTitle);
        Row h2 = sheet.createRow(curRow++);
        cell(h2, 0, "Konum Aralığı",  st.colHdr);
        cell(h2, 1, "Case Sayısı",    st.colHdr);
        cell(h2, 2, "Yorum",          st.colHdr);

        long nNA = rows.stream().filter(r -> r.foundAt() == 0 && r.expTrack().isEmpty() && r.expArtist().isEmpty()).count();
        long[] dist = {
                rows.stream().filter(r -> r.foundAt() == 1).count(),
                rows.stream().filter(r -> r.foundAt() == 2 || r.foundAt() == 3).count(),
                rows.stream().filter(r -> r.foundAt() >= 4 && r.foundAt() <= 10).count(),
                notFound,
                nNA,
        };
        String[][] distLabels = {
                {"1. sıra",       "Tam istenen yerde — mükemmel"},
                {"2–3. sıra",     "Üst sıralarda — kabul edilebilir"},
                {"4–10. sıra",    "Mevcut ama geride — sıralama iyileştirilebilir"},
                {"Top-10'da YOK", "İçerik eksik veya çok geride → aktif bug"},
                {"N/A (Gözlem)",  "Beklenen içerik tanımlanmamış — sadece top-10 gözlemleniyor"},
        };
        CellStyle[] distStyles = { st.posFirst, st.posTop3, st.posTop10, st.posNotFound, st.posNA };

        for (int i = 0; i < dist.length; i++) {
            Row row = sheet.createRow(curRow++);
            cell(row, 0, distLabels[i][0],  distStyles[i]);
            Cell vc = row.createCell(1); vc.setCellValue(dist[i]); vc.setCellStyle(distStyles[i]);
            cell(row, 2, distLabels[i][1],  st.data(i % 2 == 0));
        }
        curRow++;

        // ── Bölüm Bazlı Analiz ────────────────────────────────────────────────
        curRow = writeSectionTitle(sheet, curRow, "Bölüm Bazlı Analiz", st.sectionTitle);
        Row h3 = sheet.createRow(curRow++);
        cell(h3, 0, "Bölüm",         st.colHdr);
        cell(h3, 1, "Toplam",         st.colHdr);
        cell(h3, 2, "Bulunan",        st.colHdr);
        cell(h3, 3, "Bulunamayan",    st.colHdr);
        cell(h3, 4, "Başarı %",       st.colHdr);

        Map<String, List<SnapshotRow>> bySection = rows.stream()
                .collect(Collectors.groupingBy(SnapshotRow::section, LinkedHashMap::new, Collectors.toList()));

        int si = 0;
        for (Map.Entry<String, List<SnapshotRow>> entry : bySection.entrySet()) {
            List<SnapshotRow> secRows = entry.getValue();
            long secHasExp = secRows.stream().filter(r -> !r.expTrack().isEmpty() || !r.expArtist().isEmpty()).count();
            long secFound  = secRows.stream().filter(r -> r.foundAt() >= 1).count();
            long secMissed = secHasExp - secFound;
            int  secPct    = pct((int) secFound, (int) secHasExp);

            Row row = sheet.createRow(curRow++);
            boolean even = (si++ % 2 == 0);
            cell(row, 0, entry.getKey(),                      st.bold(even));
            numCell(row, 1, secRows.size(),                   st.center(even));
            numCell(row, 2, (int) secFound,                   secFound == secHasExp ? st.posFirst : st.posTop3);
            numCell(row, 3, (int) secMissed,                  secMissed > 0 ? st.posNotFound : st.posFirst);
            numCell(row, 4, secPct,                           secPct >= 80 ? st.posFirst : (secPct >= 50 ? st.posTop10 : st.posNotFound));
        }
        curRow++;

        // ── Aktif Bug Listesi ─────────────────────────────────────────────────
        List<SnapshotRow> bugList = rows.stream()
                .filter(r -> r.foundAt() == 0 && (!r.expTrack().isEmpty() || !r.expArtist().isEmpty()))
                .toList();

        if (!bugList.isEmpty()) {
            curRow = writeSectionTitle(sheet, curRow,
                    "Aktif Bug Listesi — Top-10'da Bulunamayanlar (" + bugList.size() + ")", st.sectionTitle);
            Row h4 = sheet.createRow(curRow++);
            cell(h4, 0, "Case ID",      st.colHdr);
            cell(h4, 1, "Arama Terimi", st.colHdr);
            cell(h4, 2, "Beklenen",     st.colHdr);
            cell(h4, 3, "Bölüm",        st.colHdr);

            for (int i = 0; i < bugList.size(); i++) {
                SnapshotRow r = bugList.get(i);
                Row row = sheet.createRow(curRow++);
                boolean even = (i % 2 == 0);
                cell(row, 0, r.caseId(),       st.posNotFound);
                cell(row, 1, r.term(),         st.data(even));
                cell(row, 2, buildExpected(r), st.data(even));
                cell(row, 3, r.section(),      st.data(even));
            }
            curRow++;
        }

        // ── Developer Geliştirme Öncelikleri ─────────────────────────────────
        curRow = writeSectionTitle(sheet, curRow, "Developer Geliştirme Öncelikleri", st.sectionTitle);
        Row h5 = sheet.createRow(curRow++);
        cell(h5, 0, "Öncelik",   st.colHdr);
        cell(h5, 1, "Sorun",     st.colHdr);
        cell(h5, 2, "Case Sayısı", st.colHdr);
        cell(h5, 3, "Öneri",     st.colHdr);

        // Compute priority categories from data
        long p0Count = rows.stream().filter(r -> r.foundAt() == 0 && !r.expTrack().isEmpty()).count();
        long p1Count = rows.stream().filter(r -> r.foundAt() == 0 && !r.expArtist().isEmpty() && r.expTrack().isEmpty()).count();
        long p2SortCount = rows.stream().filter(r -> r.foundAt() >= 4 && r.foundAt() <= 10).count();
        long p2ArtistCount = rows.stream()
                .filter(r -> r.foundAt() >= 2 && !r.top10().isEmpty()
                        && r.top10().get(0).contains("[Sanatçı]")).count();

        long topSanatciBlock = rows.stream()
                .filter(r -> r.foundAt() >= 2)
                .filter(r -> {
                    List<String> before = r.top10().subList(0, Math.min(r.foundAt() - 1, r.top10().size()));
                    return before.stream().filter(l -> l.contains("[Sanatçı]")).count() >= 2;
                }).count();

        long topAlbumBlock = rows.stream()
                .filter(r -> r.foundAt() >= 2)
                .filter(r -> {
                    List<String> before = r.top10().subList(0, Math.min(r.foundAt() - 1, r.top10().size()));
                    return before.stream().anyMatch(l -> l.contains("[Albüm]"));
                }).count();

        Object[][] priorities = {
                {"P0 — KRİTİK", "Şarkı top-10'da hiç gelmiyor",
                        p0Count, "Şarkı indeksini kontrol et, exact-match zorunlu olarak dahil et"},
                {"P1 — YÜKSEK", "Sanatçı top-10'da bulunamıyor",
                        p1Count, "Sanatçı normalleştirme ve alias eşleştirme geliştir"},
                {"P2 — ORTA",   "Sanatçı kartları şarkıların önüne geçiyor",
                        topSanatciBlock, "Sanatçı kartı priority'sini düşür, şarkı exact-match'e boost ver"},
                {"P2 — ORTA",   "Albüm sonuçları şarkıların önüne geçiyor",
                        topAlbumBlock, "Song-first ranking policy: şarkı bulunursa albüm/klibi bastır"},
                {"P2 — ORTA",   "4–10. sıralarda beklenen içerik var ama geride",
                        p2SortCount, "Exact-match skor katsayısını artır, popularity sinyalini gözden geçir"},
                {"P3 — DÜŞÜK",  "Yazım hatası toleransı zayıf (otomatik düzeltme yanlış yönlendiriyor)",
                        rows.stream().filter(r -> "Auto-Correct".equals(r.section())).count(),
                        "Fuzzy match thresholdunu düzenle, Türkçe fonetik eşleştirme ekle"},
                {"P3 — DÜŞÜK",  "Lirik arama desteği eksik",
                        rows.stream().filter(r -> "Lyric Arama".equals(r.section())).count(),
                        "Şarkı sözü indekslemesi (lyrics field) arama motoruna eklenebilir"},
        };

        String[] pColors = {"P0", "P1", "P2", "P2", "P2", "P3", "P3"};
        for (int i = 0; i < priorities.length; i++) {
            Row row = sheet.createRow(curRow++);
            boolean even = (i % 2 == 0);
            CellStyle priStyle = pColors[i].equals("P0") ? st.posNotFound
                    : pColors[i].equals("P1") ? st.posTop10
                    : pColors[i].equals("P2") ? st.posTop3
                    : st.posFirst;
            cell(row, 0, priorities[i][0].toString(), priStyle);
            cell(row, 1, priorities[i][1].toString(), st.data(even));
            numCell(row, 2, ((Number) priorities[i][2]).intValue(), st.center(even));
            cell(row, 3, priorities[i][3].toString(), st.wrapData(even));
        }
        curRow++;

        // ── Önerilen Geliştirme Yol Haritası ─────────────────────────────────
        curRow = writeSectionTitle(sheet, curRow, "Önerilen Geliştirme Yol Haritası", st.sectionTitle);
        Row h6 = sheet.createRow(curRow++);
        cell(h6, 0, "Faz",  st.colHdr);
        cell(h6, 1, "Görev", st.colHdr);
        cell(h6, 2, "Açıklama", st.colHdr);

        Object[][] roadmap = {
                {"Faz 1 (Hemen)", "Şarkı exact-match score boost",
                        "Arama terimi şarkı adıyla birebir eşleştiğinde skor katsayısını artır. En yüksek etkili iyileştirme."},
                {"Faz 1 (Hemen)", "Sanatçı kartı öncelik sıralaması",
                        "Şarkı exact-match varsa, aynı sanatçının kartını şarkının altına taşı (kartlar 2. sıraya)."},
                {"Faz 2 (Kısa)", "İçerik indeksleme iyileştirmesi",
                        "Top-10'da hiç gelmeyen şarkıları (aktif bug listesi) tek tek incele. Eksik indeks mi, normalleştirme sorunu mu?"},
                {"Faz 2 (Kısa)", "Albüm ezme sorunu giderme",
                        "Şarkı adıyla arama yapıldığında albüm sonucu şarkının önüne geçiyorsa, song-first policy uygula."},
                {"Faz 3 (Orta)", "Türkçe yazım toleransı",
                        "Otomatik düzeltme yanlış kelimeye yönlendiriyor (örn. arabam→graham, dandini→dancing). Türkçe fonetik sözlük ekle."},
                {"Faz 3 (Orta)", "Lirik arama",
                        "Kullanıcılar şarkı sözü parçasıyla arama yapıyor. Lyrics field indekslenmeli ve eşleşme sağlanmalı."},
                {"Faz 4 (Uzun)", "Playlist sıralama",
                        "90'lar, çocuk, pop, yabancı gibi kategori aramalarında ilgili çalma listeleri öne çıkarılmalı."},
        };
        for (int i = 0; i < roadmap.length; i++) {
            Row row = sheet.createRow(curRow++);
            boolean even = (i % 2 == 0);
            cell(row, 0, roadmap[i][0].toString(), st.bold(even));
            cell(row, 1, roadmap[i][1].toString(), st.bold(even));
            cell(row, 2, roadmap[i][2].toString(), st.wrapData(even));
            row.setHeightInPoints(30f);
        }
        curRow++;

        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 36 * 256);
        sheet.setColumnWidth(2, 16 * 256);
        sheet.setColumnWidth(3, 52 * 256);
        sheet.setColumnWidth(4, 14 * 256);
    }


    // =========================================================================
    // OTOMATİK ANALİZ METOTLARI
    // =========================================================================

    /**
     * Beklenen içeriğin kaçıncı sırada geldiğini ve önündeki içerik türlerini açıklar.
     */
    private static String autoAnalyze(SnapshotRow sr) {
        boolean hasExp = !sr.expArtist().isEmpty() || !sr.expTrack().isEmpty();
        if (!hasExp) return "Gözlem case'i — beklenen içerik tanımlanmamış.";

        boolean isPlaylistSearch = sr.expTrack().toLowerCase().startsWith("[playlist] ");

        if (sr.foundAt() == 0) {
            return isPlaylistSearch
                    ? "Eşleşen playlist top-10'da hiç gelmiyor → playlist indeksleme veya sıralama sorunu."
                    : "Beklenen içerik top-10'da hiç gelmiyor → aktif içerik eksikliği veya indeksleme sorunu.";
        }
        if (sr.foundAt() == 1) {
            return isPlaylistSearch
                    ? "1. sırada eşleşen playlist geliyor ✓"
                    : "1. sırada geliyor — beklenen sonuç ✓";
        }

        // 0-indexed, önündeki elemanlar: [0, foundAt-2]
        List<String> before = sr.top10().subList(0, Math.min(sr.foundAt() - 1, sr.top10().size()));

        long nSanatci  = before.stream().filter(l -> l.contains("[Sanatçı]")).count();
        long nAlbum    = before.stream().filter(l -> l.contains("[Albüm]")).count();
        long nPlaylist = before.stream().filter(l -> l.contains("[Playlist]")).count();
        long nSarki    = before.size() - nSanatci - nAlbum - nPlaylist;

        if (isPlaylistSearch) {
            List<String> parts = new ArrayList<>();
            if (nSarki    > 0) parts.add(nSarki    + " şarkı");
            if (nSanatci  > 0) parts.add(nSanatci  + " sanatçı kartı");
            if (nAlbum    > 0) parts.add(nAlbum    + " albüm");
            if (nPlaylist > 0) parts.add(nPlaylist + " başka playlist");
            String prefix = parts.isEmpty() ? "başka içerikler" : String.join(", ", parts);
            return sr.foundAt() + ". sırada eşleşen playlist geliyor — önünde: " + prefix + ".";
        }

        List<String> parts = new ArrayList<>();
        if (nSanatci  > 0) parts.add(nSanatci  + " sanatçı kartı");
        if (nAlbum    > 0) parts.add(nAlbum    + " albüm kartı");
        if (nPlaylist > 0) parts.add(nPlaylist + " playlist kartı");
        if (nSarki    > 0) parts.add(nSarki    + " farklı şarkı");

        String prefix = parts.isEmpty() ? "başka içerikler" : String.join(", ", parts);
        return sr.foundAt() + ". sırada geliyor — önünde: " + prefix + ". Sıralama iyileştirilebilir.";
    }

    /**
     * Bulgu durumuna göre developer için teknik tavsiye üretir.
     */
    private static String devRecommendation(SnapshotRow sr) {
        boolean hasExp = !sr.expArtist().isEmpty() || !sr.expTrack().isEmpty();
        if (!hasExp) return "—";

        boolean isPlaylistSearch = sr.expTrack().toLowerCase().startsWith("[playlist] ");

        if (sr.foundAt() == 1) return isPlaylistSearch
                ? "Playlist sıralaması başarılı — izleme önerilir."
                : "Mevcut sıralama başarılı — izleme önerilir.";

        if (sr.foundAt() == 0) {
            if (isPlaylistSearch) {
                long nSarki   = sr.top10().stream().filter(l -> !l.contains("[")).count();
                long nSanatci = sr.top10().stream().filter(l -> l.contains("[Sanatçı]")).count();
                if (nSarki >= 5) return "Top-10 şarkılarla dolu, playlist yok — playlist sıralama ağırlığı artırılmalı.";
                if (nSanatci >= 3) return "Sanatçı kartları baskın, playlist çıkmıyor — playlist boost eklenmeli.";
                return "Eşleşen playlist bulunamıyor — playlist adları veya indeks kontrol edilmeli.";
            }
            // Top-10'da ne var?
            long nSanatci  = sr.top10().stream().filter(l -> l.contains("[Sanatçı]")).count();
            long nAlbum    = sr.top10().stream().filter(l -> l.contains("[Albüm]")).count();
            long nPlaylist = sr.top10().stream().filter(l -> l.contains("[Playlist]")).count();

            if (nSanatci >= 5) return "Top-10 sanatçı kartlarıyla dolu — şarkı exact-match boost zorunlu.";
            if (nAlbum   >= 3) return "Albüm sonuçları baskın — song-first ranking politikası uygulanmalı.";
            if (nPlaylist>= 3) return "Playlist sonuçları baskın — exact-match şarkı önceliği eklenmeli.";
            return "İçerik indekste yok veya normalleştirme hatası — indeks ve veri kaynağı kontrol edilmeli.";
        }

        // foundAt >= 2: önündeki baskın türü bul
        List<String> before = sr.top10().subList(0, Math.min(sr.foundAt() - 1, sr.top10().size()));
        long nSanatci  = before.stream().filter(l -> l.contains("[Sanatçı]")).count();
        long nAlbum    = before.stream().filter(l -> l.contains("[Albüm]")).count();
        long nPlaylist = before.stream().filter(l -> l.contains("[Playlist]")).count();

        if (isPlaylistSearch) {
            long nSarki = before.size() - nSanatci - nAlbum - nPlaylist;
            if (nSarki > 0) return "Şarkılar playlist önüne geçiyor — arama türü tespiti iyileştirilmeli.";
            if (nSanatci > 0) return "Sanatçı kartları playlist önüne geçiyor — playlist boost eklenmeli.";
            return "Playlist " + sr.foundAt() + ". sırada geliyor — sıralama iyileştirilebilir.";
        }

        long maxType = Math.max(nSanatci, Math.max(nAlbum, nPlaylist));
        if (maxType == 0)    return "Başka şarkılar daha önde — exact-match skor katsayısı artırılmalı.";
        if (maxType == nSanatci) return "Sanatçı kartları önce geliyor — sanatçı önceliği düşürülmeli, şarkı exact-match'e boost verilmeli.";
        if (maxType == nAlbum)   return "Albüm sonuçları önce geliyor — song-first policy: şarkı varsa albümü bastır.";
        return "Playlist sonuçları önce geliyor — exact şarkı eşleşmesine öncelik tanınmalı.";
    }

    // =========================================================================
    // YARDIMCI METOTLAR
    // =========================================================================

    private static String buildExpected(SnapshotRow sr) {
        String a = sr.expArtist().trim();
        String t = sr.expTrack().trim();
        if (a.isEmpty() && t.isEmpty()) return "(gözlem)";
        if (a.isEmpty()) return t;
        if (t.isEmpty()) return "[Sanatçı] " + a;
        return t + " — " + a;
    }

    private static String buildTop10Text(SnapshotRow sr) {
        StringBuilder sb = new StringBuilder();
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
        if (p == 0) return st.posNotFound;
        if (p == 1) return st.posFirst;
        if (p <= 3) return st.posTop3;
        return st.posTop10;
    }

    private static int writeSectionTitle(Sheet sheet, int rowIdx, String title, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        row.setHeightInPoints(18);
        Cell c = row.createCell(0);
        c.setCellValue(title);
        c.setCellStyle(style);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, 4));
        return rowIdx + 1;
    }

    private static int writeDataTable(Sheet sheet, int curRow, Object[][] data, Styles st) {
        for (int i = 0; i < data.length; i++) {
            Row row  = sheet.createRow(curRow++);
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

    private static void numCell(Row row, int col, int value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static int pct(int part, int total) {
        return total == 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }

    // =========================================================================
    // STİL SINIFI
    // =========================================================================

    private static class Styles {
        final CellStyle title, sectionTitle, colHdr, topHdr, analyzeHdr, adviceHdr;
        final CellStyle posFirst, posTop3, posTop10, posNotFound, posNA;

        private final CellStyle dataEven, dataOdd;
        private final CellStyle centerEven, centerOdd;
        private final CellStyle boldEven, boldOdd;
        private final CellStyle listEven, listOdd;
        private final CellStyle wrapEven, wrapOdd;

        Styles(Workbook wb) {
            Font boldWh   = f(wb, true,  12, IndexedColors.WHITE.getIndex());
            Font bold     = f(wb, true,  11, IndexedColors.BLACK.getIndex());
            Font boldDk   = f(wb, true,  10, IndexedColors.BLACK.getIndex());
            Font reg      = f(wb, false, 10, IndexedColors.BLACK.getIndex());
            Font small    = f(wb, false,  9, IndexedColors.BLACK.getIndex());
            Font boldRed  = f(wb, true,  10, IndexedColors.DARK_RED.getIndex());
            Font boldDkBl = f(wb, true,  10, IndexedColors.DARK_BLUE.getIndex());
            Font boldGrn  = f(wb, true,  10, IndexedColors.DARK_GREEN.getIndex());

            title       = cs(wb, boldWh,   IndexedColors.DARK_BLUE.getIndex(),      true,  true, false);
            sectionTitle= cs(wb, bold,     IndexedColors.PALE_BLUE.getIndex(),       false, true, false);
            colHdr      = cs(wb, bold,     IndexedColors.PALE_BLUE.getIndex(),       true,  true, false);
            topHdr      = cs(wb, bold,     IndexedColors.LIGHT_ORANGE.getIndex(),    true,  true, false);
            analyzeHdr  = cs(wb, boldDkBl, IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex(), true, true, false);
            adviceHdr   = cs(wb, boldGrn,  IndexedColors.LIGHT_GREEN.getIndex(),     true,  true, false);

            posFirst    = cs(wb, boldDk,  IndexedColors.GREEN.getIndex(),           true, true, false);
            posTop3     = cs(wb, boldDk,  IndexedColors.LIGHT_GREEN.getIndex(),     true, true, false);
            posTop10    = cs(wb, boldDk,  IndexedColors.LIGHT_YELLOW.getIndex(),    true, true, false);
            posNotFound = cs(wb, boldRed, IndexedColors.ROSE.getIndex(),            true, true, false);
            posNA       = cs(wb, reg,     IndexedColors.GREY_25_PERCENT.getIndex(), true, true, false);

            dataEven    = cs(wb, reg,   IndexedColors.GREY_25_PERCENT.getIndex(), false, true, false);
            dataOdd     = cs(wb, reg,   IndexedColors.WHITE.getIndex(),           false, true, false);
            centerEven  = cs(wb, reg,   IndexedColors.GREY_25_PERCENT.getIndex(), true,  true, false);
            centerOdd   = cs(wb, reg,   IndexedColors.WHITE.getIndex(),           true,  true, false);
            boldEven    = cs(wb, bold,  IndexedColors.GREY_25_PERCENT.getIndex(), false, true, false);
            boldOdd     = cs(wb, bold,  IndexedColors.WHITE.getIndex(),           false, true, false);
            listEven    = cs(wb, small, IndexedColors.GREY_25_PERCENT.getIndex(), false, true, true);
            listOdd     = cs(wb, small, IndexedColors.WHITE.getIndex(),           false, true, true);
            wrapEven    = cs(wb, small, IndexedColors.GREY_25_PERCENT.getIndex(), false, true, true);
            wrapOdd     = cs(wb, small, IndexedColors.WHITE.getIndex(),           false, true, true);
        }

        CellStyle data(boolean even)     { return even ? dataEven   : dataOdd;   }
        CellStyle center(boolean even)   { return even ? centerEven : centerOdd; }
        CellStyle bold(boolean even)     { return even ? boldEven   : boldOdd;   }
        CellStyle listCell(boolean even) { return even ? listEven   : listOdd;   }
        CellStyle wrapData(boolean even) { return even ? wrapEven   : wrapOdd;   }

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