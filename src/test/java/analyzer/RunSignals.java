package analyzer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KATMAN 0 — LLM'siz, deterministik sinyaller.
 *
 * Buradaki hicbir sey LLM tahminine birakilmaz: agirlikli skor, sira devrilmesi,
 * duplikasyon, olu populerlik alani gibi olgular hesapla bulunur.
 *
 * Iki tuketici var:
 *   - KuralMotoru  -> RowContext (yapisal) uzerinde calisir, LLM'siz karar verir
 *   - PromptBuilder -> facts (insan okunur) halini LLM'e verir
 * Ikisi de AYNI olgu kumesini gorur.
 */
public final class RunSignals {

    // ---------- kosu geneli ----------
    public final Map<String, Boolean> deadPopularityByIndex = new TreeMap<>();
    public final Map<String, Integer> hitCountByIndex = new TreeMap<>();
    public final Set<String> seenIndexTypes = new LinkedHashSet<>();
    public int totalRows, nokRows;
    /** kategori -> [toplam, nok] */
    public final Map<String, int[]> byType = new LinkedHashMap<>();

    // ---------- satir bazli ----------
    /** caseId -> insan okunur olgu satirlari (LLM icin) */
    public final Map<String, List<String>> facts = new LinkedHashMap<>();
    /** caseId -> yapisal olgular (kural motoru icin) */
    public final Map<String, RowContext> contexts = new LinkedHashMap<>();

    /** testName icindeki tirnak ici sorguyu yakalar: "a canim" arama yapilir */
    private static final Pattern QUERY = Pattern.compile("\"([^\"]+)\"");

    public static RunSignals compute(List<ReportRow> rows, Map<String, String> previousStatus) {
        RunSignals s = new RunSignals();

        Map<String, double[]> popMinMax = new TreeMap<>(); // idx -> [min, max, nonNullCount]

        for (ReportRow r : rows) {
            s.totalRows++;
            if (r.isNok()) s.nokRows++;
            int[] agg = s.byType.computeIfAbsent(r.type, k -> new int[2]);
            agg[0]++;
            if (r.isNok()) agg[1]++;

            for (SearchHit h : r.hits()) {
                s.seenIndexTypes.add(h.indexType());
                s.hitCountByIndex.merge(h.indexType(), 1, Integer::sum);
                if (h.popValue() != null) {
                    double[] mm = popMinMax.computeIfAbsent(h.indexType(),
                            k -> new double[]{Double.MAX_VALUE, -Double.MAX_VALUE, 0});
                    mm[0] = Math.min(mm[0], h.popValue());
                    mm[1] = Math.max(mm[1], h.popValue());
                    mm[2]++;
                }
            }
        }

        for (String idx : s.seenIndexTypes) {
            double[] mm = popMinMax.get(idx);
            boolean dead = (mm == null) || (mm[2] > 0 && mm[0] == 0.0 && mm[1] == 0.0);
            s.deadPopularityByIndex.put(idx, dead);
        }

        for (ReportRow r : rows) {
            if (!r.isNok()) continue;
            RowContext ctx = buildContext(r, s, previousStatus);
            s.contexts.put(r.caseId, ctx);
            s.facts.put(r.caseId, toFacts(ctx));
        }
        return s;
    }

    // ------------------------------------------------------------------

    private static RowContext buildContext(ReportRow r, RunSignals s, Map<String, String> prev) {
        RowContext c = new RowContext();
        c.caseId = r.caseId;
        c.testTipi = r.type;
        c.hits = r.hits();

        Matcher qm = QUERY.matcher(r.testName);
        c.sorgu = qm.find() ? qm.group(1).trim() : r.testName.trim();

        c.beklenenSira = r.expectedFoundRank();
        c.bulunamadi = c.beklenenSira <= 0;

        // API/altyapi hatasi: bu satir bir arama bulgusu degil, kosu hatasidir.
        String detN = ExcelReport.norm(r.sonuc);
        c.apiHatasi = detN.contains("api hatasi")
                   || detN.contains("failed to parse")
                   || detN.contains("bos sonuc")
                   || detN.contains("timeout")
                   || detN.contains("connection refused");

        if (c.hits.isEmpty()) { ekSinyalleriTopla(c); return c; }

        c.ilkIndex = c.hits.get(0).indexType();

        Set<String> idxs = new LinkedHashSet<>();
        for (SearchHit h : c.hits) idxs.add(h.indexType());
        c.indexCesidi = idxs.size();

        // agirlik devrilmesi — ilk ornegi al
        outer:
        for (int i = 0; i < c.hits.size(); i++) {
            for (int j = i + 1; j < c.hits.size(); j++) {
                SearchHit up = c.hits.get(i), down = c.hits.get(j);
                if (up.indexType().equals(down.indexType())) continue;
                if (down.rawScore() > up.rawScore()) {
                    c.agirlikDevrilmesi = true;
                    c.devrilenUstIndex = up.indexType();
                    c.devrilenAltIndex = down.indexType();
                    break outer;
                }
            }
        }

        // duplikasyon
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (SearchHit h : c.hits) {
            seen.merge((h.indexType() + "|" + h.label()).toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        for (int v : seen.values()) if (v > 1) { c.duplikasyon = true; break; }

        // populerlik
        for (SearchHit h : c.hits) {
            if (h.popMissing()) c.populerlikEksik++;
            if (Boolean.TRUE.equals(s.deadPopularityByIndex.get(h.indexType()))) {
                c.oluPopulerlikIndexleri.add(h.indexType());
            }
        }

        // sorguyla birebir ayni adli sonuc sayisi (tier / exact bonusu gostergesi)
        String q = ExcelReport.norm(c.sorgu);
        if (!q.isEmpty()) {
            for (SearchHit h : c.hits) {
                String lbl = ExcelReport.norm(h.label());
                // "Phonk - Castle." gibi etiketlerde ad kismini al
                int dash = lbl.indexOf(" — ");
                String ad = dash > 0 ? lbl.substring(0, dash).trim() : lbl;
                if (ad.equals(q)) {
                    c.birebirBaslikEslesmesi++;
                    if (h.rank() == 1) c.ilkSonucSorguylaAyni = true;
                }
            }
        }

        // beklenen sonucun ustunde performer var mi
        int limit = c.beklenenSira > 0 ? Math.min(c.beklenenSira - 1, c.hits.size()) : c.hits.size();
        for (int i = 0; i < limit; i++) {
            if ("performers".equals(c.hits.get(i).indexType())) { c.ustteperformerVar = true; break; }
        }

        // ayni sanatcinin varyant kayitlari (solo / "&" / virgullu collab / harf
        // buyuklugu farkli kopya) top-N'de kac slot yiyor
        Map<String, Integer> varyant = new LinkedHashMap<>();
        for (SearchHit h : c.hits) {
            if (!"performers".equals(h.indexType())) continue;
            varyant.merge(sanatciTabani(h.label()), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : varyant.entrySet()) {
            if (e.getValue() > c.sanatciVaryantSlot) {
                c.sanatciVaryantSlot = e.getValue();
                c.sanatciVaryantAdi = e.getKey();
            }
        }

        c.sorguKelimeSayisi = c.sorgu.trim().isEmpty() ? 0 : c.sorgu.trim().split("\\s+").length;
        c.tekIndexBaskini = c.indexCesidi == 1;

        // POPULERLIK KARSILASTIRMASI: hedef, ustunde kalan en populer kayittan
        // daha mi populer? Rapor artik prodPopularity bastigi icin bu olculebiliyor.
        if (c.beklenenSira > 0 && c.beklenenSira <= c.hits.size()) {
            SearchHit hedef = c.hits.get(c.beklenenSira - 1);
            SearchHit enPopulerUst = null;
            for (int i = 0; i < c.beklenenSira - 1 && i < c.hits.size(); i++) {
                SearchHit h = c.hits.get(i);
                if (h.popValue() == null) continue;
                if (enPopulerUst == null || h.popValue() > enPopulerUst.popValue()) enPopulerUst = h;
            }
            if (hedef.popValue() != null && enPopulerUst != null) {
                c.hedefPopulerlik = hedef.popValue();
                c.ustEnYuksekPopulerlik = enPopulerUst.popValue();
                c.ustEnPopulerEtiket = enPopulerUst.label();
                c.ustEnPopulerIndex = enPopulerUst.indexType();
                c.karsilastirmaAyniIndex = hedef.indexType().equals(enPopulerUst.indexType());
                double d = hedef.popValue() - enPopulerUst.popValue();
                c.populerlikKarsilastirmasi = d > 0.001 ? "HEDEF_DAHA_POPULER"
                                            : d < -0.001 ? "HEDEF_DAHA_AZ_POPULER" : "ESIT";
            }
        }

        if (prev != null) {
            String was = prev.get(r.caseId);
            if ("OK".equals(was)) c.regresyon = true;
            else if (was != null) c.kaliciBulgu = true;
        }

        ekSinyalleriTopla(c);
        return c;
    }

    /**
     * "DEHA INC. & cakal" / "MECURE, X.SIDER" / "Musa & Gulsah" -> ayni tabana iner.
     * Amac: ayni sanatcinin collab ve kopya kayitlarini tek grupta saymak.
     */
    private static String sanatciTabani(String label) {
        String s = label == null ? "" : label;
        int dash = s.indexOf(" — ");
        if (dash > 0) s = s.substring(0, dash);
        String[] parts = s.split("\\s*[&,]\\s*|\\s+feat\\.|\\s+ft\\.", 2);
        return ExcelReport.norm(parts[0]);
    }

    /**
     * Kazanan kuraldan BAGIMSIZ olarak tetiklenen tum sinyalleri toplar.
     * Kural motoru ilk eslesen kurali secer; ikincil mekanizmalar (duplikasyon,
     * varyant, populerlik bosluklari) boylece raporda gorunur kalir.
     */
    private static void ekSinyalleriTopla(RowContext c) {
        if (c.apiHatasi) {
            c.ekSinyaller.add("API/ALTYAPI HATASI — bu satir arama bulgusu degil");
        }
        if (c.duplikasyon) {
            c.ekSinyaller.add("Duplikasyon: ayni kayit top-N'de birden fazla");
        }
        if (c.sanatciVaryantSlot >= 3) {
            c.ekSinyaller.add("Sanatci varyanti: '" + c.sanatciVaryantAdi + "' "
                    + c.sanatciVaryantSlot + " slot (solo + collab + kopya)");
        }
        if (c.agirlikDevrilmesi) {
            c.ekSinyaller.add("Agirlik devrilmesi: [" + c.devrilenUstIndex
                    + "] ustte, ham skoru daha yuksek [" + c.devrilenAltIndex + "] altta");
        }
        if (c.birebirBaslikEslesmesi >= 3) {
            c.ekSinyaller.add("Birebir baslik eslesmesi: " + c.birebirBaslikEslesmesi + " kayit");
        }
        if (c.populerlikEksik >= 2) {
            c.ekSinyaller.add("Populerlik alani bos: " + c.populerlikEksik + " kayit");
        }
        for (String idx : c.oluPopulerlikIndexleri) {
            // DIKKAT: bu bir ETL bulgusu DEGIL. 04.09.2026'da olculdu: skorlamayi
            // `prodPopularity` belirliyor (skor = taban + 0.58 x prodPopularity, R2=1.0).
            // Rapor eski alani basiyorsa deger 0 gorunur ama motor sinyali canlidir.
            c.ekSinyaller.add("Raporda populerlik 0 goruluyor [" + idx + "] — muhtemelen "
                    + "eski alan basimi (skorlama prodPopularity kullaniyor), ETL bulgusu degil");
        }
        switch (c.populerlikKarsilastirmasi) {
            case "HEDEF_DAHA_POPULER" -> c.ekSinyaller.add(String.format(Locale.ROOT,
                    "Populerlik: hedef %.2f > ustundeki en populer %.2f (%s) — populerlik eziliyor, GERCEK bulgu",
                    c.hedefPopulerlik, c.ustEnYuksekPopulerlik, c.ustEnPopulerEtiket));
            case "HEDEF_DAHA_AZ_POPULER" -> c.ekSinyaller.add(String.format(Locale.ROOT,
                    "Populerlik: hedef %.2f < ustundeki en populer %.2f (%s)%s",
                    c.hedefPopulerlik, c.ustEnYuksekPopulerlik, c.ustEnPopulerEtiket,
                    c.karsilastirmaAyniIndex
                        ? " — AYNI index, motor populerlige gore hakli olabilir, BEKLENTIYI gozden gecirin"
                        : " — FARKLI index, populerlik kiyasi gecersiz, bu bir intent problemi"));
            case "ESIT" -> c.ekSinyaller.add(String.format(Locale.ROOT,
                    "Populerlik: hedef ile ustundeki kayit ESIT (%.2f) — populerlik ayirt edemiyor, tie-breaker gerekli",
                    c.hedefPopulerlik));
            default -> { }
        }
        if (c.regresyon) {
            c.ekSinyaller.add("REGRESYON: onceki kosuda OK idi");
        }
    }

    /** Yapisal olgulari LLM'e verilecek insan okunur satirlara cevirir. */
    private static List<String> toFacts(RowContext c) {
        List<String> f = new ArrayList<>();

        if (c.beklenenSira > 0) {
            f.add("Beklenen icerik " + c.beklenenSira + ". sirada bulundu (beklenti karsilanmadi).");
        } else {
            f.add("Beklenen icerik top-10 icinde HIC bulunamadi.");
        }

        if (c.hits.isEmpty()) {
            f.add("UYARI: sonuc metninden hicbir sonuc satiri ayristirilamadi.");
            return f;
        }

        if (c.agirlikDevrilmesi) {
            f.add(String.format(Locale.ROOT,
                    "AGIRLIK DEVRILMESI: ustteki [%s] kaydinin ham skoru, alttaki [%s] kaydindan DUSUK. "
                  + "Sirayi ham skor degil, skor x topHitsScoreWeight belirliyor (%s).",
                    c.devrilenUstIndex, c.devrilenAltIndex, IndexWeights.describe()));
        }

        if (c.duplikasyon) {
            f.add("DUPLIKASYON: ilk " + c.hits.size() + " sonucta ayni kayit birden fazla yer aliyor. "
                + "Tekillestirme (field collapse) hedefi yukari tasiyabilir.");
        }

        if (c.populerlikEksik > 0) {
            f.add("POPULERLIK EKSIK: ilk " + c.hits.size() + " sonucun " + c.populerlikEksik
                + " tanesinde populerlik alani bos ('-'); bu kayitlar skorda 0 sayilir ama yine de ust siralarda.");
        }

        if (!c.oluPopulerlikIndexleri.isEmpty()) {
            f.add("RAPORLAMA ARTEFAKTI: [" + String.join(", ", c.oluPopulerlikIndexleri)
                + "] index'inde rapora basilan populerlik alani 0 geliyor. Bu bir ETL bulgusu DEGILDIR. "
                + "04.09.2026'da olculdu: skorlamayi `prodPopularity` belirliyor "
                + "(tam eslesme grubunda skor = 103.7777 + 0.58 x prodPopularity, R2 = 1.00000). "
                + "`popularity` alani UAT'ta 0 ama skorlamada kullanilmiyor. Bu satirin gercek kok nedenini "
                + "populerlikte ARAMA — exact/tier bonusuna, agirliga veya beklentiye bak.");
        }

        if (c.ilkSonucSorguylaAyni) {
            f.add("1. SIRA SORGUYLA BIREBIR: en ustteki sonucun adi sorguyla (\"" + c.sorgu
                + "\") harfi harfine ayni. Kisa/birebir isim, daha populer uzun ismi geciyor.");
        }

        if (c.birebirBaslikEslesmesi > 0) {
            f.add("BIREBIR ESLESME: ilk sonuclarin " + c.birebirBaslikEslesmesi
                + " tanesinin adi sorguyla (\"" + c.sorgu + "\") birebir ayni. "
                + "songs index'inde tierRankingEnabled=true; birebir baslik eslesmesi ~25-35 puan sicrama alir.");
        }

        f.add(c.indexCesidi == 1
                ? "Tum sonuclar tek index'ten geliyor (" + c.ilkIndex + "); cross-index harmanlama etken degil."
                : "Sonuclar " + c.indexCesidi + " farkli index'ten geliyor; 1. sira [" + c.ilkIndex + "].");

        if (c.regresyon) {
            f.add("REGRESYON: bu case onceki kosuda OK idi, bu kosuda NOK. "
                + "Aradaki config/veri degisikligi oncelikle incelenmeli.");
        } else if (c.kaliciBulgu) {
            f.add("Bu case onceki kosuda da NOK idi (kalici bulgu).");
        }

        return f;
    }

    /** Kosu geneli olgular — her prompt'a eklenir. */
    public String globalFactsBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append("KOSU GENELI OLGULAR\n");
        sb.append("- Toplam case: ").append(totalRows).append(", NOK: ").append(nokRows).append('\n');
        sb.append("- topHitsScoreWeight: ").append(IndexWeights.describe()).append('\n');
        for (Map.Entry<String, Boolean> e : deadPopularityByIndex.entrySet()) {
            if (Boolean.TRUE.equals(e.getValue())) {
                sb.append("- RAPORLAMA NOTU: [").append(e.getKey())
                  .append("] index'inde rapora basilan populerlik alani 0. Bu ETL bulgusu DEGIL — ")
                  .append("skorlamayi prodPopularity belirliyor (04.09.2026 olcumu, R2=1.0). ")
                  .append("Kok nedeni populerlikte aramayin.\n");
            }
        }
        sb.append("- Kategori bazli NOK: ");
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, int[]> e : byType.entrySet()) {
            if (e.getValue()[1] > 0) parts.add(e.getKey() + " " + e.getValue()[1] + "/" + e.getValue()[0]);
        }
        sb.append(String.join(" · ", parts)).append('\n');
        return sb.toString();
    }
}
