package analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * NOK satirlarini analiz edip Excel raporuna 8 sutun ekler.
 *
 * UC KATMANLI:
 *   Katman 0 — RunSignals : deterministik olgular (agirlikli skor, devrilme, duplikasyon...)
 *   Katman 1 — KuralMotoru: kurallar.json ile SIFIR MALIYETLI etiketleme
 *   Katman 2 — LLM        : sadece kural eslesmeyen satirlar icin (API key varsa)
 *
 * Kural motoru API key olmadan da calisir. LLM tamamen opsiyoneldir.
 *
 * KULLANIM — test class'inda tek satir:
 *
 *     ExcelTestReportWriter.writeBulgu(ROWS, "reports/main-cases");
 *     BulguAnalyzer.annotateLatest("reports/main-cases");
 *
 * GUVENLIK KURALI: analyzer HICBIR kosulda testi patlatmaz.
 */
public final class BulguAnalyzer {

    private BulguAnalyzer() {}

    // ------------------------------------------------------------------ public API

    /**
     * Klasordeki en yeni TestReport_*.xlsx dosyasini dondurur; yoksa null.
     * Siralama dosya ADINA gore yapilir (mtime guvenilmez — analyzer raporu yeniden yazar).
     */
    public static Path enYeniRapor(String subDir) {
        try {
            Path dir = Path.of(subDir);
            if (!Files.isDirectory(dir)) return null;
            try (var s = Files.list(dir)) {
                return s.filter(BulguAnalyzer::raporMu)
                        .max(Comparator.comparing((Path p) -> p.getFileName().toString()))
                        .orElse(null);
            }
        } catch (Exception e) {
            log("enYeniRapor hatasi: " + e);
            return null;
        }
    }

    /**
     * Rapor dosyasi olarak kabul edilen xlsx'leri filtreler.
     * Kural: .xlsx ile bitmeli, Excel temp dosyasi (~$) olmamali,
     * ve "_PROD.xlsx" / "_UAT.xlsx" / "_LOCAL.xlsx" gibi ortam etiketi icermeli.
     * Bu sayede "prod.xlsx" veya "uat.xlsx" gibi elle konulan dosyalar
     * ortam etiket formatina uymadigi icin annotateLatest() tarafindan atlanir.
     * Prefix (TestReport_, AramaKalitesiTest_, vb.) artik kisitlanmiyor.
     */
    private static boolean raporMu(Path p) {
        String ad = p.getFileName().toString();
        String lower = ad.toLowerCase();
        if (!lower.endsWith(".xlsx") || ad.startsWith("~$")) return false;
        // Ortam etiketi icermeli: _PROD.xlsx, _UAT.xlsx, _LOCAL.xlsx, _UAT-HMUUD.xlsx
        return lower.endsWith("_prod.xlsx")
                || lower.endsWith("_uat.xlsx")
                || lower.endsWith("_local.xlsx")
                || lower.contains("_uat-") && lower.endsWith(".xlsx");
    }

    /** Verilen klasordeki EN YENI TestReport_*.xlsx dosyasini analiz eder (ortam ayrimi yok). */
    public static void annotateLatest(String subDir) {
        annotateLatest(subDir, null);
    }

    /**
     * Verilen klasordeki EN YENI raporu analiz eder. ortam verilirse yalnizca
     * o ortamin raporlari dikkate alinir (TestReport_..._PROD.xlsx gibi).
     *
     * Neden ortam ayrimi: "onceki kosu" karsilastirmasi regresyon sutununu uretiyor.
     * PROD raporu UAT raporuyla karsilastirilirsa iki ortam arasindaki her fark
     * regresyon gibi gorunur. Ayni ortamin onceki kosusu dogru referanstir.
     */
    public static void annotateLatest(String subDir, String ortam) {
        try {
            Path dir = Path.of(subDir);
            if (!Files.isDirectory(dir)) {
                log("Klasor yok, analiz atlandi: " + dir.toAbsolutePath());
                return;
            }
            final String son = (ortam == null || ortam.isBlank())
                    ? null
                    : ("_" + ortam.trim().toUpperCase(java.util.Locale.ROOT) + ".xlsx")
                        .toLowerCase(java.util.Locale.ROOT);
            List<Path> reports;
            try (var s = Files.list(dir)) {
                // Timestamp kismina gore sirala: dosya adi <Prefix>_dd.MM.yyyy_HH.mm_ORTAM.xlsx
                // formatinda oldugu icin ilk '_' sonrasini karsilastirmak prefix'ten bagimsiz
                // kronolojik sira verir. mtime kullanilamaz: analyzer raporu yeniden yazdigi
                // icin eski bir raporun mtime'i yeni bir rapordan sonra gelebilir.
                reports = s.filter(BulguAnalyzer::raporMu)
                           .filter(p -> son == null
                                     || p.getFileName().toString().toLowerCase().endsWith(son))
                           .sorted(Comparator.comparing((Path p) -> {
                               String n = p.getFileName().toString();
                               int i = n.indexOf('_');
                               return i >= 0 ? n.substring(i) : n;
                           }).reversed())
                           .toList();
            }
            if (reports.isEmpty()) {
                log("Klasorde " + (son == null ? "" : ortam.toUpperCase(java.util.Locale.ROOT) + " ")
                        + "raporu yok: " + dir);
                return;
            }
            log("Analiz edilecek rapor: " + reports.get(0).getFileName()
                    + (reports.size() > 1 ? "  (onceki: " + reports.get(1).getFileName() + ")" : ""));
            annotate(reports.get(0), reports.size() > 1 ? reports.get(1) : null);
        } catch (Exception e) {
            log("annotateLatest hatasi: " + e);
        }
    }

    /**
     * Raporun hangi ortamdan geldigini belirler ve IndexWeights'e bildirir.
     * Kural metinlerindeki {ortam} / {popAlani} / {tabanFormul} yer tutucularini
     * bu bilgi besliyor — yanlis ortam, PROD raporunda UAT olcumu basilmasina yol acar.
     *
     * 1) Dosya adi:  TestReport_<zaman>_PROD.xlsx  /  _UAT.xlsx
     * 2) Icerik:     "Sonuc" metninde `prodPopularity=` varsa UAT,
     *                yalnizca `popularity=` varsa PROD (itemDesc ortama gore aliani secer)
     * 3) Ikisi de belirsizse UAT varsayilir ve uyari loglanir.
     */
    private static void ortamiBelirle(Path report, ExcelReport xls) {
        String ad = report.getFileName().toString().toUpperCase(java.util.Locale.ROOT);
        if (ad.endsWith("_PROD.XLSX")) {
            IndexWeights.ortamAyarla("PROD");
            log("Ortam: PROD (dosya adindan)");
            return;
        }
        if (ad.endsWith("_UAT.XLSX")) {
            IndexWeights.ortamAyarla("UAT");
            log("Ortam: UAT (dosya adindan)");
            return;
        }
        int prodPop = 0, sadePop = 0;
        for (ReportRow r : xls.rows) {
            String t = r.sonuc == null ? "" : r.sonuc;
            if (t.contains("prodPopularity=")) prodPop++;
            else if (t.contains("popularity=")) sadePop++;
            if (prodPop + sadePop >= 40) break;
        }
        if (prodPop > sadePop && prodPop > 0) {
            IndexWeights.ortamAyarla("UAT");
            log("Ortam: UAT (icerikten — prodPopularity alani goruldu)");
        } else if (sadePop > 0) {
            IndexWeights.ortamAyarla("PROD");
            log("Ortam: PROD (icerikten — yalnizca popularity alani goruldu)");
        } else {
            IndexWeights.ortamAyarla("UAT");
            log("UYARI: ortam belirlenemedi, UAT varsayildi. Rapor adi '..._PROD.xlsx' / "
                    + "'..._UAT.xlsx' ile bitmiyor ve icerikte populerlik alani bulunamadi.");
        }
    }

    public static void annotate(Path report, Path previousReport) {
        ExcelReport xls = null;
        try {
            xls = ExcelReport.open(report);
            ortamiBelirle(report, xls);

            Map<String, String> prev = previousReport == null
                    ? null : ExcelReport.statusMap(previousReport);

            RunSignals signals = RunSignals.compute(xls.rows, prev);
            List<ReportRow> noks = xls.rows.stream().filter(ReportRow::isNok).toList();

            log("Rapor: " + report.getFileName()
                    + " | toplam " + xls.rows.size() + " case, " + noks.size() + " NOK"
                    + (previousReport == null ? "" : " | onceki: " + previousReport.getFileName()));

            if (noks.isEmpty()) {
                log("NOK yok, analiz gerekmiyor.");
                return;
            }

            Map<String, BulguAnalysis> results = analyzeAll(noks, signals);
            String runSummary = AnalyzerConfig.runSummaryEnabled()
                    ? RunSummarizer.summarize(signals, results, xls.rows) : "";

            ReportWriter.write(xls, results, signals, runSummary);
            xls.save();
            log("Analiz yazildi: " + report.toAbsolutePath());

        } catch (Exception e) {
            log("Analiz basarisiz (test kosusu etkilenmedi): " + e);
        } finally {
            if (xls != null) xls.close();
        }
    }

    /** Tek satir analizi (kosu geneli baglam olmadan). */
    public static BulguAnalysis analyze(ReportRow row) {
        RunSignals s = RunSignals.compute(List.of(row), null);
        RowContext ctx = s.contexts.get(row.caseId);
        List<String> facts = s.facts.getOrDefault(row.caseId, List.of());
        String kanit = String.join(" | ", facts);

        BulguAnalysis istisna = new IstisnaListesi().bak(row.caseId, kanit);
        if (istisna != null) return sonlandir(istisna, ctx);

        BulguAnalysis kural = new KuralMotoru().uygula(ctx, kanit);
        if (kural != null) return sonlandir(kural, ctx);

        if (!AnalyzerConfig.enabled()) {
            return BulguAnalysis.incelenmeli(row.caseId, kanit, "kural eslesmedi, API key yok");
        }
        return sonlandir(llmIle(row, facts, s.globalFactsBlock(), new ClaudeClient(), kanit), ctx);
    }

    // ------------------------------------------------------------------ ic isleyis

    private static Map<String, BulguAnalysis> analyzeAll(List<ReportRow> noks, RunSignals signals) {
        Map<String, BulguAnalysis> out = new LinkedHashMap<>();

        // ---- ISTISNA LISTESI: ekibin kalici kararlari, her seyden once ----
        IstisnaListesi istisnalar = new IstisnaListesi();
        KuralMotoru motor = new KuralMotoru();
        List<ReportRow> kalanlar = new ArrayList<>();
        int istisnaSayisi = 0, kuralSayisi = 0;

        for (ReportRow r : noks) {
            RowContext ctx = signals.contexts.get(r.caseId);
            String kanit = String.join(" | ", signals.facts.getOrDefault(r.caseId, List.of()));

            BulguAnalysis a = istisnalar.bak(r.caseId, kanit);
            if (a != null) { out.put(r.caseId, sonlandir(a, ctx)); istisnaSayisi++; continue; }

            a = ctx == null ? null : motor.uygula(ctx, kanit);
            if (a != null) { out.put(r.caseId, sonlandir(a, ctx)); kuralSayisi++; }
            else kalanlar.add(r);
        }
        log("Bilinen istisna: " + istisnalar.aktifSayisi() + " kayitli, " + istisnaSayisi + " satir eslesti.");
        log("Kural motoru: " + motor.kuralSayisi() + " kural yuklendi, "
                + kuralSayisi + "/" + noks.size() + " satir maliyetsiz etiketlendi.");

        if (kalanlar.isEmpty()) return out;

        // ---- KATMAN 2: LLM (sadece kural eslesmeyenler) ----
        if (!AnalyzerConfig.enabled()) {
            log("Kalan " + kalanlar.size() + " satir icin API key yok — INCELENMELI olarak isaretlendi.");
            for (ReportRow r : kalanlar) {
                out.put(r.caseId, BulguAnalysis.incelenmeli(r.caseId,
                        String.join(" | ", signals.facts.getOrDefault(r.caseId, List.of())),
                        "kural eslesmedi, API key yok"));
            }
            return out;
        }

        AnalysisCache cache = new AnalysisCache(Path.of(AnalyzerConfig.cachePath()));
        String global = signals.globalFactsBlock();
        ClaudeClient client = new ClaudeClient();

        List<ReportRow> todo = new ArrayList<>();
        int hit = 0;
        for (ReportRow r : kalanlar) {
            BulguAnalysis cached = cache.get(AnalysisCache.signature(r), r.caseId);
            if (cached != null) { out.put(r.caseId, sonlandir(cached, signals.contexts.get(r.caseId))); hit++; }
            else todo.add(r);
        }
        log("Onbellek: " + hit + " satir hazir, " + todo.size() + " satir LLM'e gidiyor.");

        int threads = Math.max(1, Math.min(AnalyzerConfig.concurrency(), 8));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Map<String, Future<BulguAnalysis>> futures = new LinkedHashMap<>();
        try {
            for (ReportRow r : todo) {
                List<String> facts = signals.facts.getOrDefault(r.caseId, List.of());
                String kanit = String.join(" | ", facts);
                Callable<BulguAnalysis> task = () -> llmIle(r, facts, global, client, kanit);
                futures.put(r.caseId, pool.submit(task));
            }
            for (Map.Entry<String, Future<BulguAnalysis>> e : futures.entrySet()) {
                BulguAnalysis a;
                try {
                    a = e.getValue().get(AnalyzerConfig.timeoutSeconds() + 30L, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    a = BulguAnalysis.incelenmeli(e.getKey(),
                            String.join(" | ", signals.facts.getOrDefault(e.getKey(), List.of())),
                            shorten(ex));
                }
                out.put(e.getKey(), sonlandir(a, signals.contexts.get(e.getKey())));
            }
        } finally {
            pool.shutdownNow();
        }

        for (ReportRow r : todo) {
            BulguAnalysis a = out.get(r.caseId);
            if (a != null) cache.put(AnalysisCache.signature(r), a);
        }
        cache.flush();

        long failed = out.values().stream().filter(BulguAnalysis::analizEdilemedi).count();
        if (failed > 0) log("UYARI: " + failed + " satir analiz edilemedi, INCELENMELI olarak isaretlendi.");
        return out;
    }

    /** Regresyon gibi capraz olgular her iki kaynak icin de sonda uygulanir. */
    private static BulguAnalysis sonlandir(BulguAnalysis a, RowContext ctx) {
        if (ctx == null || !ctx.regresyon) return a;
        return a.withCozum("[REGRESYON — bu case onceki kosuda OK idi] " + a.cozum());
    }

    private static BulguAnalysis llmIle(ReportRow row, List<String> facts,
                                        String global, ClaudeClient client, String kanit) {
        try {
            JsonNode in = client.structured(
                    AnalyzerConfig.rowModel(),
                    PromptBuilder.SYSTEM,
                    PromptBuilder.userMessage(row, facts, global),
                    "bulgu_analizi",
                    "NOK test satiri icin bulgu analizi sonucunu dondur.",
                    rowSchema(),
                    1200);

            String kategori = in.path("kategori").asText("RANKING").trim().toUpperCase();
            if (!BulguAnalysis.KATEGORILER.contains(kategori)) kategori = "RANKING";

            double guven = clamp(in.path("guven").asDouble(0.0));
            boolean bulgu = in.path("bulgu").asBoolean(true);
            String cozum = in.path("cozum").asText("").trim();

            BulguAnalysis a = new BulguAnalysis(row.caseId, bulgu, kategori, cozum,
                    in.path("devNotu").asText("").trim(), guven,
                    in.path("kokNeden").asText("").trim(), "LLM", kanit, false);

            // Otonomi kurali: dusuk guvenli "bulgu degil" karari satiri asla kapatmaz.
            if (!bulgu && guven < AnalyzerConfig.confidenceThreshold()) {
                a = a.yukselt("[DUSUK GUVEN — insan incelemesi onerilir] " + cozum);
            }
            return a;

        } catch (Exception e) {
            return BulguAnalysis.incelenmeli(row.caseId, kanit, shorten(e));
        }
    }

    /** LLM'in uymak ZORUNDA oldugu cikti semasi. */
    static ObjectNode rowSchema() {
        var M = ClaudeClient.mapper();
        ObjectNode props = M.createObjectNode();

        props.set("bulgu", desc(M, "boolean",
                "Gercek bir arama motoru bulgusu mu? Test/veri kaynakliysa false."));

        ObjectNode kat = M.createObjectNode();
        kat.put("type", "string");
        ArrayNode en = M.createArrayNode();
        BulguAnalysis.KATEGORILER.forEach(en::add);
        kat.set("enum", en);
        kat.put("description", "Bulgu kategorisi.");
        props.set("kategori", kat);

        props.set("cozum", desc(M, "string",
                "AKSIYON — rapordaki 'NE YAPILMALI' sutununa yazilir. Su uc parcayi ICERMEK ZORUNDA, "
              + "her biri yeni satirda: 'NEREDE: <index tanimi adi ve id>', "
              + "'NE YAP: <alan adi>: <mevcut deger> -> <onerilen deger>', "
              + "'DOGRULA: <hangi sorgu kosulunca ne gozlenmeli>'. "
              + "Gerekirse 'YAPMA: <yapilmamasi gereken sey ve nedeni>' ekle. "
              + "'Gozden gecirin', 'iyilestirilmeli', 'optimize edilmeli' gibi ifadeler YASAK — "
              + "somut alan adi ve deger yazamiyorsan bunu acikca soyle."));
        props.set("devNotu", desc(M, "string",
                "ANALIZ — rapordaki 'NEDEN OLDU' sutununa yazilir. Mekanizmayi anlat: hangi skor terimi, "
              + "hangi agirlik, hangi ayar bu sonucu uretti. Aksiyon YAZMA, o cozum alanina ait."));

        ObjectNode g = M.createObjectNode();
        g.put("type", "number");
        g.put("minimum", 0);
        g.put("maximum", 1);
        g.put("description", "Karara guven, 0.0-1.0.");
        props.set("guven", g);

        props.set("kokNeden", desc(M, "string",
                "Bu satirin ait oldugu mekanizma, 3-6 kelime. Ayni mekanizmadaki satirlar ayni metni almali."));

        ObjectNode schema = M.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", props);
        ArrayNode req = M.createArrayNode();
        req.add("bulgu"); req.add("kategori"); req.add("cozum");
        req.add("devNotu"); req.add("guven"); req.add("kokNeden");
        schema.set("required", req);
        return schema;
    }

    private static ObjectNode desc(com.fasterxml.jackson.databind.ObjectMapper M,
                                   String type, String description) {
        ObjectNode n = M.createObjectNode();
        n.put("type", type);
        n.put("description", description);
        return n;
    }

    private static double clamp(double d) {
        if (Double.isNaN(d)) return 0.0;
        return Math.max(0.0, Math.min(1.0, d));
    }

    private static long mtime(Path p) {
        try { return Files.getLastModifiedTime(p).toMillis(); } catch (Exception e) { return 0L; }
    }

    private static String shorten(Exception e) {
        String m = e.getMessage();
        if (m == null) m = e.getClass().getSimpleName();
        return m.length() > 160 ? m.substring(0, 160) + "..." : m;
    }

    static void log(String msg) {
        System.out.println("[BulguAnalyzer] " + msg);
    }

    // ------------------------------------------------------------------ CLI

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Kullanim: BulguAnalyzer <rapor.xlsx | reports/klasoru> [onceki-rapor.xlsx]");
            return;
        }
        File f = new File(args[0]);
        if (f.isDirectory()) annotateLatest(args[0]);
        else annotate(f.toPath(), args.length > 1 ? Path.of(args[1]) : null);
    }
}
