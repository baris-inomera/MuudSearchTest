package analyzer;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Analiz sonuclarini Excel'e yazar.
 *
 * Mevcut sutunlara DOKUNULMAZ; yeni sutunlar en saga eklenir ve sadece NOK satirlari
 * doldurulur. Ayrica "AI Kosu Ozeti" adinda ikinci bir sayfa olusturulur.
 */
final class ReportWriter {

    private ReportWriter() {}

    /**
     * Sutun sirasi bilerek boyle: once KARAR (bulgu/kategori/kok neden), sonra
     * NE YAPILMALI (somut aksiyon), sonra NEDEN OLDU (analiz). Agirlikli skor
     * bloku en sagda cunku genis ve okuyucunun ilk bakmasi gereken sey degil.
     */
    private static final String[] NEW_COLS = {
            "Gercek Bulgu?",
            "Kategori",
            "Kok Neden",
            "NE YAPILMALI (aksiyon)",
            "NEDEN OLDU (analiz)",
            "Ek Sinyaller",
            "Guven",
            "Kaynak",
            "Agirlikli Skor (skor x topHitsScoreWeight)"
    };
    private static final int[] WIDTHS = {14, 18, 26, 78, 62, 40, 10, 30, 52};

    /** Eski surumlerde analiz blogu bu basliklarla baslamisti — idempotentlik icin taniyoruz. */
    private static final String[] ESKI_ILK_SUTUNLAR = {
            "Gercek Bulgu?",
            "Agirlikli Skor (skor x topHitsScoreWeight)"
    };

    static void write(ExcelReport xls,
                      Map<String, BulguAnalysis> results,
                      RunSignals signals,
                      String runSummary) {

        Workbook wb = xls.wb;
        Sheet sh = xls.sheet;

        Styles st = new Styles(wb);

        Row hdr = sh.getRow(xls.headerRow);

        // Idempotent: ayni rapor tekrar analiz edilirse yeni sutun eklemek yerine
        // mevcut analiz sutunlarinin uzerine yazilir.
        int start = -1;
        outer:
        for (int c = 0; c < hdr.getLastCellNum(); c++) {
            Cell hc = hdr.getCell(c);
            if (hc == null) continue;
            String t = ExcelReport.text(hc);
            for (String eski : ESKI_ILK_SUTUNLAR) {
                if (eski.equals(t)) { start = c; break outer; }
            }
        }
        if (start < 0) {
            start = Math.max(0, (int) hdr.getLastCellNum());
        } else {
            // Eski duzenden geliyorsa artakalan sutunlari temizle (sutun sayisi degismedi ama
            // sira degisti; yine de eski baslik kalintisi kalmasin).
            for (int c = start; c < hdr.getLastCellNum(); c++) {
                Cell hc = hdr.getCell(c);
                if (hc != null) hc.setCellValue("");
            }
        }

        for (int i = 0; i < NEW_COLS.length; i++) {
            Cell c = hdr.createCell(start + i);
            c.setCellValue(NEW_COLS[i]);
            c.setCellStyle(st.header);
            sh.setColumnWidth(start + i, WIDTHS[i] * 256);
        }

        for (ReportRow r : xls.rows) {
            if (!r.isNok()) continue;                      // SADECE NOK satirlari
            BulguAnalysis a = results.get(r.caseId);
            if (a == null) continue;

            Row row = sh.getRow(r.excelRow);
            if (row == null) row = sh.createRow(r.excelRow);

            Cell b = set(row, start, a.bulguText(), st.center);
            b.setCellStyle(switch (a.bulguText()) {
                case "EVET"  -> st.bulguEvet;
                case "HAYIR" -> st.bulguHayir;
                default      -> st.bulguIncele;
            });
            set(row, start + 1, a.kategori(), st.center);
            set(row, start + 2, a.kokNeden(), st.body);
            set(row, start + 3, a.cozum(), st.aksiyon);
            set(row, start + 4, a.devNotu(), st.body);

            // Kazanan kuraldan bagimsiz TUM sinyaller — ikincil mekanizmalar
            // (duplikasyon, sanatci varyanti, regresyon) gorunmez kalmasin.
            RowContext ctx = signals == null ? null : signals.contexts.get(r.caseId);
            String ek = "";
            if (ctx != null && !ctx.ekSinyaller.isEmpty()) {
                ek = "• " + String.join("\n• ", ctx.ekSinyaller);
            }
            set(row, start + 5, ek, st.body);

            set(row, start + 6, a.guvenText(), st.center);
            set(row, start + 7, a.kaynak(), st.center);
            set(row, start + 8, weightedBlock(r), st.mono);

            row.setHeightInPoints(satirYuksekligi(a, r, ek));
        }

        // Dondurulmus bolme YOK: raporda satirlar cok yuksek oldugu icin sabit ust blok
        // ekranin yarisini yiyor. (0,0) varsa mevcut bolmeyi de kaldirir.
        sh.createFreezePane(0, 0);

        writeSummarySheet(wb, st, signals, results, runSummary);
    }

    /**
     * Aksiyon metni artik cok satirli ("NEREDE / NE YAP / DOGRULA"), sabit 96pt yetmiyor.
     * En uzun hucreye gore yuksekligi hesapla, 320pt ile sinirla.
     */
    private static float satirYuksekligi(BulguAnalysis a, ReportRow r, String ekSinyaller) {
        int max = Math.max(gorunurSatir(a.cozum(), 78), gorunurSatir(a.devNotu(), 62));
        max = Math.max(max, gorunurSatir(ekSinyaller, 40));
        max = Math.max(max, r.hits().size());
        return Math.min(320f, Math.max(96f, 13.5f * max));
    }

    /** Metnin verilen sutun genisliginde kac gorsel satir kaplayacagini tahmin eder. */
    private static int gorunurSatir(String s, int genislik) {
        if (s == null || s.isEmpty()) return 1;
        int n = 0;
        for (String line : s.split("\n", -1)) {
            n += Math.max(1, (line.length() + genislik - 1) / genislik);
        }
        return n;
    }

    private static String weightedBlock(ReportRow r) {
        StringBuilder sb = new StringBuilder();
        for (SearchHit h : r.hits()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(String.format(Locale.ROOT, "%d. [%s] %s | %.1f x %s = %.2f",
                    h.rank(), h.indexType(), clip(h.label(), 30),
                    h.rawScore(), trimW(IndexWeights.of(h.indexType())), h.weightedScore()));
        }
        return sb.toString();
    }

    private static void writeSummarySheet(Workbook wb, Styles st, RunSignals signals,
                                          Map<String, BulguAnalysis> results, String runSummary) {
        String name = "AI Kosu Ozeti";
        int idx = wb.getSheetIndex(name);
        if (idx >= 0) wb.removeSheetAt(idx);
        Sheet s = wb.createSheet(name);
        s.setColumnWidth(0, 34 * 256);
        s.setColumnWidth(1, 120 * 256);

        int r = 0;
        r = title(s, st, r, "MUUD ARAMA — OTOMATIK BULGU ANALIZI");

        r = kv(s, st, r, "Toplam case", String.valueOf(signals.totalRows));
        r = kv(s, st, r, "NOK", String.valueOf(signals.nokRows));

        long evet = results.values().stream().filter(a -> "EVET".equals(a.bulguText())).count();
        long hayir = results.values().stream().filter(a -> "HAYIR".equals(a.bulguText())).count();
        long incele = results.values().stream().filter(BulguAnalysis::analizEdilemedi).count();
        r = kv(s, st, r, "Gercek bulgu (EVET)", String.valueOf(evet));
        r = kv(s, st, r, "Motor bulgusu degil (HAYIR)", String.valueOf(hayir));
        r = kv(s, st, r, "Analiz edilemedi (INCELENMELI)", String.valueOf(incele));
        long istisna = results.values().stream()
                .filter(a -> a.kaynak() != null && a.kaynak().startsWith("ISTISNA")).count();
        long kural = results.values().stream()
                .filter(a -> a.kaynak() != null && a.kaynak().startsWith("K") && !a.kaynak().startsWith("KURAL")).count();
        long llm = results.values().stream().filter(a -> "LLM".equals(a.kaynak())).count();
        r = kv(s, st, r, "Bilinen istisna (ekip karari)", String.valueOf(istisna));
        r = kv(s, st, r, "Kaynak", "Istisna listesi: " + istisna + " | Kural motoru: " + kural
                + " (maliyetsiz) | LLM: " + llm);
        if (istisna > 0) {
            r = kv(s, st, r, "Basari orani notu",
                    istisna + " case bilinen istisna olarak isaretli (icerik ortamda yok / beklenti hatali). "
                  + "Motor performansi degerlendirilirken bunlar NOK sayisindan dusulmeli.");
        }
        r++;

        // kategori dagilimi
        r = title(s, st, r, "KATEGORI DAGILIMI");
        Map<String, Integer> kat = new LinkedHashMap<>();
        for (String k : BulguAnalysis.KATEGORILER) kat.put(k, 0);
        results.values().forEach(a -> kat.merge(a.kategori(), 1, Integer::sum));
        for (Map.Entry<String, Integer> e : kat.entrySet()) {
            if (e.getValue() > 0) r = kv(s, st, r, e.getKey(), String.valueOf(e.getValue()));
        }
        r++;

        // kok neden gruplari
        r = title(s, st, r, "KOK NEDEN GRUPLARI (satir bazli etiketlerden)");
        Map<String, List<String>> groups = new LinkedHashMap<>();
        results.forEach((id, a) -> groups
                .computeIfAbsent(a.kokNeden() == null || a.kokNeden().isBlank() ? "(etiketsiz)" : a.kokNeden(),
                        x -> new java.util.ArrayList<>())
                .add(id));
        for (Map.Entry<String, List<String>> e : groups.entrySet().stream()
                .sorted((a, b) -> b.getValue().size() - a.getValue().size()).toList()) {
            r = kv(s, st, r, e.getKey() + "  (" + e.getValue().size() + ")",
                    String.join(", ", e.getValue()));
        }
        r++;

        // olu sinyaller
        boolean any = signals.deadPopularityByIndex.containsValue(Boolean.TRUE);
        if (any) {
            r = title(s, st, r, "VERI UYARILARI");
            for (Map.Entry<String, Boolean> e : signals.deadPopularityByIndex.entrySet()) {
                if (Boolean.TRUE.equals(e.getValue())) {
                    r = kv(s, st, r, e.getKey(),
                            "Bu index'teki TUM sonuclarda populerlik alani 0/bos. "
                          + "Siralama sinyali devre disi — veri/indexleme tarafinda incelenmeli.");
                }
            }
            r++;
        }

        // yontem notu
        r = title(s, st, r, "YONTEM");
        r = kv(s, st, r, "Agirlikli skor",
                "Raporda yazan skor o sonucun kendi index'i icindeki skorudur ve index'ler arasi "
              + "karsilastirilabilir degildir. Nihai sirayi skor x topHitsScoreWeight belirler: "
              + IndexWeights.describe() + ".");
        r = kv(s, st, r, "Model",
                "Satir analizi: " + AnalyzerConfig.rowModel()
              + " | Kosu ozeti: " + AnalyzerConfig.runModel()
              + " | temperature=0, yapisal cikti semasi zorunlu.");
        r = kv(s, st, r, "Otonomi kurali",
                "LLM 'bulgu degil' dese bile satir gizlenmez; dusuk guvenli kararlar "
              + "INCELENMELI olarak isaretlenir. Analiz edilemeyen satirlar da raporda kalir.");

        if (runSummary != null && !runSummary.isBlank()) {
            r++;
            r = title(s, st, r, "MODEL DEGERLENDIRMESI (" + AnalyzerConfig.runModel() + ")");
            Row rr = s.createRow(r++);
            Cell c = rr.createCell(0);
            c.setCellValue(runSummary);
            c.setCellStyle(st.body);
            s.addMergedRegion(new CellRangeAddress(rr.getRowNum(), rr.getRowNum(), 0, 1));
            rr.setHeightInPoints(Math.min(600f, 14f * (runSummary.split("\n").length + 2)));
        }
    }

    // ------------------------------------------------------------------ yardimcilar

    private static Cell set(Row row, int col, String val, CellStyle style) {
        Cell c = row.getCell(col);
        if (c == null) c = row.createCell(col);
        c.setCellValue(val == null ? "" : val);
        c.setCellStyle(style);
        return c;
    }

    private static int title(Sheet s, Styles st, int r, String text) {
        Row row = s.createRow(r++);
        Cell c = row.createCell(0);
        c.setCellValue(text);
        c.setCellStyle(st.header);
        Cell c2 = row.createCell(1);
        c2.setCellStyle(st.header);
        return r;
    }

    private static int kv(Sheet s, Styles st, int r, String k, String v) {
        Row row = s.createRow(r++);
        Cell a = row.createCell(0);
        a.setCellValue(k);
        a.setCellStyle(st.keyCell);
        Cell b = row.createCell(1);
        b.setCellValue(v);
        b.setCellStyle(st.body);
        int lines = Math.max(1, v.length() / 130 + 1);
        row.setHeightInPoints(Math.min(180f, 14f * lines));
        return r;
    }

    private static String clip(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    private static String trimW(double d) {
        String s = String.format(Locale.ROOT, "%.3f", d);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** Tek yerde tanimlanan hucre stilleri (POI'de stil sayisi sinirlidir, tekrar yaratma). */
    private static final class Styles {
        final CellStyle header, body, center, mono, keyCell, aksiyon,
                bulguEvet, bulguHayir, bulguIncele;

        Styles(Workbook wb) {
            Font hf = wb.createFont();
            hf.setBold(true);
            hf.setColor(IndexedColors.WHITE.getIndex());
            hf.setFontHeightInPoints((short) 10);
            hf.setFontName("Arial");

            Font bf = wb.createFont();
            bf.setFontHeightInPoints((short) 10);
            bf.setFontName("Arial");

            Font mf = wb.createFont();
            mf.setFontHeightInPoints((short) 8);
            mf.setFontName("Consolas");

            Font kf = wb.createFont();
            kf.setBold(true);
            kf.setFontHeightInPoints((short) 10);
            kf.setFontName("Arial");

            header = wb.createCellStyle();
            header.setFont(hf);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setWrapText(true);

            body = wb.createCellStyle();
            body.setFont(bf);
            body.setWrapText(true);
            body.setVerticalAlignment(VerticalAlignment.TOP);

            center = wb.createCellStyle();
            center.setFont(bf);
            center.setWrapText(true);
            center.setAlignment(HorizontalAlignment.CENTER);
            center.setVerticalAlignment(VerticalAlignment.TOP);

            mono = wb.createCellStyle();
            mono.setFont(mf);
            mono.setWrapText(true);
            mono.setVerticalAlignment(VerticalAlignment.TOP);

            keyCell = wb.createCellStyle();
            keyCell.setFont(kf);
            keyCell.setWrapText(true);
            keyCell.setVerticalAlignment(VerticalAlignment.TOP);

            // Aksiyon sutunu gorsel olarak one cikmali — okunacak ilk sey bu.
            Font af = wb.createFont();
            af.setFontHeightInPoints((short) 10);
            af.setFontName("Arial");
            af.setColor(IndexedColors.DARK_BLUE.getIndex());
            aksiyon = wb.createCellStyle();
            aksiyon.setFont(af);
            aksiyon.setWrapText(true);
            aksiyon.setVerticalAlignment(VerticalAlignment.TOP);
            aksiyon.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
            aksiyon.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            bulguEvet = fill(wb, bf, IndexedColors.ROSE);
            bulguHayir = fill(wb, bf, IndexedColors.LIGHT_GREEN);
            bulguIncele = fill(wb, bf, IndexedColors.LEMON_CHIFFON);
        }

        private static CellStyle fill(Workbook wb, Font f, IndexedColors color) {
            CellStyle s = wb.createCellStyle();
            s.setFont(f);
            s.setWrapText(true);
            s.setAlignment(HorizontalAlignment.CENTER);
            s.setVerticalAlignment(VerticalAlignment.TOP);
            s.setFillForegroundColor(color.getIndex());
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return s;
        }
    }
}
