package tests.arama;

import client.MuudSearchApi;
import config.TestConfig;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import report.ExcelTestReportWriter;
import report.TestResultRow;
import util.MuudSearchUtils;

import java.util.*;
import java.util.stream.Stream;

/**
 * DDP PROD BULGULARI (Muud Search Defect Takibi, 25 madde) — UAT'te yeniden üretme ve analiz.
 *
 * Prod'da henüz Intent Search (Salih) yok; bulgular önce UAT'te koşulur:
 *   OK  → UAT'te beklenen davranış sağlanıyor (bulgu bu ortamda çözülmüş / yeniden üretilemedi)
 *   NOK → Bulgu UAT'te de devam ediyor; raporda NEDEN gelmediği analiz edilir
 *         (beklenen içerik hangi sırada, üstündeki içerikler ve skor/popülarite farkı).
 *
 * Mevcut GuncelAramalarTest (≈430 case) ile ilgisi yoktur; ayrı rapor üretir.
 * Test HİÇBİR ZAMAN fail etmez — saf gözlem & Excel raporu üretir.
 *
 * Çalıştır:
 *   mvn test -Dtest=DdpBulguTest
 * Çıktı:
 *   reports/ddp-bulgu/DdpBulguTest_dd.MM.yyyy_HH.mm_ORTAM.xlsx
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DdpBulguTest extends TestConfig {

    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final List<TestResultRow> ROWS = new ArrayList<>();
    private static MuudSearchApi api;

    @BeforeAll
    static void init() {
        api = new MuudSearchApi();
        System.out.println("✅ DdpBulguTest başlatıldı — ortam: " + MuudSearchApi.ortam());
    }

    @AfterAll
    static void writeReport() {
        System.out.printf("%n📋 Toplam %d bulgu case'i işlendi.%n", ROWS.size());
        ExcelTestReportWriter.writeBulgu(ROWS, "reports/ddp-bulgu", MuudSearchApi.ortam(), "DdpBulguTest");
    }

    // ── Case tanımı ──────────────────────────────────────────────────────────
    public enum Check {
        /** Beklenen içerik (tür/ad/sanatçı) ilk topN içinde olmalı. */
        RANK_ITEM,
        /** Adı aranan terimle birebir eşleşen içerik 1. sırada olmalı (öncesinde başka içerik olmamalı). */
        FIRST_EXACT,
        /** 1. sırada sanatçı, 2-6. sıralarda en az 3'ü o sanatçının şarkısı olmalı. */
        ARTIST_THEN_SONGS,
        /** Belirtilen adları içeren içerikler ilk topN'de OLMAMALI. */
        NOT_IN_TOP,
        /** 1. sıradaki içeriğin adı belirtilen ifadeyle başlamalı. */
        FIRST_STARTS
    }

    /**
     * @param id          DB_xx
     * @param no          Excel'deki bulgu numarası
     * @param terms       Aranacak terim(ler) — birden fazlaysa varyasyon (yazım/boşluk) testidir
     * @param expKind     songs | albums | playlists | performers | videos | "" (fark etmez)
     * @param expName     Beklenen ad ("a;b;c" → NOT_IN_TOP için çoklu)
     * @param expArtist   Beklenen sanatçı (boş = kontrol yok)
     * @param topN        Beklenen en kötü sıra
     * @param limit       API limit (listede çok aşağıda kalan bulgular için 60)
     * @param consistency Varyasyonların ilk 5 sonucu birbirine yakın olmalı mı
     */
    public record Finding(String id, int no, String section, String title, String bulgu, Check check,
                          List<String> terms, String expKind, String expName, String expArtist,
                          int topN, int limit, boolean consistency) {
        @Override public String toString() { return id + " · #" + no + " " + title; }
    }

    private static Finding f(String id, int no, String section, String title, String bulgu, Check check,
                             List<String> terms, String expKind, String expName, String expArtist,
                             int topN, int limit, boolean consistency) {
        return new Finding(id, no, section, title, bulgu, check, terms, expKind, expName, expArtist,
                topN, limit, consistency);
    }

    private static final String S_SIRA   = "Sıralama";
    private static final String S_SANATCI= "Sanatçı sonrası şarkılar";
    private static final String S_LISTE  = "Liste / playlist";
    private static final String S_YAZIM  = "Yazım toleransı";
    private static final String S_LYRICS = "Şarkı sözü";
    private static final String S_TAM    = "Birebir eşleşme";

    public static Stream<Finding> cases() {
        List<Finding> l = new ArrayList<>();

        l.add(f("DB_01", 1, S_SIRA, "crush — CRUSH! şarkısı 1. sırada olmalı",
                "crush aramasında 3. sırada çıkan içeriğin (CRUSH! şarkısı) 1. sırada gelmesi beklenir.",
                Check.RANK_ITEM, List.of("crush"), "songs", "Crush!", "", 1, 20, false));

        // #2 — sanatçı sonrası şarkıları (blok3 #7'de, semicenk #6'da ayrıca)
        String[] sanatcilar = {"hadise", "motive", "manifest", "müslüm gürses", "simge", "sezen aksu", "tarkan",
                "sagopa kajmer", "ferdi tayfur", "yalın", "ebru gündeş", "yıldız tilbe", "serdar ortaç"};
        char sub = 'a';
        for (String s : sanatcilar) {
            l.add(f("DB_02" + (sub++), 2, S_SANATCI, "Sanatçı ilk sırada, hemen altında şarkıları: " + s,
                    "Sanatçı adı anlaşıldığında ilk sırada sanatçı, sonrasında sanatçıya ait şarkılar gelmeli; "
                            + "kural en çok aratılan sanatçıların hepsinde çalışmıyor.",
                    Check.ARTIST_THEN_SONGS, List.of(s), "", "", s, 6, 20, false));
        }

        l.add(f("DB_03", 3, S_LISTE, "türkü — 'Türküler' listesi 7. sırada",
                "türkü aramasında Türküler listesinin Türkçe Akustik listelerinin gerisinde 7. sırada gelmesi beklenmez.",
                Check.RANK_ITEM, List.of("türkü"), "playlists", "Türküler", "", 3, 20, false));

        l.add(f("DB_04", 4, S_LISTE, "ilahi — Emir Şamur / Oğuzhan Koç / Klima Etkisi listeleri üstte olmamalı",
                "ilahi aramasında Emir Şamur, Oğuzhan Koç ve Klima Etkisi listelerinin üst sıralarda gelmesi anlamlı değil.",
                Check.NOT_IN_TOP, List.of("ilahi"), "", "Emir Şamur;Oğuzhan Koç;Klima Etkisi", "", 3, 20, false));

        l.add(f("DB_05", 5, S_SANATCI, "wegh — sanatçıdan sonra şarkıları gelmeli (klip/albüm değil)",
                "Sanatçı doğru bulunuyor ama altına şarkıları yerine klip ve albümleri geliyor; popularity mi?",
                Check.ARTIST_THEN_SONGS, List.of("wegh"), "", "", "Wegh", 6, 20, false));

        l.add(f("DB_06", 6, S_SANATCI, "semicenk — Tümü'nde şarkı gelmiyor",
                "Semicenk aramasında (5. madde ile aynı) neredeyse hiç şarkı sonucu gelmiyor.",
                Check.ARTIST_THEN_SONGS, List.of("semicenk"), "", "", "Semicenk", 6, 20, false));

        l.add(f("DB_07", 7, S_YAZIM, "blok3 / blok 3 / blok — aynı sonuçlar beklenir",
                "blok3 aramasında sanatçı + şarkıları geliyor, 'blok 3' ve 'blok' aramasında sanatçı bulunsa da şarkıları yerine "
                        + "başka içerikler geliyor; manuel tanım algısı.",
                Check.ARTIST_THEN_SONGS, List.of("blok3", "blok 3", "blok"), "", "", "Blok3", 6, 20, true));

        l.add(f("DB_08", 8, S_LISTE, "ilahiler — aynı isimli çalma listesi ilk 10'da olmalı",
                "ilahiler aramasında aynı isimli çalma listesi ilk 50-60 sonuçta gelmiyor.",
                Check.RANK_ITEM, List.of("ilahiler"), "playlists", "İlahiler", "", 10, 60, false));

        l.add(f("DB_09", 9, S_LISTE, "ninni — 'Ninni Zamanı' listesi ilk 10'da olmalı",
                "ninni aramasında Ninni Zamanı listesi Tümü'nde ilk 50-60 sonuçta gelmiyor.",
                Check.RANK_ITEM, List.of("ninni"), "playlists", "Ninni Zamanı", "", 10, 60, false));

        l.add(f("DB_10", 10, S_LISTE, "türkçe pop — aynı isimli liste 9. sırada",
                "türkçe pop aramasında aynı isme sahip çalma listesi 9. sırada; eski/popülerliği az listeler üstte.",
                Check.RANK_ITEM, List.of("türkçe pop"), "playlists", "Türkçe Pop", "", 3, 20, false));

        l.add(f("DB_11", 11, S_SIRA, "90 lar — 90'lar ile başlayan içerik ilk sırada olmalı",
                "90 lar aramasında 90'lar ile başlayan sonuçlar varken ifadenin ortada geçtiği içerik ilk sırada geliyor.",
                Check.FIRST_STARTS, List.of("90 lar"), "", "90", "", 1, 20, false));

        l.add(f("DB_12", 12, S_YAZIM, "Türkiye'nin 7 Muud'u — kesme işareti varyasyonları",
                "Albüm, kesme işareti kullanılmadan bulunamıyor; önerilen keyword'e tıklayınca kesme işareti tipi (’ / ') "
                        + "değiştiği için sonuç daha kötü oluyor.",
                Check.RANK_ITEM,
                List.of("Türkiye’nin 7 Muud’u", "Türkiye'nin 7 Muud'u", "Türkiyenin 7 Muudu",
                        "türkiye nin 7 muud u", "turkiyenin 7 muudu", "türkiye'nin 7 muudu"),
                "albums", "Türkiye'nin 7 Muud'u", "", 3, 20, false));

        l.add(f("DB_13", 13, S_SANATCI, "kıraç — şarkıları yerine albümler geliyor",
                "Kıraç aramasında 2-3. sıradaki içerikler Kıraç En Popüler Şarkılar listesinden önde; şarkılar yerine albümler.",
                Check.ARTIST_THEN_SONGS, List.of("kıraç"), "", "", "Kıraç", 6, 20, false));

        l.add(f("DB_14", 14, S_YAZIM, "senden adam olmaz — 1 karakter eksik",
                "1 karakter eksik yazıldığında (snden adam olmaz) sonuç çok değişiyor.",
                Check.RANK_ITEM, List.of("senden adam olmaz", "snden adam olmaz"),
                "songs", "Senden Adam Olmaz", "", 5, 20, true));

        l.add(f("DB_15", 15, S_TAM, "karma — birebir eşleşen içerik 1. sırada olmalı",
                "karma yazıldığında birebir eşleşen içerik 2. sırada geliyor.",
                Check.FIRST_EXACT, List.of("karma"), "", "Karma", "", 1, 20, false));

        l.add(f("DB_16", 16, S_TAM, "tss — birebir eşleşen içerik 1. sırada olmalı",
                "tss aramasında birebir eşleşen içerik 3. sırada geliyor.",
                Check.FIRST_EXACT, List.of("tss"), "", "TSS", "", 1, 20, false));

        l.add(f("DB_17", 17, S_TAM, "ağla yüreğim ağla — birebir şarkı ilk 3'te olmalı",
                "ağla yüreğim ağla aramasında birebir eşleşen içerik 6. sırada geliyor.",
                Check.RANK_ITEM, List.of("ağla yüreğim ağla"), "songs", "Ağla Yüreğim Ağla", "", 3, 20, false));

        l.add(f("DB_18", 18, S_YAZIM, "hayice — hatice beklenir, hadise'ye düzeltiliyor",
                "Klavye hatasıyla hayice yazıldığında arama motoru hadise düzeltmesi yapıyor; hatice beklenir.",
                Check.RANK_ITEM, List.of("hayice", "hatice"), "", "Hatice", "", 3, 20, true));

        l.add(f("DB_19", 19, S_YAZIM, "yar ayrı gayrın mı var — 1 karakter eksik",
                "yar ayrın gayrın mı var (1 karakter fazla/eksik) yazıldığında sonuç alakasız değişiyor.",
                Check.RANK_ITEM, List.of("yar ayrı gayrın mı var", "yar ayrın gayrın mı var"),
                "songs", "Yar Ayrı Gayrın Mı Var", "", 3, 20, true));

        l.add(f("DB_20", 20, S_YAZIM, "gidenlerden bir tek seni bana — 1 karakter yanlış",
                "Klavyede 1 karakter yanlış yazıldığında (nir) sonuç çok değişiyor.",
                Check.RANK_ITEM, List.of("gidenlerden bir tek seni bana", "gidenlerden nir tek seni bana"),
                "songs", "Gidenlerden", "", 3, 20, true));

        l.add(f("DB_21", 21, S_LYRICS, "diva bebeğim — söz ile şarkı bulunmalı",
                "Burak Bulut – Diva şarkısının sözü (diva bebeğim) aratıldığında şarkı bulunamıyor.",
                Check.RANK_ITEM, List.of("diva bebeğim"), "songs", "Diva", "Burak Bulut", 5, 20, false));

        l.add(f("DB_22", 22, S_LYRICS, "pervazımda güller açmış — söz ile şarkı bulunmalı",
                "Emre Fel – Karşında Bi' Gör Beni şarkısının sözü aratıldığında şarkı bulunamıyor.",
                Check.RANK_ITEM, List.of("pervazımda güller açmış"), "songs", "Karşında Bi' Gör Beni", "Emre Fel", 5, 20, false));

        l.add(f("DB_23", 23, S_TAM, "öp — aynı isimli şarkı 1. sırada olmalı",
                "öp aramasında aynı isme sahip şarkılar 3. sıradan itibaren geliyor; ilk 2 sonuç alakasız.",
                Check.FIRST_EXACT, List.of("öp"), "", "Öp", "", 1, 20, false));

        l.add(f("DB_24", 24, S_LYRICS, "gel bir sarayım — şarkı (klip değil) Tümü'nde gelmeli",
                "Şarkı sözünden ilgili şarkı bulunamıyor, klip ilk sırada; şarkı Tümü'nde gelmiyor (platformda var).",
                Check.RANK_ITEM, List.of("gel bir sarayım"), "songs", "Aşkın Olayım", "Simge", 3, 20, false));

        l.add(f("DB_25", 25, S_YAZIM, "gel şvbel — Tümü'nde Lvbel C5 şarkısı gelmeli",
                "Klavye hatası (gel şvbel) ile Şarkılar sekmesi doğru şarkıyı getirirken Tümü alakasız sonuç getiriyor.",
                Check.RANK_ITEM, List.of("gel şvbel", "gel şebel"), "", "", "Lvbel C5", 5, 20, true));

        return l.stream();
    }

    // ── Test ─────────────────────────────────────────────────────────────────
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @Order(1)
    void run(Finding fi) {
        List<TermResult> results = new ArrayList<>();
        for (String term : fi.terms()) {
            results.add(evaluateTerm(fi, term));
        }

        boolean allOk = results.stream().allMatch(TermResult::ok);
        String consistencyMsg = "";
        if (fi.consistency() && results.size() > 1) {
            Set<String> base = keysTop(results.get(0).items(), 5);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < results.size(); i++) {
                Set<String> other = keysTop(results.get(i).items(), 5);
                long common = other.stream().filter(base::contains).count();
                sb.append("'").append(fi.terms().get(i)).append("' ile '").append(fi.terms().get(0))
                  .append("' ilk-5 ortak: ").append(common).append("/5");
                if (common < 3) {
                    allOk = false;
                    sb.append("  ❌ (yazım varyasyonunda sonuç tutarsız — düzeltme/correction yönlendirmesi incelenmeli)");
                } else {
                    sb.append("  ✅");
                }
                sb.append("\n");
            }
            consistencyMsg = "Tutarlılık:\n" + sb;
        }

        String status = allOk ? "OK" : "NOK";
        StringBuilder out = new StringBuilder();
        out.append(allOk
                ? "UAT'te beklenen davranış sağlanıyor (bulgu bu ortamda yeniden üretilemedi).\n\n"
                : "Bulgu UAT'te de devam ediyor — analiz aşağıda.\n\n");
        for (TermResult r : results) out.append(r.text()).append("\n");
        if (!consistencyMsg.isEmpty()) out.append(consistencyMsg);

        System.out.printf("[%s] %-8s | #%-2d | %s%n", status, fi.id(), fi.no(), fi.title());

        ROWS.add(new TestResultRow(
                fi.id(),
                "#" + fi.no() + " · " + fi.title(),
                "Prod bulgusu #" + fi.no() + ": " + fi.bulgu() + "\nTerim(ler): " + String.join(" | ", fi.terms()),
                expectedText(fi),
                fi.section(),
                "active-indices",
                status,
                out.toString().trim()
        ));
    }

    // ── Değerlendirme ────────────────────────────────────────────────────────
    record Item(int pos, String kind, String name, String artist, double score, String pop, String id) {}
    record TermResult(String term, boolean ok, String text, List<Item> items) {}

    private TermResult evaluateTerm(Finding fi, String term) {
        List<Item> items;
        String extra = "";
        try {
            int lim = fi.limit();
            Response res = api.search(term, "active-indices", lim, true);
            // Gateway büyük limit'e HTTP 400 dönebiliyor (örn. 60): 50, sonra 20 ile tekrar dene
            if (res.statusCode() == 400 && lim > 50) { lim = 50; res = api.search(term, "active-indices", lim, true); }
            if (res.statusCode() == 400 && lim > 20) { lim = 20; res = api.search(term, "active-indices", lim, true); }
            JsonPath jp = res.jsonPath();
            items = parse(jp, lim);
            extra = rootFields(jp);
            if (items.isEmpty()) {
                return new TermResult(term, false,
                        "▶ '" + term + "' → NOK — API boş sonuç döndürdü (HTTP " + res.statusCode() + ").\n", items);
            }
        } catch (Exception e) {
            return new TermResult(term, false, "▶ '" + term + "' → NOK — API hatası: " + e.getMessage() + "\n", List.of());
        }

        boolean ok;
        String verdict;
        String analysis = "";

        switch (fi.check()) {
            case RANK_ITEM -> {
                int idx = indexOf(items, fi);
                ok = idx >= 0 && idx < fi.topN();
                if (idx >= 0) {
                    verdict = (idx + 1) + ". sırada" + (ok ? "" : " (beklenen: ilk " + fi.topN() + ")");
                } else {
                    verdict = "ilk " + items.size() + " içinde yok";
                }
                if (!ok) analysis = explain(items, idx, describe(fi));
            }
            case FIRST_EXACT -> {
                int idx = -1;
                for (int i = 0; i < items.size(); i++) {
                    if (norm(items.get(i).name()).equals(norm(fi.expName()))) { idx = i; break; }
                }
                ok = idx == 0;
                verdict = idx == 0 ? "birebir eşleşen içerik 1. sırada"
                        : idx > 0 ? "birebir eşleşen içerik " + (idx + 1) + ". sırada"
                        : "birebir eşleşen içerik ilk " + items.size() + " içinde yok";
                if (!ok) analysis = explain(items, idx, "adı '" + fi.expName() + "' olan içerik");
            }
            case ARTIST_THEN_SONGS -> {
                Item first = items.get(0);
                boolean firstOk = "performers".equals(first.kind())
                        && norm(first.name()).equals(norm(fi.expArtist()));
                int songs = 0;
                Map<String, Integer> kinds = new TreeMap<>();
                int max = Math.min(6, items.size());
                for (int i = 1; i < max; i++) {
                    Item it = items.get(i);
                    kinds.merge(it.kind(), 1, Integer::sum);
                    if ("songs".equals(it.kind()) && norm(it.artist()).contains(norm(fi.expArtist()))) songs++;
                }
                ok = firstOk && songs >= 3;
                verdict = "1. sıra: [" + first.kind() + "] " + first.name()
                        + (firstOk ? " ✅" : " ❌ (sanatçı değil)")
                        + " | 2-6. sıra: " + songs + " şarkı (" + fi.expArtist() + "), dağılım " + kinds;
                if (!ok) {
                    analysis = !firstOk
                            ? "1. sırada beklenen sanatçı sayfası yok → intent/sanatçı tespiti çalışmıyor ya da ad eşleşmesi zayıf."
                            : "Sanatçı sayfasından sonra şarkılar yerine " + kinds
                              + " geliyor → kind önceliği (sanatçının şarkıları) uygulanmıyor; sıralama skor/popülarite ile belirleniyor.";
                }
            }
            case NOT_IN_TOP -> {
                List<String> bad = new ArrayList<>();
                for (String tok : fi.expName().split(";")) {
                    for (int i = 0; i < Math.min(fi.topN(), items.size()); i++) {
                        if (norm(items.get(i).name()).contains(norm(tok))) bad.add((i + 1) + ". " + items.get(i).name());
                    }
                }
                ok = bad.isEmpty();
                verdict = ok ? "istenmeyen içerik ilk " + fi.topN() + "'te yok" : "ilk " + fi.topN() + "'te: " + bad;
                if (!ok) analysis = "Bu listeler 'ilahi' kelimesini adında taşımıyor olabilir; üst sıralara popülarite/içerik "
                        + "alanı eşleşmesi ile çıkıyor. İlk sıradaki içeriğin skor ve popülariteleri aşağıdaki listede.";
            }
            case FIRST_STARTS -> {
                Item first = items.get(0);
                ok = norm(first.name()).startsWith(norm(fi.expName()));
                verdict = "1. sıra: [" + first.kind() + "] " + first.name() + (ok ? " ✅" : " ❌");
                if (!ok) {
                    int idx = -1;
                    for (int i = 0; i < items.size(); i++) {
                        if (norm(items.get(i).name()).startsWith(norm(fi.expName()))) { idx = i; break; }
                    }
                    analysis = explain(items, idx, "adı '" + fi.expName() + "' ile başlayan içerik");
                }
            }
            default -> { ok = false; verdict = "tanımsız kontrol"; }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("▶ '").append(term).append("' → ").append(ok ? "OK" : "NOK").append(" — ").append(verdict).append("\n");
        if (!analysis.isEmpty()) sb.append("  Analiz: ").append(analysis).append("\n");
        if (!extra.isEmpty()) sb.append("  Yanıt alanları: ").append(extra).append("\n");
        sb.append("  İlk 10:\n");
        for (int i = 0; i < Math.min(10, items.size()); i++) sb.append("   ").append(line(items.get(i))).append("\n");
        return new TermResult(term, ok, sb.toString(), items);
    }

    /** Beklenen içeriğin sırasını (0 tabanlı) bulur; yoksa -1. */
    private int indexOf(List<Item> items, Finding fi) {
        for (int i = 0; i < items.size(); i++) {
            if (matches(items.get(i), fi)) return i;
        }
        return -1;
    }

    private boolean matches(Item it, Finding fi) {
        if (!fi.expKind().isBlank() && !fi.expKind().equals(it.kind())) return false;
        if (!fi.expName().isBlank() && !norm(it.name()).equals(norm(fi.expName()))) return false;
        if (!fi.expArtist().isBlank()) {
            String a = norm(fi.expArtist());
            boolean artistOk = norm(it.artist()).contains(a)
                    || ("performers".equals(it.kind()) && norm(it.name()).contains(a));
            if (!artistOk) return false;
        }
        return true;
    }

    private String describe(Finding fi) {
        StringBuilder sb = new StringBuilder();
        if (!fi.expKind().isBlank()) sb.append("[").append(fi.expKind()).append("] ");
        if (!fi.expName().isBlank()) sb.append("'").append(fi.expName()).append("' ");
        if (!fi.expArtist().isBlank()) sb.append("(").append(fi.expArtist()).append(")");
        return sb.toString().trim();
    }

    private String expectedText(Finding fi) {
        return switch (fi.check()) {
            case RANK_ITEM -> "İlk " + fi.topN() + " içinde " + describe(fi) + " gelmeli.";
            case FIRST_EXACT -> "Adı '" + fi.expName() + "' olan (birebir eşleşen) içerik 1. sırada gelmeli.";
            case ARTIST_THEN_SONGS -> "1. sırada '" + fi.expArtist() + "' sanatçısı, 2-6. sırada en az 3 şarkısı gelmeli.";
            case NOT_IN_TOP -> "İlk " + fi.topN() + " içinde şunlar OLMAMALI: " + fi.expName().replace(";", ", ");
            case FIRST_STARTS -> "1. sıradaki içeriğin adı '" + fi.expName() + "' ile başlamalı.";
        };
    }

    /**
     * Beklenen içerik neden geride kaldı?
     * idx>0: üstündeki içeriklerle skor/popülarite/tür karşılaştırması; idx<0: ilk sonuçlarda yok.
     */
    private String explain(List<Item> items, int idx, String what) {
        if (idx < 0) {
            return what + " ilk " + items.size() + " sonuçta HİÇ yok → indekste kayıt yok, ad eşleşmiyor "
                    + "(analyzer/tokenization) ya da skor çok düşük olabilir. Postman ile ES'te kaydın varlığını kontrol edin.";
        }
        if (idx == 0) return "";
        Item exp = items.get(idx);
        StringBuilder sb = new StringBuilder();
        sb.append(what).append(" ").append(idx + 1).append(". sırada (skor=").append(fmt(exp.score()))
          .append(", pop=").append(exp.pop()).append("). Önündeki içerikler: ");
        Set<String> kindsAbove = new LinkedHashSet<>();
        int higher = 0;
        for (int i = 0; i < idx && i < 5; i++) {
            Item a = items.get(i);
            kindsAbove.add(a.kind());
            sb.append("\n    ").append(i + 1).append(". [").append(a.kind()).append("] ").append(a.name())
              .append(" (skor=").append(fmt(a.score())).append(", pop=").append(a.pop()).append(")");
            if (a.score() > exp.score()) higher++;
        }
        sb.append("\n  Yorum: ");
        if (higher == Math.min(idx, 5)) {
            sb.append("Önündeki içeriklerin hepsi daha yüksek skorlu; sıralama skor (BM25 + popülarite) farkından kaynaklanıyor. ");
        }
        if (!kindsAbove.contains(exp.kind()) || kindsAbove.size() > 1) {
            sb.append("Üstte farklı türler (").append(kindsAbove).append(") var: tür/intent önceliği uygulanmıyor. ");
        }
        sb.append("Intent Search (kind boost) bu davranışı değiştiriyorsa UAT'te OK görülmesi beklenir.");
        return sb.toString();
    }

    // ── Yardımcılar ──────────────────────────────────────────────────────────
    private List<Item> parse(JsonPath jp, int limit) {
        List<Item> list = new ArrayList<>();
        String base = MuudSearchUtils.getBasePath(jp);
        List<Object> raw = MuudSearchUtils.resultsList(jp);
        int n = Math.min(raw.size(), limit);
        for (int i = 0; i < n; i++) {
            String d = base + "[" + i + "].data";
            String kind = MuudSearchUtils.safeStr(jp.getString(d + ".kind"));
            if (kind.isEmpty()) kind = MuudSearchUtils.safeStr(jp.getString(d + ".itemKind"));
            String performer = MuudSearchUtils.getPerformerName(jp, d);
            String name = MuudSearchUtils.safeStr(jp.getString(d + ".songName"));
            if (name.isEmpty()) name = MuudSearchUtils.safeStr(jp.getString(d + ".albumName"));
            if (name.isEmpty()) name = MuudSearchUtils.safeStr(MuudSearchUtils.getPlaylistName(jp, d));
            if (name.isEmpty()) name = performer;
            double score = 0;
            Object s = jp.get(base + "[" + i + "].score");
            if (s instanceof Number num) score = num.doubleValue();
            Object pop = jp.get(d + ".prodPopularity");
            if (pop == null) pop = jp.get(d + ".popularity");
            Object id = jp.get(base + "[" + i + "].id");
            list.add(new Item(i + 1, kind, name, performer, score,
                    pop == null ? "-" : pop.toString(), id == null ? "-" : id.toString()));
        }
        return list;
    }

    /** Yanıtın kök seviyesindeki skaler alanlar (correctedText gibi düzeltme bilgisi var mı diye). */
    private String rootFields(JsonPath jp) {
        try {
            Map<String, Object> root = jp.getMap("");
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> e : root.entrySet()) {
                Object v = e.getValue();
                if (v == null || v instanceof List || v instanceof Map) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(e.getKey()).append("=").append(v);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private Set<String> keysTop(List<Item> items, int n) {
        Set<String> keys = new LinkedHashSet<>();
        for (int i = 0; i < Math.min(n, items.size()); i++) {
            Item it = items.get(i);
            keys.add(it.kind() + "|" + norm(it.name()) + "|" + norm(it.artist()));
        }
        return keys;
    }

    private String line(Item it) {
        return it.pos() + ". [" + it.kind() + "] " + it.name()
                + (it.artist().isEmpty() || it.artist().equals(it.name()) ? "" : " — " + it.artist())
                + " | skor=" + fmt(it.score()) + " | pop=" + it.pop() + " | ID=" + it.id();
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }

    /** Küçük harf (TR) + harf/rakam dışındaki her şeyi at: kesme işareti, boşluk, noktalama farkı yok sayılır. */
    private static String norm(String s) {
        if (s == null) return "";
        return s.toLowerCase(TR).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
